package whodat.filters

import com.nimbusds.jwt.JWTParser
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.reactor.mono
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import whodat.service.MaskinportenTokenExchanger
import java.net.SocketException
import java.time.Duration
import java.util.Optional

@FilterMatcher
annotation class RateLimitRetryFilterMatcher

fun retryDelayFrom(ex: HttpClientResponseException): Duration {
    val headers = ex.response.headers
    val header: Long =
        headers
            .getFirst("Retry-After")
            .flatMap { Optional.ofNullable(it.toLongOrNull()) }
            .filter { it > 0 }
            .orElse(1L)

    return Duration.ofSeconds(header)
}

private fun refreshTokenIfNeeded(
    request: MutableHttpRequest<*>,
    exchanger: MaskinportenTokenExchanger,
): Mono<String> {
    val current =
        request.headers
            .getFirst(HttpHeaders.AUTHORIZATION)
            .orElse("")
            .removePrefix("Bearer ")
    val expMillis =
        JWTParser
            .parse(current)
            .jwtClaimsSet.expirationTime.time
    return if (expMillis - System.currentTimeMillis() <= 10_000) {
        mono {
            exchanger.getToken("token")
        }
    } else {
        Mono.just(current)
    }
}

private fun copyRequest(orig: MutableHttpRequest<*>): MutableHttpRequest<*> {
    val copy = HttpRequest.create<Any>(orig.method, orig.uri.toString())
    orig.headers.forEach { n, vs -> vs.forEach { copy.header(n, it) } }
    return copy
}

@Singleton
@RateLimitRetryFilterMatcher
class RateLimitRetryFilter(
    private val maskinportenTokenExchanger: Provider<MaskinportenTokenExchanger>,
) : HttpClientFilter {
    private val log = LoggerFactory.getLogger(RateLimitRetryFilter::class.java)

    override fun doFilter(
        request: MutableHttpRequest<*>,
        chain: ClientFilterChain,
    ): Publisher<out HttpResponse<*>> {
        fun attemptOnce(forceFreshConnection: Boolean = false): Mono<HttpResponse<*>> =
            Mono.defer {
                val fresh =
                    copyRequest(request).apply {
                        if (forceFreshConnection) header(HttpHeaders.CONNECTION, "close")
                    }
                Mono.from(chain.proceed(fresh))
            }

        return attemptOnce()
            .onErrorResume { t ->
                when {
                    // 429: honor Retry-After and retry with a fresh connection
                    t is HttpClientResponseException && t.status == HttpStatus.TOO_MANY_REQUESTS -> {
                        val delay = retryDelayFrom(t)
                        log.info("429 Encountered, delaying ${delay.seconds} seconds")

                        refreshTokenIfNeeded(request, maskinportenTokenExchanger.get())
                            .flatMap { token ->
                                val newReq =
                                    copyRequest(request)
                                        .bearerAuth(token)
                                        .header(HttpHeaders.CONNECTION, "close") // force new socket after 429
                                Mono
                                    .delay(delay, Schedulers.boundedElastic())
                                    .then(Mono.from(chain.proceed(newReq)))
                            }
                    }

                    // Tiny “retry once on reset”: treat low-level SocketException as transient
                    t is HttpClientException && t.cause is SocketException -> {
                        Mono
                            .delay(Duration.ofMillis(150), Schedulers.boundedElastic())
                            .then(attemptOnce(forceFreshConnection = true))
                    }

                    else -> Mono.error(t)
                }
            }
    }
}
