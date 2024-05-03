package common

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseMock(
    private val history: MutableList<UpdateDescriptor> = mutableListOf()
) {
    fun apply(updateDescriptor: UpdateDescriptor) {
        history.add(updateDescriptor)
//        println("""Applied update ${updateDescriptor.id}
//            |--------------------------
//            |${toPrettyString()}
//            |--------------------------
//        """.trimMargin())
    }

    fun applyAll(updates: Iterable<UpdateDescriptor>) {
        history.addAll(updates)
//        println("""Applied update ${updateDescriptor.id}
//            |--------------------------
//            |${toPrettyString()}
//            |--------------------------
//        """.trimMargin())
    }

    fun lastUpdate() = history.last()

    fun data() = history.toList()

    fun deepCopy(history: MutableList<UpdateDescriptor> = this.history.map { it }.toMutableList()) =
        DatabaseMock(history)

    fun toPrettyString() =
        history.joinToString(separator = "\n") { "#${it.baseId} -> #${it.id} (${it.author}): ${it.value}" }
}