import client.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import common.RandomStringGenerator
import common.UpdateDescriptor
import common.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors

fun main(args: Array<String>) = DevClientMain().main(args)

class DevClientMain : CliktCommand() {
    private val name by option("--name", help = "Name that identifies this client").required()
    private val port by option("--port", help = "HTTP port to connect to").int()
        .default(9000)
    private val wsPort by option("--ws-port", help = "WebSocket port to connect to").int()
        .default(9001)
    private val host by option("--host", help = "Address of the server").default("localhost")
    private val fetchResource by option("--fetch-resource", help = "HTTP resource to fetch the most recent version of the project from")
        .default("fetch")
    private val syncResource by option("--sync-resource", help = "HTTP resource to use for project synchronization")
        .default("synchronize")
    private val seed by option("--seed", help = "Seed for the random string generator. Defaults to the name's hash").long()

    override fun run() {
        val uidGenerator = RandomStringGenerator(seed ?: name.hashCode().toLong())
        val updateInputChannel = Channel<UpdateDescriptor>()
        val serverPostChannel = Channel<UpdateDescriptor>()
        val updateInputScope = CoroutineScope(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )
        val clientInputScope = CoroutineScope(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )

        val fetchClient = HTTPClient(host, port, fetchResource, syncResource)

        val client = Client(name, updateInputChannel, serverPostChannel, fetchClient)
        val webSocketClient = WebSocketClient(host, wsPort, updateInputChannel, serverPostChannel)

        client.start()
        webSocketClient.start()
        val clientInputJob = clientInputScope.launch {
            while (true) {
                val message = readLine() ?: return@launch
                if (message.equals("exit", true)) return@launch
                val newId = uidGenerator.generate(5)
                val idWithReject = if (message == "reject") "-$newId" else newId
                val updateDescriptor = UpdateDescriptor(name, "", idWithReject, UpdateStatus.LOCAL)
                updateInputChannel.send(updateDescriptor)
            }
        }

        runBlocking {
            clientInputJob.join()
        }
        webSocketClient.stop()
        client.stop()
        println("Connection closed. Goodbye!")
    }
}