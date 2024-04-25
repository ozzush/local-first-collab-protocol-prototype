package common

class LogModel {
    private val log = mutableListOf(UpdateDescriptor("server", 0, 1))

    fun add(updateDescriptor: UpdateDescriptor) {
        log.add(updateDescriptor)
        println("Log updated: $log")
    }

    operator fun plusAssign(other: List<UpdateDescriptor>) {
        log += other
        println("Log updated: $log")
    }

    fun last() = log.last()

    fun lastFromAuthor(author: String) = log.findLast { it.author == author }
}