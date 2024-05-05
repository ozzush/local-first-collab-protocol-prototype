import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import common.UpdateDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.util.logging.Logger
import server.HttpServer
import server.Server
import server.WebSocketServer
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) = DevServerMain().main(args)

class DevServerMain : CliktCommand() {
    private val port by option("--port", help = "HTTP Port to listen on").int()
        .default(9000)
    private val wsPort by option("--ws-port", help = "WebSocket port to listen on").int()
        .default(9001)

    private val lifetime by option("--lifetime", help = "Amount of seconds that this instance lives for").int()
    private val statFile by option("--stat-file", help = "File to write statistics to")

    override fun run() {
        LOG.info("Starting HttpServer on port $port")
        LOG.info("Starting WebSocketServer on port $wsPort")

        val updateInputChannel = Channel<UpdateDescriptor>()
        val serverResponseChannel = Channel<UpdateDescriptor>()
        val server = Server(updateInputChannel, serverResponseChannel)
        val httpServer = HttpServer(port, server)
        val webSocketServer = WebSocketServer(wsPort, updateInputChannel, serverResponseChannel)
        server.start()
        webSocketServer.start(0, false)
        httpServer.start()
        if (lifetime == null) {
            while (true) {
                val message = readLine() ?: break
                if (message.equals("exit", ignoreCase = true)) break
            }
        } else {
            runBlocking { delay(lifetime!!.seconds) }
        }
        server.stop()
        webSocketServer.stop()
        httpServer.stop()
        val stat = """
            Total processed updates:      ${server.updatesProcessed}
            Rejected updates:             ${server.rejectedUpdates}
            Total synchronization calls:  ${server.syncCalls}
            Failed synchronization calls: ${server.failedSyncCalls}
            
        """.trimIndent()
        if (statFile != null) {
            File(statFile!!).writeText(stat)
        } else {
            print(stat)
        }
        exitProcess(0)
    }
}

private val LOG = Logger.getLogger("StartupServer")