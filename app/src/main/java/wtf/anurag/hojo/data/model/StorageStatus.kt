package wtf.anurag.hojo.data.model

import com.google.gson.annotations.SerializedName

data class StorageStatus(
        val totalBytes: Long,
        val usedBytes: Long,
        val version: String,
        @SerializedName("device_type") val deviceType: String,
        val type: String,
        val isOk: String,
        val id: String
)
