package no.ssb.whodat.filters

import com.google.auth.oauth2.GoogleCredentials
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import whodat.filters.AccessTokenFilterMatcher
import java.io.FileInputStream
import kotlin.jvm.optionals.getOrNull

/**
 * This filter will obtain an [com.google.auth.oauth2.AccessToken] and add it to the request. It can use credentials from either
 * Google's default Application Default Credentials or a custom Service Account (as opposed to the
 * {@see io.micronaut.gcp.http.client.GoogleAuthFilter} which only uses the Compute metadata server).
 */
@AccessTokenFilterMatcher
@Singleton
class AccessTokenFilter(
    @Value("\${gcp.http.client.filter.credentials-path}") credentialsPath: String?,
    @param:Value("\${gcp.http.client.filter.project-id}") private val projectId: String?,
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
    private val credsWithQuota: GoogleCredentials =
        if (!projectId.isNullOrBlank()) {
            baseCredentials.createWithQuotaProject(projectId)
        } else {
            baseCredentials
        }

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        val cfg = getConfig(request)

        log.info("Using quota project: ${credsWithQuota.quotaProjectId}")

        val scoped = credsWithQuota.createScoped(cfg?.scopes ?: defaultScopesFor(request))

        val refreshedToken = scoped.refreshAccessToken()

        log.info("Scoped token: ${refreshedToken.tokenValue}")
        log.info("Scopes: ${refreshedToken.scopes}")

        val token = refreshedToken.tokenValue

        log.info("refreshed token: $token")
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

    private fun defaultScopesFor(request: MutableHttpRequest<*>): List<String> =
        listOf("https://www.googleapis.com/auth/cloud-identity.groups.readonly")

    private fun setQuotaProject(request: MutableHttpRequest<*>) {
        log.info("Project ID $projectId")
        projectId?.let {
            // Either header…
            request.headers.add("x-goog-user-project", it)
            // …or use credentials with quota project:
            // baseCredentials = baseCredentials.createWithQuotaProject(it)  // if you prefer this route
        }
    }
}
