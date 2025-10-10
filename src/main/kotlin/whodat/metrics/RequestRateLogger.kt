package whodat.metrics

import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import no.ssb.whodat.service.FregClient
import org.slf4j.LoggerFactory
import whodat.filters.ClientProgressFilterMatcher
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

@Requires(condition = AnyClientWithMatcher::class) // Only activate logger if filter is in use
@Singleton
class RequestRateLogger {
    private val log = LoggerFactory.getLogger(RequestRateLogger::class.java)
    private val completed = LongAdder()
    private val started = LongAdder()
    private val inflight = AtomicInteger()

    fun onStart() {
        started.increment()
        inflight.incrementAndGet()
    }

    fun onDone() {
        completed.increment()
        inflight.decrementAndGet()
    }

    @Scheduled(fixedDelay = "1s")
    fun report() {
        val c = completed.sumThenReset()
        val s = started.sumThenReset()
        val inF = inflight.get()
        log.info("FREG rate: started={} req/s, completed={} req/s, inflight={}", s, c, inF)
    }
}

@Singleton
class AnyClientWithMatcher : Condition {
    override fun matches(context: ConditionContext<*>): Boolean {
        val bc = context.beanContext ?: return false

        val defs = bc.getBeanDefinitions(Qualifiers.byStereotype(ClientProgressFilterMatcher::class.java))

        return defs.any { it.annotationMetadata.hasAnnotation(Client::class.java) }
    }
}
