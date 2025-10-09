package whodat.filters

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import whodat.metrics.RequestRateLogger

@Singleton
@Requires(env = ["local"])
@ServerFilter(Filter.MATCH_ALL_PATTERN)
class ClientProgressFilter(
    private val rate: RequestRateLogger,
) : HttpClientFilter {
    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        rate.onStart()
        return Mono
            .from(chain.proceed(request))
            .doFinally { rate.onDone() }
    }
}

@FilterMatcher
annotation class ClientProgressFilterMatcher
