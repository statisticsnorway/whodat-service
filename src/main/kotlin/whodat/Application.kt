package no.ssb.whodat

import io.micronaut.runtime.Micronaut.run
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server

@OpenAPIDefinition(
	info = Info(
		title = "my-openapi-app",
		version = "0.0"
	),
	servers= [
		Server(url = "http://localhost:8080", description = "Local development")
			 ],
)
object Api {
}


fun main(args: Array<String>) {
	run(*args)
}
