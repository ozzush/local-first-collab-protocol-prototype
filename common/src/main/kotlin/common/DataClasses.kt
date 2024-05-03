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
    val status: UpdateStatus
)
