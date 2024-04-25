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

    private var currentJob: Job? = null

    fun start() {
        currentJob = eventLoopScope.launch {
            LOG.info("Processing updates")
            for (update in updateInputChannel) {
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
                log.add(update)
                val clientUpdate = ClientUpdate(name, base, update.initialId, hasUnconfirmedUpdates())
                unconfirmedUpdates.add(clientUpdate)
                channelScope.launch {
                    serverChannel.send(clientUpdate)
                }
            // This is an acknowledgement from the server
            } else {
                unconfirmedUpdates.removeAt(0)
                if (unconfirmedUpdates.isEmpty()) {
                    if (unappliedUpdates.isNotEmpty()) {
                        log += unappliedUpdates
                        unappliedUpdates.clear()
                    }
                    lastConfirmedServerId = update.assignedId!!
                }
            }
        } else {
            if (unconfirmedUpdates.isNotEmpty()) {
                unappliedUpdates.add(update)
            } else {
                log.add(update)
            }
        }
    }

    private fun baseTxnId() =
        if (hasUnconfirmedUpdates()) log.last().initialId else lastConfirmedServerId

    private var lastConfirmedServerId: Long = 1

    private fun hasUnconfirmedUpdates() = unconfirmedUpdates.isNotEmpty()
}


private val LOG = Logger.getLogger("Client")
