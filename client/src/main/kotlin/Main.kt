import client.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import common.ClientUpdate
import common.UpdateDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.Exception
import java.util.concurrent.Executors

fun main(args: Array<String>) = DevClientMain().main(args)

class DevClientMain : CliktCommand() {
    private val name by option("--name", help = "Name that identifies this client").required()
    private val wsPort by option("--ws-port", help = "WebSocket port to connect to").int()
        .default(9001)
    private val host by option("--host", help = "Address of the server").default("localhost")

    override fun run() {
        val updateInputChannel = Channel<UpdateDescriptor>()
        val serverPostChannel = Channel<ClientUpdate>()
        val updateInputScope = CoroutineScope(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )
        val clientInputScope = CoroutineScope(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )

        val client = Client(name, updateInputChannel, serverPostChannel)
        val webSocketClient = WebSocketClient(host, wsPort, updateInputChannel, serverPostChannel)

        client.start()
        webSocketClient.start()
        val clientInputJob = clientInputScope.launch {
            while (true) {
                val message = readLine() ?: return@launch
                if (message.equals("exit", true)) return@launch
                val newId = try {
                    message.toLong()
                } catch (e: Exception) {
                    println("Enter a number!")
                    continue
                }
                val updateDescriptor = UpdateDescriptor(name, newId, null)
                updateInputScope.launch {
                    updateInputChannel.send(updateDescriptor)
                }
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