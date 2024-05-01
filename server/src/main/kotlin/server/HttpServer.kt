package server

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
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

            else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}

private val LOG = Logger.getLogger("HttpServer")