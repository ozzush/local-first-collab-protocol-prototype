package client

import common.DatabaseMock
import common.UpdateDescriptor
import common.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.logging.Logger

class Client(
    private val name: String,
    private val updateInputChannel: Channel<UpdateDescriptor>,
    private val serverChannel: Channel<UpdateDescriptor>,
    private val client: FetchClient
) {
    private val eventLoopScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val channelScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val unconfirmedUpdates = mutableListOf<UpdateDescriptor>()

    private var database = DatabaseMock()

    private var currentJob: Job? = null

    private fun loadDatabase(newDatabase: DatabaseMock) {
        database = newDatabase
        unconfirmedUpdates.clear()
    }

    private fun fetchAndLoadNewDatabase() {
        val newDatabase = client.fetch()
        loadDatabase(newDatabase)
    }

    fun start() {
        loadDatabase(client.fetch())
        println("Current log: ${database.data()}")
        currentJob = eventLoopScope.launch {
            LOG.info("Processing updates")
            for (update in updateInputChannel) {
                processUpdate(update)
                println("Current log: ${database.data()}")
            }
        }
    }

    fun stop() {
        runBlocking {
            currentJob?.cancelAndJoin()
        }
    }

    private fun processUpdate(update: UpdateDescriptor) {
        // Discarded update from another client
        // Although ideally such messages shouldn't be sent by the server at all
        if (update.author != name && update.status == UpdateStatus.REJECT) return

        when (update.status) {
            UpdateStatus.LOCAL -> {
                val realUpdate = update.copy(baseId = baseId())
                database.apply(realUpdate)
                unconfirmedUpdates.add(realUpdate)
                channelScope.launch {
                    serverChannel.send(realUpdate)
                }
            }
            UpdateStatus.COMMIT -> {
                if (update.author == name) {
                    unconfirmedUpdates.removeAt(0)
                } else if (update.baseId == baseId()) {
                    database.apply(update)
                } else {
                    // The client diverged from the server and needs to fetch the latest
                    // version of the project
                    fetchAndLoadNewDatabase()
                }
            }
            // The server rejected this client's update.
            // The client should discard its local state and fetch project state from the server
            UpdateStatus.REJECT -> {
                // TODO: INITIATE SYNCHRONIZATION PHASE AND SENDING ALL UNCOMMITTED UPDATES
                // TODO: TO THE SERVER
                fetchAndLoadNewDatabase()
            }
        }
    }

    private fun baseId() = database.lastUpdate().id
}


private val LOG = Logger.getLogger("Client")
