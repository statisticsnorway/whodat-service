package whodat.filters

import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.MDC
import ulid.ULID
import java.util.*

@ServerFilter(Filter.MATCH_ALL_PATTERN)
class CorrelationIdFilter {
    @RequestFilter
    fun correlationIdFilter(
        request: HttpRequest<*>,
        mutablePropagatedContext: MutablePropagatedContext,
    ) {
        val correlationID =
            Optional
                .ofNullable<String?>(request.getHeaders().get(CORRELATION_ID_HEADER))
                .orElse(ULID.nextULID().toString())

        MDC.put(CORRELATION_ID_NAME, correlationID)
        mutablePropagatedContext.add(MdcPropagationContext())
        MDC.remove(CORRELATION_ID_NAME)
    }

    @ResponseFilter
    fun correlationIdHeaderFilter(response: MutableHttpResponse<*>) {
        val header = MDC.get(CORRELATION_ID_NAME)
        response.getHeaders().add(CORRELATION_ID_HEADER, header)
    }

    companion object {
        const val CORRELATION_ID_HEADER: String = "X-Correlation-Id"
        const val CORRELATION_ID_NAME: String = "CorrelationID"
    }
}
