package no.ssb.whodat.filters

import com.google.auth.oauth2.GoogleCredentials
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import whodat.filters.AccessTokenFilterMatcher
import java.io.FileInputStream
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * This filter will obtain an [com.google.auth.oauth2.AccessToken] and add it to the request. It can use credentials from either
 * Google's default Application Default Credentials or a custom Service Account (as opposed to the
 * {@see io.micronaut.gcp.http.client.GoogleAuthFilter} which only uses the Compute metadata server).
 */
@AccessTokenFilterMatcher
@Singleton
class AccessTokenFilter(
    @Value($$"${gcp.http.client.filter.credentials-path}") credentialsPath: String?,
    @param:Value($$"${gcp.http.client.filter.project-id}") private val projectId: String?,
    private val applicationContext: ApplicationContext,
) : HttpClientFilter {
    private val log = LoggerFactory.getLogger(AccessTokenFilter::class.java)
    private val credentials: GoogleCredentials

    init {
        if (credentialsPath == null) {
            log.info("Using Application Default Credentials")
            this.credentials = GoogleCredentials.getApplicationDefault()
        } else {
            log.info("Using Credentials from Service Account file: $credentialsPath")
            this.credentials =
                GoogleCredentials.fromStream(
                    FileInputStream(credentialsPath),
                )
        }
    }

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        val config: AccessTokenFilterConfig? = getConfig(request)
        val accessToken =
            if (config != null) {
                getAccessToken(config.audience)
            } else {
                getAccessToken(getAudienceFromRequest(request))
            }
        log.info("Access token: $accessToken")
        request.bearerAuth(accessToken)
        setProjectIdHeader(request)
        return chain.proceed(request)
    }

    private fun setProjectIdHeader(request: MutableHttpRequest<*>) {
        if (projectId != null) {
            log.debug("Using projectId $projectId from config to override quotaProjectId")
            request.headers.add("x-goog-user-project", projectId)
        }
    }

    private fun getAccessToken(audience: String?): String? = credentials.createScoped(audience).refreshAccessToken().tokenValue

    private fun getConfig(request: MutableHttpRequest<*>): AccessTokenFilterConfig? {
        val serviceId = request.getAttribute("micronaut.http.serviceId")
        if (serviceId.isPresent) {
            return applicationContext
                .findBean(
                    AccessTokenFilterConfig::class.java,
                    Qualifiers.byName(serviceId.get().toString()),
                ).getOrNull()
        }
        return null
    }

    private fun getAudienceFromRequest(request: MutableHttpRequest<*>): String {
        val fullURI = request.uri
        return fullURI.scheme + "://" + fullURI.host
    }
}
