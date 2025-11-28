package wtf.anurag.hojo.data.model

data class FileItem(
        val name: String,
        val type: String, // "dir" or "file"
        val size: Long? = null
)
