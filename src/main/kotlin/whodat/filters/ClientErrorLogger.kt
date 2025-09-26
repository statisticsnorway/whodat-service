package whodat.filters

import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

@Filter(serviceId = ["cloud-identity-service"])
class ClientErrorLoggerFilter : HttpClientFilter {
    private val log = LoggerFactory.getLogger(ClientErrorLoggerFilter::class.java)

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> =
        Flux.from(chain.proceed(request)).doOnNext { resp ->
            if (resp.status.code >= 400) {
                val body = resp.body() ?: "<no body>"
                log.error(
                    "Cloud Identity HTTP {} {} -> {} body={}",
                    request.method,
                    request.uri,
                    resp.status,
                    body,
                )
            }
        }
}
