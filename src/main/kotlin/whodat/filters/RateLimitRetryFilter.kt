package whodat.filters

import com.nimbusds.jwt.JWTParser
import io.micronaut.http.*
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.*
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.reactor.mono
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import whodat.service.MaskinportenTokenExchanger
import java.time.Duration
import java.util.Optional

fun retryDelayFrom(ex: HttpClientResponseException): Duration {
    val headers = ex.response.headers
    val header: Long =
        headers
            .getFirst("Retry-After")
            .flatMap { Optional.ofNullable(it.toLongOrNull()) }
            .orElse(1L)

    return Duration.ofSeconds(header)
}

private fun refreshTokenIfNeeded(
    request: MutableHttpRequest<*>,
    exchanger: MaskinportenTokenExchanger,
): Mono<String> =
    mono {
        val current =
            request.headers
                .getFirst(HttpHeaders.AUTHORIZATION)
                .orElse("")
                .removePrefix("Bearer ")
        val expMillis =
            JWTParser
                .parse(current)
                .jwtClaimsSet.expirationTime.time
        if (expMillis - System.currentTimeMillis() <= 10_000) {
            exchanger.tokenExchange() // suspend, but we’re in a coroutine backed by Reactor, so non-blocking
        } else {
            current
        }
    }

private fun copyRequest(orig: MutableHttpRequest<*>): MutableHttpRequest<*> {
    val copy = HttpRequest.create<Any>(orig.method, orig.uri.toString()) // for GET this preserves query params
    // copy headers
    orig.headers.forEach { name, values -> values.forEach { copy.header(name, it) } }
    return copy
}

@Singleton
@RateLimitRetryFilterMatcher
class RateLimitRetryFilter(
    val maskinportenTokenExchanger: Provider<MaskinportenTokenExchanger>,
) : HttpClientFilter {
    private val maxRetries: Long = 5
    private val baseDelay = Duration.ofMillis(500)

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        val attempt = Mono.defer { Mono.from(chain.proceed(request)) }

        return attempt
            .onErrorResume { t ->
                if (t is HttpClientResponseException && t.status == HttpStatus.TOO_MANY_REQUESTS) {
                    val retryDelay = retryDelayFrom(t)
                    refreshTokenIfNeeded(request, maskinportenTokenExchanger.get())
                        .flatMap { token ->
                            val newReq = copyRequest(request).bearerAuth(token)
                            Mono
                                .delay(retryDelay)
                                .then(Mono.from(chain.proceed(newReq)))
                        }
                } else {
                    Mono.error(t)
                }
            }.retryWhen(
                Retry
                    .backoff(maxRetries, baseDelay)
                    .jitter(0.5)
                    .filter { t ->
                        t is HttpClientResponseException && t.status == HttpStatus.TOO_MANY_REQUESTS
                    },
            ).onErrorResume { t ->
                Mono.error(t)
            }
    }
}
