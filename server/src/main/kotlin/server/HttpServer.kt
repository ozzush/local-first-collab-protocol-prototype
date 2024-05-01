package server

import common.BulkSendChannel
import common.SynchronizeResponse
import common.UpdateDescriptor
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class HttpServer(port: Int, private val server: Server, private val serverResponseChannel: BulkSendChannel<UpdateDescriptor>) : NanoHTTPD("localhost", port) {
    override fun serve(session: IHTTPSession): Response {
        LOG.info(session.uri)
        return when (session.uri) {
            "/fetch" -> {
                val log = server.database
                val logString = Json.encodeToString(log)
                newFixedLengthResponse(Status.OK, "application/json", logString)
            }
            "/synchronize" -> {
                session.parseBody(mutableMapOf<String, String>())
                val updatesStr = session.queryParameterString
                val updates = Json.decodeFromString<List<UpdateDescriptor>>(updatesStr)
                val processedUpdates = server.synchronize(updates)
                val syncResponse = SynchronizeResponse(server.database, processedUpdates != null)
                val responseJson = Json.encodeToString(syncResponse)
                if (processedUpdates != null) {
                    runBlocking { serverResponseChannel.sendBulk (processedUpdates) }
                }
                newFixedLengthResponse(Status.OK, "application/json", responseJson)
            }

            else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}

private val LOG = Logger.getLogger("HttpServer")