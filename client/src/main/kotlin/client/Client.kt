package client

import common.ClientUpdate
import common.LogModel
import common.UpdateDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.logging.Logger

class Client(
    private val name: String,
    private val updateInputChannel: Channel<UpdateDescriptor>,
    private val serverChannel: Channel<ClientUpdate>
) {
    private val eventLoopScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val channelScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val unappliedUpdates = mutableListOf<UpdateDescriptor>()
    private val unconfirmedUpdates = mutableListOf<ClientUpdate>()

    private val log = LogModel()

    private var localLog: LogModel? = null

    private val displayedLog
        get() = localLog ?: log

    private var currentJob: Job? = null

    fun start() {
        currentJob = eventLoopScope.launch {
            LOG.info("Processing updates")
            for (update in updateInputChannel) {
                processUpdate(update)
                println("Current log: ${displayedLog.data()}")
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
        if (update.author != name && update.assignedId == -1L) return

        // The server rejected this client's update. The client should discard its local state and
        // fetch relevant state from the server
        if (update.author == name && update.assignedId == -1L) {
            throw RuntimeException("Fatal! Update was rejected by the server!")
        }

        if (update.author == name) {
            // This update was generated locally
            if (update.assignedId == null) {
                val base = baseTxnId()
                if (localLog == null) localLog = log.deepCopy()
                localLog!!.add(update)
                val clientUpdate = ClientUpdate(name, base, update.initialId, hasUnconfirmedUpdates())
                unconfirmedUpdates.add(clientUpdate)
                channelScope.launch {
                    serverChannel.send(clientUpdate)
                }
            // This is an acknowledgement from the server
            } else {
                unconfirmedUpdates.removeAt(0)
                log.add(update)
                if (unconfirmedUpdates.isEmpty()) {
                    localLog = null
                }
            }
        // This is an update from another client, processed by the server
        } else {
            log.add(update)
        }
    }

    private fun baseTxnId() = localLog?.last()?.initialId ?: log.last().assignedId!!

    private fun hasUnconfirmedUpdates() = unconfirmedUpdates.isNotEmpty()
}


private val LOG = Logger.getLogger("Client")
