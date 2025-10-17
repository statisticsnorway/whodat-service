package whodat.exceptions

import io.micronaut.http.*
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton

@Serdeable
data class FregApiError(
    val code: String,
    val message: String,
    val upstreamStatus: Int?,
    val upstreamBody: String?,
    val rowIndex: Int,
)

@Produces(MediaType.APPLICATION_JSON)
@Singleton
class FregExceptionHandler : ExceptionHandler<FregUpstreamException, HttpResponse<FregApiError>> {
    override fun handle(
        request: HttpRequest<*>,
        e: FregUpstreamException,
    ): HttpResponse<FregApiError> {
        val ex = e.upstream
        val rowIndex = e.rowIndex

        val upstreamStatus = ex.response?.status
        val upstreamBody = ex.response?.getBody(String::class.java)?.orElse(null)

        val body =
            FregApiError(
                code = "FREG_UPSTREAM_ERROR",
                message = "FREG lookup failed",
                upstreamStatus = upstreamStatus?.code,
                upstreamBody = upstreamBody,
                rowIndex = rowIndex,
            )

        val statusToReturn = upstreamStatus ?: HttpStatus.BAD_GATEWAY
        return HttpResponse.status<FregApiError>(statusToReturn).body(body)
    }
}
