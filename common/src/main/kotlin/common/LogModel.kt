package common

class LogModel {
    private val log = mutableListOf(UpdateDescriptor("server", 0, 1))

    fun deepCopy() = LogModel().also {
        it.log.clear()
        it += log
    }

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