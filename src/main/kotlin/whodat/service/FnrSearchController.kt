package no.ssb.whodat.service

import com.mycompany.model.Folkeregisterettilgjengeliggjoeringhendelsev1bulkrequestHendelseBulkoppslagRequest
import com.mycompany.model.Folkeregisterettilgjengeliggjoeringhendelsev1bulkresponseHendelseBulkoppslagResponse
import com.mycompany.model.Folkeregisterettilgjengeliggjoeringpersonv1Folkeregisterperson
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.serde.annotation.Serdeable
import no.ssb.whodat.gcp.GCPSecretManagerClient
import whodat.service.MaskinportenGuardianClient
import java.io.File

@Serdeable
data class findPersonsRequest(
    val foedselsEllerDNummer: String
)

@Controller()
private class FnrSearchController(
    private val maskinPortenGuardianClient: MaskinportenGuardianClient,
    private val keycloakClient: KeycloakClient,
    private val gcpSecretManagerClient: GCPSecretManagerClient,
    private val fregClient: FregClient
) {

    @Post("/search")
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun searchFnr(request: FregClientRequest): HttpResponse<FregClientResponse> {

        val authString = "${gcpSecretManagerClient.clientId}:${gcpSecretManagerClient.clientSecret}"
        val encoded = java.util.Base64.getEncoder().encodeToString(authString.toByteArray())
        val keycloakResponse = keycloakClient.fetchAccessToken("Basic $encoded", mapOf(
            "grant_type" to "client_credentials"
        ))
        
        val maskinPortenResponse = maskinPortenGuardianClient.fetchAccessToken(authorization = "Bearer ${keycloakResponse.accessToken}", emptyMap())

        val file = File("tester.txt")
        file.writeText(maskinPortenResponse.accessToken)

        val results = fregClient.searchFnr("Bearer ${maskinPortenResponse.accessToken}", request)
        return HttpResponse.ok(results)
    }

    @Post("/findpersons")
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun findPersons(request: findPersonsRequest): HttpResponse<Folkeregisterettilgjengeliggjoeringpersonv1Folkeregisterperson> {

        val authString = "${gcpSecretManagerClient.clientId}:${gcpSecretManagerClient.clientSecret}"
        val encoded = java.util.Base64.getEncoder().encodeToString(authString.toByteArray())
        val keycloakResponse = keycloakClient.fetchAccessToken("Basic $encoded", mapOf(
            "grant_type" to "client_credentials"
        ))

        val maskinPortenResponse = maskinPortenGuardianClient.fetchAccessToken(authorization = "Bearer ${keycloakResponse.accessToken}", emptyMap())

        val file = File("tester.txt")
        file.writeText(maskinPortenResponse.accessToken)

        val results = fregClient.findPersons("Bearer ${maskinPortenResponse.accessToken}", request.foedselsEllerDNummer)

        return HttpResponse.ok(results)
    }
}