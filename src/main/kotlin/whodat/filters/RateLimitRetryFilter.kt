package whodat.filters

import com.nimbusds.jwt.JWTParser
import io.micronaut.http.*
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.*
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import whodat.service.MaskinportenTokenExchanger
import java.time.Duration

fun retryDelayFrom(ex: HttpClientResponseException): Duration {
    val headers = ex.response.headers
    val header =
        headers
            .getFirst("Retry-After")
            .orElse("")
            .toLong()

    return Duration.ofSeconds(header)
}

private fun refreshTokenIfNeededAsync(
    request: MutableHttpRequest<*>,
    exchanger: MaskinportenTokenExchanger,
): Mono<String> =
    Mono
        .fromCallable {
            val current =
                request.headers
                    .getFirst(HttpHeaders.AUTHORIZATION)
                    .orElse("")
                    .removePrefix("Bearer ")
            val expMillis =
                JWTParser
                    .parse(current)
                    .jwtClaimsSet.expirationTime.time
            if (expMillis - System.currentTimeMillis() <= 10_000) exchanger.tokenExchange() else current
        }.subscribeOn(Schedulers.boundedElastic())

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
                // If 429 and Retry-After exists, wait once then retry chain manually
                if (t is HttpClientResponseException && t.status == HttpStatus.TOO_MANY_REQUESTS) {
                    val delay = retryDelayFrom(t)
                    Mono.delay(delay).then(Mono.from(chain.proceed(request)))
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
