import client.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import common.RandomStringGenerator
import common.UpdateDescriptor
import common.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

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
    private val networkDelay by option("--network-delay", help = "Simulate network delay in all communication between client and server").double()
        .default(3.0)
    private val avgAutoUpdateFrequency by option("--auto-update", help = "Perform an automatic update every <arg> seconds on average. The delay is picked randomly from the interval [x*0.5, x*1,5)").int()
    private val conflictFraction by option("--conflict-frac", help = "Fraction of conflicting updates. Only used with --auto-update").double().default(0.0)

    private fun updateFromStdin(updateId: String): UpdateDescriptor? {
        val message = readLine() ?: return null
        if (message.equals("exit", true)) return null
        return UpdateDescriptor(name, "", updateId, UpdateStatus.LOCAL, message)
    }

    private suspend fun autoUpdate(updateId: String, doubleGenerator: Random): UpdateDescriptor {
        val updateFrequency = avgAutoUpdateFrequency!!
        val delayValue = doubleGenerator.nextDouble(updateFrequency * 0.5, updateFrequency * 1.5)
        delay(delayValue.seconds)
        val produceConflict = doubleGenerator.nextDouble(1.0) < conflictFraction
        val message = if (produceConflict) "conflict" else ""
        return UpdateDescriptor(name, "", updateId, UpdateStatus.LOCAL, message)
    }

    override fun run() {
        val realSeed = seed ?: name.hashCode().toLong()
        val uidGenerator = RandomStringGenerator(realSeed)
        val doubleGenerator = Random(realSeed)

        val updateInputChannel = Channel<UpdateDescriptor>()
        val serverPostChannel = Channel<UpdateDescriptor>()
        val clientInputScope = CoroutineScope(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )

        val fetchClient = HTTPClient(host, port, fetchResource, syncResource, networkDelay.seconds)

        val client = Client(name, updateInputChannel, serverPostChannel, fetchClient)
        val webSocketClient = WebSocketClient(host, wsPort, updateInputChannel, serverPostChannel, networkDelay.seconds)

        client.start()
        webSocketClient.start()
        val clientInputJob = clientInputScope.launch {
            while (true) {
                val updateId = uidGenerator.generate(5)
                val updateDescriptor = if (avgAutoUpdateFrequency != null) autoUpdate(updateId, doubleGenerator) else updateFromStdin(updateId) ?: break
                updateInputChannel.send(updateDescriptor)
            }
        }

        runBlocking {
            clientInputJob.join()
        }
        webSocketClient.stop()
        client.stop()
        exitProcess(0)
    }
}