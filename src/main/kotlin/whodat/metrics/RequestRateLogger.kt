package whodat.metrics

import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

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
