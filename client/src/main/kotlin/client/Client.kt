package client

import common.DatabaseMock
import common.SynchronizeResponse
import common.UpdateDescriptor
import common.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.Executors
import java.util.logging.Logger

class Client(
    private val name: String,
    private val updateInputChannel: ReceiveChannel<UpdateDescriptor>,
    private val serverChannel: SendChannel<UpdateDescriptor>,
    private val client: HTTPClient
) {
    private val eventLoopScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val unconfirmedUpdates = mutableListOf<UpdateDescriptor>()
    private val fetchedAheadServerUpdates = mutableListOf<UpdateDescriptor>()

    private var clientDatabase = DatabaseMock()
    private var serverDatabase = DatabaseMock()

    private var currentJob: Job? = null

    private val clientBaseId
        get() = clientDatabase.lastUpdate().id

    private val serverBaseId
        get() = serverDatabase.lastUpdate().id

    private fun printClientDatabase() {
        println("""Client database with last id $clientBaseId
            |--------------------------
            |${clientDatabase.toPrettyString()}
            |--------------------------
        """.trimMargin())
    }

    private fun printServerDatabase() {
        println("""Server database with last id $serverBaseId
            |--------------------------
            |${serverDatabase.toPrettyString()}
            |--------------------------
        """.trimMargin())
    }

    fun start() {
        serverDatabase = client.fetch()
        clientDatabase = serverDatabase.deepCopy()
        printClientDatabase()

        currentJob = eventLoopScope.launch {
            LOG.info("Processing updates")
            for (update in updateInputChannel) {
                LOG.info("Next update: $update")
                processUpdate(update)
            }
        }
    }

    fun stop() {
        runBlocking {
            currentJob?.cancelAndJoin()
        }
    }

    private fun applyToServerDatabase(update: UpdateDescriptor) {
        if (fetchedAheadServerUpdates.isNotEmpty()) {
            check(fetchedAheadServerUpdates.first().id == update.id)
            fetchedAheadServerUpdates.removeFirst()
        } else {
            check(update.baseId == serverBaseId)
            serverDatabase.apply(update)
            printServerDatabase()
        }
    }

    private fun processUpdate(update: UpdateDescriptor) {
        // Discarded update from another client
        // Although ideally such messages shouldn't be sent by the server at all
        if (update.author != name && update.status == UpdateStatus.REJECT) {
            return
        }

        when (update.status) {
            UpdateStatus.LOCAL -> {
                LOG.info("...local update")
                val realUpdate = update.copy(baseId = clientBaseId)
                clientDatabase.apply(realUpdate)
                printClientDatabase()
                unconfirmedUpdates.add(realUpdate)
                runBlocking { serverChannel.send(realUpdate) }
            }

            UpdateStatus.COMMIT -> {
                applyToServerDatabase(update)
                printServerDatabase()
                if (update.author == name) {
                    LOG.info("...confirmation from server")
                    if (update.id == unconfirmedUpdates.getOrNull(0)?.id) {
                        unconfirmedUpdates.removeFirst()
                    }
                } else if (update.baseId == clientBaseId) {
                    LOG.info("...applying")
                    clientDatabase.apply(update)
                    printClientDatabase()
                } else {
                    LOG.info("...can't apply")
                    // The client diverged from the server and needs to synchronize
                    synchronize()
                }
            }
            UpdateStatus.REJECT -> {
                // The server rejected this client's update.
                // The server must have processed a conflicting update before
                // receiving this update and rejecting it.
                // The client should have already synchronized with the server
                // when he received the conflicting update, so we don't synchronize again.
                LOG.info("...update rejected")
            }
        }
    }

    private fun synchronize(): SynchronizeResponse? {
        return if (unconfirmedUpdates.isNotEmpty()) {
            LOG.info("...synchronizing")
            LOG.info("Unconfirmed updates: $unconfirmedUpdates")
            val response = client.synchronize(unconfirmedUpdates, serverBaseId)
            LOG.info("Synchronization response: $response")
            unconfirmedUpdates.clear()
            serverDatabase.applyAll(response.updates)
            fetchedAheadServerUpdates += response.updates
            clientDatabase = serverDatabase.deepCopy()
            printClientDatabase()
            response
            // TODO: In GanttProject prompt the user to do something if the updates where not committed
        } else {
            null
        }
    }
}


private val LOG = Logger.getLogger("Client")
