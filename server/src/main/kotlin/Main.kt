import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.channels.Channel
import java.util.logging.Logger
import common.ClientUpdate
import common.ServerResponse
import server.Server
import server.WebSocketServer

fun main(args: Array<String>) = DevServerMain().main(args)

class DevServerMain : CliktCommand() {
    private val wsPort by option("--ws-port", help = "WebSocket port to listen on").int()
        .default(9001)

    private lateinit var server: Server

    override fun run() {
        LOG.info("Starting WebSocketServer on port $wsPort")

        val updateInputChannel = Channel<ClientUpdate>()
        val serverResponseChannel = Channel<ServerResponse>()
        server = Server(updateInputChannel, serverResponseChannel)
        val webSocketServer = WebSocketServer(wsPort, updateInputChannel, serverResponseChannel)
        webSocketServer.start(0, false)
    }
}

private val LOG = Logger.getLogger("StartupServer")