package no.ssb.whodat.service

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.http.HttpHeaders.ACCEPT
import io.micronaut.http.HttpHeaders.CONTENT_TYPE
import io.micronaut.http.HttpHeaders.USER_AGENT
import io.micronaut.http.MediaType.TEXT_PLAIN
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotNull

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
    val inkluderOppholdsadresse: String? = null,
    val soekFonetisk: Boolean? = null,
    val inkluderDoede: Boolean? = null,
    val opplysningsgrunnlag: String? = null,
    val maksTreff: Int? = null,
)

@Serdeable
data class FregClientResponse(
    val foedselsEllerDNummer: List<String>,
)

@Client(id = "freg")
interface FregClient {
    @Get("/folkeregisteret/offentlig-med-hjemmel/api/v1/personer/soek{?request*}")
    @Headers(
        Header(name = CONTENT_TYPE, value = "application/json"),
        Header(name = ACCEPT, value = "application/json"),
    )
    @Consumes(TEXT_PLAIN)
    suspend fun searchFnr(
        @Header authorization: String,
        @QueryValue request: FregClientRequest,
    ): FregClientResponse
}
