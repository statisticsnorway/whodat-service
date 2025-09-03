package no.ssb.whodat

import io.micronaut.runtime.Micronaut.run
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server

@OpenAPIDefinition(
    info =
        Info(
            title = "my-openapi-app",
            version = "0.0",
        ),
    security = [SecurityRequirement(name = "Keycloak token")],
    servers = [
        Server(url = "http://localhost:9191", description = "Local development"),
    ],
)
@SecurityScheme(
    name = "Keycloak token",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "jwt",
    paramName = "Authorization"
)
object Api

fun main(args: Array<String>) {
    run(*args)
}
