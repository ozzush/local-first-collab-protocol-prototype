package common

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BulkSendChannel<E>(private val channel: SendChannel<E>) : SendChannel<E> by channel {
    val mutex = Mutex()
    override suspend fun send(element: E) {
        mutex.withLock {
            channel.send(element)
        }
    }

    suspend fun sendBulk(elements: Iterable<E>) {
        mutex.withLock {
            elements.forEach { element -> channel.send(element) }
        }
    }
}