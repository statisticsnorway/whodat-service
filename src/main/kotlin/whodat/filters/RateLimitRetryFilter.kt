package whodat.filters

import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.*
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.*
import java.time.Duration
import java.time.format.DateTimeFormatter

fun retryDelayFrom(ex: HttpClientResponseException): Duration {
    val headers = ex.response.headers
    val v = headers.getFirst("Retry-After").orElse(null) ?: return Duration.ofSeconds(1)

    // Either seconds or RFC 1123 date
    v.toLongOrNull()?.let { return Duration.ofSeconds(it) }

    return try {
        val until = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        val d = Duration.between(Instant.now(), until)
        if (d.isNegative) Duration.ofSeconds(1) else d
    } catch (_: Exception) {
        Duration.ofSeconds(1)
    }
}

@Singleton
@RateLimitRetryFilterMatcher
class RateLimitRetryFilter : HttpClientFilter {
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
