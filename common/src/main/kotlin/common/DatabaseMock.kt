package common

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseMock(
    private val history: MutableList<UpdateDescriptor> = mutableListOf()
) {
    fun apply(updateDescriptor: UpdateDescriptor) {
        history.add(updateDescriptor)
        println("""Applied update ${updateDescriptor.id}
            |--------------------------
            |${toPrettyString()}
            |--------------------------
        """.trimMargin())
    }

    fun lastUpdate() = history.last()

    fun data() = history.toList()

    fun toPrettyString() = history.joinToString(separator = "\n") { "#${it.baseId}(${it.author}): ${it.value}" }
}