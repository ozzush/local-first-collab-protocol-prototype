package server

import common.ClientUpdate
import common.LogModel
import common.ServerResponse
import common.UpdateDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.Executors
import java.util.logging.Logger

class Server(
    private val updateInputChannel: Channel<ClientUpdate>,
    private val serverResponseChannel: Channel<ServerResponse>
) {
    private val wsCommunicationScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val log = LogModel()

    init {
        wsCommunicationScope.launch {
            processUpdatesLoop()
        }
    }

    private suspend fun processUpdatesLoop() {
        for (update in updateInputChannel) {
            val response = processUpdate(update)
            serverResponseChannel.send(response)
        }
    }

    private fun processUpdate(update: ClientUpdate): ServerResponse {
        LOG.info("Next update: $update")
        val base = baseTxnId()
        val shouldCommitUpdate = shouldCommit(update)
        val newTxnId = if (shouldCommitUpdate) base + 1 else -1L
        if (shouldCommitUpdate) {
            log.add(UpdateDescriptor(update.author, update.newTxnId, newTxnId))
        }
        return ServerResponse(
            update.author, base, update.newTxnId, newTxnId
        )
    }

    private fun shouldCommit(update: ClientUpdate): Boolean {
        if (update.basedOnLocal) {
            val lastCommitByAuthor = log.lastFromAuthor(update.author)
            if (lastCommitByAuthor == null || lastCommitByAuthor.initialId != update.baseTxnId) {
                LOG.info("Update from client ${update.author} is based on his " +
                         "local update ${update.baseTxnId} " +
                         "but the previous commit from this client " +
                         "is numbered ${lastCommitByAuthor?.initialId}")
                return false
            }
        }
        return update.newTxnId != 0L
    }

    private fun baseTxnId() = log.last().assignedId!!
}

private val LOG = Logger.getLogger("Server")
