package common

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseMock(
    private val history: MutableList<UpdateDescriptor> = mutableListOf()
) {
    fun apply(updateDescriptor: UpdateDescriptor) {
        history.add(updateDescriptor)
    }

    fun lastUpdate() = history.last()

    fun data() = history.toList()
}