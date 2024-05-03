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

    private var database = DatabaseMock()

    private val appliedUpdates
        get() = database.data().map { it.id }.toSet()

    private var currentJob: Job? = null

    private fun loadDatabase(newDatabase: DatabaseMock) {
        database = newDatabase
        unconfirmedUpdates.clear()
        printDatabase()
    }

    private fun printDatabase() {
        println("""Current database with last id ${baseId()}
            |--------------------------
            |${database.toPrettyString()}
            |--------------------------
        """.trimMargin())
    }

    private fun fetchAndLoadNewDatabase() {
        val newDatabase = client.fetch()
        loadDatabase(newDatabase)
    }

    fun start() {
        fetchAndLoadNewDatabase()

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

    private fun processUpdate(update: UpdateDescriptor) {
        // Discarded update from another client
        // Although ideally such messages shouldn't be sent by the server at all
        if (update.author != name && update.status == UpdateStatus.REJECT) {
            LOG.info("...ignoring")
            return
        }

        when (update.status) {
            UpdateStatus.LOCAL -> {
                LOG.info("...local update")
                val realUpdate = update.copy(baseId = baseId())
                database.apply(realUpdate)
                unconfirmedUpdates.add(realUpdate)
                runBlocking { serverChannel.send(realUpdate) }
            }

            UpdateStatus.COMMIT -> {
                if (update.author == name) {
                    LOG.info("...confirmation from server")
                    if (update.id == unconfirmedUpdates.getOrNull(0)?.id) {
                        LOG.info("...confirmed ${update.id}")
                        unconfirmedUpdates.removeAt(0)
                    } else {
                        LOG.info("...ignoring, id ${update.id} doesn't match last unconfirmed update id " +
                                 "${unconfirmedUpdates.getOrNull(0)?.id}")
                    }
                } else if (update.baseId == baseId()) {
                    LOG.info("...applying")
                    database.apply(update)
                } else if (update.id !in appliedUpdates) {
                    LOG.info("...can't apply")
                    // The client diverged from the server and needs to synchronize
                    synchronize()
                } else {
                    LOG.info("...already applied")
                }
            }
            // The server rejected this client's update.
            // The client should synchronize with the server
            UpdateStatus.REJECT -> {
                LOG.info("...update rejected")
                synchronize()
            }
        }
    }

    private fun synchronize(): SynchronizeResponse? {
        LOG.info("...synchronizing")
        LOG.info("Unconfirmed updates: $unconfirmedUpdates")
        return if (unconfirmedUpdates.isNotEmpty()) {
            val response = client.synchronize(unconfirmedUpdates)
            unconfirmedUpdates.clear()
            loadDatabase(response.database)
            LOG.info("Synchronization response: $response")
            response
            // TODO: In GanttProject prompt the user to do something if the updates where not committed
        } else {
            fetchAndLoadNewDatabase()
            null
        }
    }

    private fun baseId() = database.lastUpdate().id
}


private val LOG = Logger.getLogger("Client")
