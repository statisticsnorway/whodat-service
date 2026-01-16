package no.ssb.whodat.filters

import com.google.auth.oauth2.GoogleCredentials
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import kotlin.jvm.optionals.getOrNull

/**
 * This filter will obtain an [com.google.auth.oauth2.AccessToken] and add it to the request. It can use credentials from either
 * Google's default Application Default Credentials or a custom Service Account (as opposed to the
 * {@see io.micronaut.gcp.http.client.GoogleAuthFilter} which only uses the Compute metadata server).
 */
@AccessTokenFilterMatcher
@Singleton
@Requires(property = "gcp.http.client.filter.enabled", notEquals = "false", defaultValue = "true")
class AccessTokenFilter(
    @Value($$"${gcp.http.client.filter.credentials-path}") credentialsPath: String?,
    @param:Value($$"${gcp.http.client.filter.project-id}") private val projectId: String?,
    private val applicationContext: ApplicationContext,
) : HttpClientFilter {
    private val log = LoggerFactory.getLogger(AccessTokenFilter::class.java)
    private val baseCredentials: GoogleCredentials =
        if (credentialsPath == null) {
            log.info("Using Application Default Credentials")
            GoogleCredentials.getApplicationDefault()
        } else {
            log.info("Using Credentials from Service Account file: $credentialsPath")
            GoogleCredentials.fromStream(FileInputStream(credentialsPath))
        }

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        val cfg = getConfig(request)

        val scopedToken = baseCredentials.createScoped(cfg?.scopes ?: emptyList<String>())

        val token = scopedToken.refreshAccessToken().tokenValue
        request.bearerAuth(token)
        setQuotaProject(request)
        return chain.proceed(request)
    }

    private fun getConfig(request: MutableHttpRequest<*>): AccessTokenFilterConfig? {
        val serviceId = request.getAttribute("micronaut.http.serviceId")
        return if (serviceId.isPresent) {
            applicationContext
                .findBean(
                    AccessTokenFilterConfig::class.java,
                    Qualifiers.byName(serviceId.get().toString()),
                ).getOrNull()
        } else {
            null
        }
    }

    private fun setQuotaProject(request: MutableHttpRequest<*>) {
        if (projectId != null) {
            log.debug("Using projectId $projectId from config to override quotaProjectId")
            request.headers.add("x-goog-user-project", projectId)
        }
    }
}
