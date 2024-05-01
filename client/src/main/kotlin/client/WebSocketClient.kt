package client

import common.UpdateDescriptor
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.logging.Logger


class WebSocketClient(
    private val host: String,
    private val port: Int,
    private val updateInputChannel: SendChannel<UpdateDescriptor>,
    private val serverPostChannel: ReceiveChannel<UpdateDescriptor>,
) {
    private val serverPostScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val serverResponseScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val outerScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val httpClient: HttpClient = HttpClient {
        install(WebSockets)
    }

    private var currentJob: Job? = null

    fun start() {
        currentJob = outerScope.launch {
            httpClient.webSocket(method = HttpMethod.Get, host = host, port = port) {
                val job1 = serverResponseScope.launch {
                    LOG.info("Accepting server responses")
                    for (message in incoming) {
                        message as? Frame.Text ?: continue
                        val update = Json.decodeFromString<UpdateDescriptor>(message.readText())
                        LOG.info("Server response: $update")
                        updateInputChannel.send(update)
                    }
                }
                val job2 = serverPostScope.launch {
                    for (update in serverPostChannel) {
                        val message = Json.encodeToString(update)
                        LOG.info("Sending message to the server: $message")
                        send(message)
                    }
                }
                joinAll(job1, job2)
            }
        }
    }

    fun stop() {
        runBlocking {
            currentJob?.cancelAndJoin()
        }
        httpClient.close()
    }
}

private val LOG = Logger.getLogger("WebSocketClient")
