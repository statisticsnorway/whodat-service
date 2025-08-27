package no.ssb.whodat.gcp

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import org.yaml.snakeyaml.Yaml

@Requires(notEnv = ["test"])
@Singleton
class GCPSecretManagerClient(
    private val client: SecretManagerServiceClient,
) {
    lateinit var clientId: String
    lateinit var clientSecret: String

    @Property(name = "keycloak.gcp-client-project-id")
    lateinit var project: String

    @Property(name = "micronaut.gcp.secret-manager.location")
    lateinit var location: String

    @Property(name = "keycloak.gcp-client-team-uniform-name")
    lateinit var teamUniformName: String

    @Property(name = "guardian.mp-client-id")
    lateinit var mpClientId: String

    @EventListener
    fun onStartup(event: StartupEvent?) {
        val response =
            client.accessSecretVersion(
                AccessSecretVersionRequest
                    .newBuilder()
                    .setName(
                        SecretVersionName
                            .ofProjectSecretSecretVersionName(
                                project,
                                "$teamUniformName-ssb-maskinporten-$mpClientId-credentials",
                                "latest",
                            ).toString(),
                    ).build(),
            )
        val secret: String = response.payload.data.toStringUtf8()

        val yaml = Yaml()
        val secretMap: Map<String, String> = yaml.load(secret)

        val clientId = secretMap.get("client_id")
        val clientSecret = secretMap.get("client_secret")

        if (clientId == null) {
            throw IllegalStateException("Client ID is not set")
        }

        if (clientSecret == null) {
            throw IllegalStateException("Client Secret is not set")
        }

        this.clientId = clientId
        this.clientSecret = clientSecret
    }
}
