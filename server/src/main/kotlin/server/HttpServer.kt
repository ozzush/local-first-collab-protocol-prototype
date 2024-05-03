package server

import common.SynchronizeRequest
import common.SynchronizeResponse
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class HttpServer(port: Int, private val server: Server) : NanoHTTPD("localhost", port) {
    override fun serve(session: IHTTPSession): Response {
        LOG.info(session.uri)
        return when (session.uri) {
            "/fetch" -> {
                val log = server.database
                val logString = Json.encodeToString(log)
                newFixedLengthResponse(Status.OK, "application/json", logString)
            }

            "/synchronize" -> {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val requestStr = files["postData"]
                                 ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing request body")
                val request = try {
                    Json.decodeFromString<SynchronizeRequest>(requestStr)
                } catch (e: Exception) {
                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Request body must be a valid json of List<UpdateDescriptor>")
                }
                val updates = request.updates
                val processedUpdates = server.synchronize(updates)
                val missingUpdates = server.getUpdatesStartingFrom(request.lastConfirmedId)
                val syncResponse = SynchronizeResponse(missingUpdates, processedUpdates != null)
                val responseJson = Json.encodeToString(syncResponse)
                newFixedLengthResponse(Status.OK, "application/json", responseJson)
            }

            else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}

private val LOG = Logger.getLogger("HttpServer")