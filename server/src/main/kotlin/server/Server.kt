package server

import common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.logging.Logger

class Server(
    private val updateInputChannel: ReceiveChannel<UpdateDescriptor>,
    private val serverResponseChannel: SendChannel<UpdateDescriptor>
) {
    private val updateInputScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private val serverResponseScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    val database = DatabaseMock().apply { apply(UpdateDescriptor("server", "", "init", UpdateStatus.COMMIT)) }

    fun start() {
        updateInputScope.launch {
            for (update in updateInputChannel) {
                val response = processUpdate(update)
                serverResponseScope.launch {
                    serverResponseChannel.send(response)
                }
            }
        }
    }

    private fun processUpdate(update: UpdateDescriptor): UpdateDescriptor {
        LOG.info("Next update: $update")
        val status = if (shouldCommit(update)) UpdateStatus.COMMIT else UpdateStatus.REJECT
        val processedUpdate = update.copy(status = status)
        if (status == UpdateStatus.COMMIT) {
            database.apply(processedUpdate)
        }
        return processedUpdate
    }

    private fun shouldCommit(update: UpdateDescriptor): Boolean {
        return update.baseId == baseId() && update.id != ""
    }

    private fun baseId() = database.lastUpdate().id
}

private val LOG = Logger.getLogger("Server")
