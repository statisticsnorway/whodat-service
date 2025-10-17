package whodat.exceptions

import io.micronaut.http.client.exceptions.HttpClientResponseException

class FregUpstreamException(
    val upstream: HttpClientResponseException,
    val rowIndex: Int,
) : RuntimeException(upstream.message, upstream)
