package whodat.filters

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import whodat.metrics.RequestRateLogger

@Singleton
@Requires(env = ["local"])
@ClientProgressFilterMatcher
class ClientProgressFilter(
    @param:Nullable private val rate: RequestRateLogger?,
) : HttpClientFilter {
    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        rate?.onStart()
        return Mono
            .from(chain.proceed(request))
            .doFinally { rate?.onDone() }
    }
}

@FilterMatcher
annotation class ClientProgressFilterMatcher
