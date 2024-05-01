package server

import common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.logging.Logger

class Server(
    private val updateInputChannel: ReceiveChannel<UpdateDescriptor>,
    private val serverResponseChannel: SendChannel<UpdateDescriptor>
) {
    private val updateInputScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    val database = DatabaseMock().apply { apply(UpdateDescriptor("server", "", "init", UpdateStatus.COMMIT)) }

    fun start() {
        updateInputScope.launch {
            for (update in updateInputChannel) {
                LOG.info("Next update: $update")
                if (shouldCommit(update)) {
                    commit(update)
                } else {
                    reject(update)
                }
            }
        }
    }

    private fun commit(update: UpdateDescriptor): UpdateDescriptor {
        val processedUpdate = update.copy(status = UpdateStatus.COMMIT)
        database.apply(processedUpdate)
        runBlocking { serverResponseChannel.send(processedUpdate) }
        return processedUpdate
    }

    private fun reject(update: UpdateDescriptor): UpdateDescriptor {
        val processedUpdate = update.copy(status = UpdateStatus.REJECT)
        runBlocking { serverResponseChannel.send(processedUpdate) }
        return processedUpdate
    }

    fun synchronize(updates: List<UpdateDescriptor>): List<UpdateDescriptor>? {
        if (updates.isEmpty()) return null
        return if (shouldCommit(updates)) {
            val firstUpdate = updates.first().copy(baseId = baseId())
            val modifiedUpdates = listOf(firstUpdate) + updates.slice(1 until updates.size)
            modifiedUpdates.map { update -> commit(update) }
        } else null
    }

    private fun shouldCommit(updates: List<UpdateDescriptor>): Boolean {
        if (updates.any { it.id.startsWith("-") }) return false
        for (i in updates.indices) {
            if (updates[i + 1].baseId != updates[i].id) return false
        }
        return true
    }

    private fun shouldCommit(update: UpdateDescriptor): Boolean {
        return update.baseId == baseId() && !update.id.startsWith("-")
    }

    private fun baseId() = database.lastUpdate().id
}

private val LOG = Logger.getLogger("Server")
