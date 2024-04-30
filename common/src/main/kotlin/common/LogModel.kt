package common

import kotlinx.serialization.Serializable

@Serializable
data class LogModel(
    private val log: MutableList<UpdateDescriptor> = mutableListOf(UpdateDescriptor("server", 0, 1))
) {
    fun add(updateDescriptor: UpdateDescriptor) {
        log.add(updateDescriptor)
    }

    operator fun plusAssign(other: List<UpdateDescriptor>) {
        log += other
    }

    fun last() = log.last()

    fun data() = log.toList()

    fun lastFromAuthor(author: String) = log.findLast { it.author == author }
}