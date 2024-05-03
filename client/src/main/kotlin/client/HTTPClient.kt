package client

import common.DatabaseMock
import common.SynchronizeRequest
import common.SynchronizeResponse
import common.UpdateDescriptor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Exception
import java.util.logging.Logger

class HTTPClient(
    private val host: String,
    private val port: Int,
    private val fetchResource: String,
    private val synchronizeResource: String,
) {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    fun fetch(): DatabaseMock {
        val uri = "http://$host:$port/$fetchResource"
        LOG.info("Fetching project from $uri")
        val response = runBlocking { httpClient.get(uri) }
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Couldn't fetch the latest version of the project")
        }
        val responseText = runBlocking { response.bodyAsText() }
        return Json.decodeFromString(responseText)
    }

    fun synchronize(updates: List<UpdateDescriptor>, lastConfirmedId: String): SynchronizeResponse {
        val uri = "http://$host:$port/$synchronizeResource"
        LOG.info("Synchronizing project using $uri")
        val request = Json.encodeToString(SynchronizeRequest(updates, lastConfirmedId))
        val response = runBlocking {
            httpClient.post (uri)
            {
                setBody(request)
                contentType(ContentType.Application.Json)
            }
        }
        val responseStr = runBlocking { response.bodyAsText() }
        return Json.decodeFromString(responseStr)
    }
}

private val LOG = Logger.getLogger("HTTPClient")