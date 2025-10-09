package no.ssb.whodat.service

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.http.HttpHeaders.ACCEPT
import io.micronaut.http.HttpHeaders.CONTENT_TYPE
import io.micronaut.http.HttpHeaders.USER_AGENT
import io.micronaut.http.MediaType.APPLICATION_JSON
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
import whodat.filters.ClientProgressFilterMatcher
import whodat.filters.RateLimitRetryFilterMatcher

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

@ClientProgressFilterMatcher
// @RateLimitRetryFilterMatcher // Uncomment to view RPS
@Client(id = "freg")
interface FregClient {
    @Get("/folkeregisteret/offentlig-med-hjemmel/api/v1/personer/soek{?request*}")
    @Consumes(APPLICATION_JSON)
    suspend fun searchFnr(
        @Header authorization: String,
        @QueryValue request: FregClientRequest,
    ): FregClientResponse
}
