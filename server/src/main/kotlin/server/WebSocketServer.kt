package server

import common.ClientUpdate
import common.ServerResponse
import kotlinx.coroutines.channels.Channel
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.Executors
import java.util.logging.Logger

class WebSocketServer(
    port: Int,
    private val updateInputChannel: Channel<ClientUpdate>,
    private val serverResponseChannel: Channel<ServerResponse>
) :
    NanoWSD("localhost", port) {
    private val wsResponseScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val wsRequestScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val connectedClients = mutableSetOf<WebSocket>()

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return WebSocketImpl(handshake)
    }

    private inner class WebSocketImpl(handshake: IHTTPSession) : WebSocket(handshake) {
        init {
            wsResponseScope.launch {
                for (response in serverResponseChannel) {
                    try {
                        connectedClients.forEach {
                            it.send(Json.encodeToString(response))
                        }
                    } catch (e: Exception) {
                        LOG.warning("Failed to send response $response because of: ${e.localizedMessage}")
                    }
                }
            }
        }

        override fun onOpen() {
            connectedClients.add(this)
            LOG.info("WebSocket opened")
            LOG.info("Clients: $connectedClients")
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean
        ) {
            connectedClients.remove(this)
            LOG.info("WebSocket closed")
            LOG.info("Clients: $connectedClients")
        }

        override fun onMessage(message: WebSocketFrame) {
            val clientUpdate = Json.decodeFromString<ClientUpdate>(message.textPayload)
            LOG.info("Message received: $clientUpdate")
            wsRequestScope.launch {
                updateInputChannel.send(clientUpdate)
            }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException) {
            LOG.warning("WebSocket exception: $exception")
        }
    }
}

private val LOG = Logger.getLogger("WebSocketServer")