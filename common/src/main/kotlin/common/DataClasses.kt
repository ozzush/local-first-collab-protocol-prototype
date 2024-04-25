package common

import kotlinx.serialization.Serializable

@Serializable
data class UpdateDescriptor(
    val author: String,
    val initialId: Long,
    val assignedId: Long?
)

@Serializable
data class ServerResponse(
    val author: String,
    val baseTxnId: Long,
    val initialTxnId: Long,
    val newTxnId: Long
)

@Serializable
data class ClientUpdate(
    val author: String,
    val baseTxnId: Long,
    val newTxnId: Long,
    val basedOnLocal: Boolean
)
