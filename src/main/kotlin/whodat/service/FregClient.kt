package no.ssb.whodat.service

import io.micronaut.http.MediaType.APPLICATION_JSON
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.RetryPredicate
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton
import no.ssb.whodat.service.WhodatModifiers
import no.ssb.whodat.service.WhodatVariables
import org.slf4j.LoggerFactory
import whodat.exceptions.FregUpstreamException

import whodat.filters.RateLimitRetryFilterMatcher
import java.net.ConnectException
import java.net.SocketTimeoutException

@Serdeable
data class FregClientRequest(
    val navn: String? = null,
    val kjoenn: String? = null,
    val foedselsdato: String? = null,
    val foedselsaarFraOgMed: String? = null,
    val foedselsaarTilOgMed: String? = null,
    val adressenavn: String? = null,
    val husnummer: String? = null,
    val postnummer: String? = null,
    val kommunenummer: String? = null,
    val fylkesnummer: String? = null,
    val inkluderOppholdsadresse: Boolean? = null,
    val soekFonetisk: Boolean? = null,
    val inkluderDoede: Boolean? = null,
    val opplysningsgrunnlag: String? = null,
    val maksTreff: Int? = null,
) {
    companion object {
        fun from(
            variables: WhodatVariables,
            modifiers: WhodatModifiers,
        ) = FregClientRequest(
            navn = variables.navn,
            kjoenn = variables.kjoenn,
            foedselsdato = variables.foedselsdato,
            foedselsaarFraOgMed = variables.foedselsaarFraOgMed,
            foedselsaarTilOgMed = variables.foedselsaarTilOgMed,
            adressenavn = variables.adressenavn,
            husnummer = variables.husnummer,
            postnummer = variables.postnummer,
            kommunenummer = variables.kommunenummer,
            fylkesnummer = variables.fylkesnummer,
            inkluderOppholdsadresse = modifiers.inkluderOppholdsadresse,
            soekFonetisk = modifiers.soekFonetisk,
            inkluderDoede = modifiers.inkluderDoede,
            opplysningsgrunnlag = modifiers.opplysningsgrunnlag,
            maksTreff = modifiers.maksTreff,
        )
    }
}

@Serdeable
data class FregClientResponse(
    val foedselsEllerDNummer: List<String>,
)

// @ClientProgressFilterMatcher // Uncomment to view RPS
@RateLimitRetryFilterMatcher
@Client(id = "freg")
interface FregClient {
    @Get("/folkeregisteret/offentlig-med-hjemmel/api/v1/personer/soek{?request*}")
    @Consumes(APPLICATION_JSON)
    @Retryable(predicate = FregRetryPredicate::class, attempts = "5", delay = "2s")
    suspend fun searchFnrInternal(
        @Header authorization: String,
        @QueryValue request: FregClientRequest,
    ): FregClientResponse

    suspend fun searchFnr(
        req: FregClientRequest,
        rowIndex: Int,
        fetchToken: suspend () -> String
    ): FregClientResponse {
        try {
                val token = fetchToken()
                return searchFnrInternal(token, req)
        } catch (e: HttpClientResponseException) {
            throw FregUpstreamException(e, rowIndex)
        }
    }
}

@Singleton
class FregRetryPredicate : RetryPredicate {

    private val logger = LoggerFactory.getLogger(FregRetryPredicate::class.java)

    override fun test(exception: Throwable): Boolean {
        val retryable = isRetryable(exception)
        logger.info("Retry check for exception: {}. Retryable: {}", exception::class.simpleName, retryable)
        return retryable
    }

    // Helper method to determine if an exception is retryable
    private fun isRetryable(exception: Throwable): Boolean {
        val retryStatusCodes: Set<Int> = buildSet {
            addAll(500..599)
            add(401)
        }
        return when (exception) {
            is HttpClientResponseException -> exception.status.code in retryStatusCodes
            is SocketTimeoutException, is ConnectException -> true
            else -> false
        }
    }
}
