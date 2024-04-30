package client

import common.LogModel
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class FetchClient(
    private val host: String,
    private val port: Int,
    private val resource: String,
) {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    fun fetch(): LogModel {
        val uri = "http://$host:$port/$resource"
        LOG.info("Fetching project from $uri")
        val response = runBlocking { httpClient.get(uri) }
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Fatal! Couldn't fetch the latest version of the project")
        }
        val responseText = runBlocking { response.bodyAsText() }
        return Json.decodeFromString(responseText)
    }
}

private val LOG = Logger.getLogger("FetchClient")