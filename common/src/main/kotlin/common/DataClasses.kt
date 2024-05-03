package common

import kotlinx.serialization.Serializable

@Serializable
enum class UpdateStatus {
    COMMIT, REJECT, LOCAL
}

@Serializable
data class UpdateDescriptor(
    val author: String,
    val baseId: String,
    val id: String,
    val status: UpdateStatus,
    val value: String
)

@Serializable
data class SynchronizeResponse(
    val updates: List<UpdateDescriptor>,
    val updatesCommitted: Boolean
)

@Serializable
data class SynchronizeRequest(
    val updates: List<UpdateDescriptor>,
    val lastConfirmedId: String
)