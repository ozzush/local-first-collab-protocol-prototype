import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.channels.Channel
import java.util.logging.Logger
import common.ClientUpdate
import common.ServerResponse
import server.HttpServer
import server.Server
import server.WebSocketServer

fun main(args: Array<String>) = DevServerMain().main(args)

class DevServerMain : CliktCommand() {
    private val port by option("--port", help = "HTTP Port to listen on").int()
        .default(9000)
    private val wsPort by option("--ws-port", help = "WebSocket port to listen on").int()
        .default(9001)

    override fun run() {
        LOG.info("Starting WebSocketServer on port $wsPort")

        val updateInputChannel = Channel<ClientUpdate>()
        val serverResponseChannel = Channel<ServerResponse>()
        val server = Server(updateInputChannel, serverResponseChannel)
        val httpServer = HttpServer(port, server)
        val webSocketServer = WebSocketServer(wsPort, updateInputChannel, serverResponseChannel)
        server.start()
        webSocketServer.start(0, false)
        httpServer.start()
    }
}

private val LOG = Logger.getLogger("StartupServer")