package wtf.anurag.hojo.utils

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow

object FormatUtils {
    fun formatBytes(bytes: Long, decimals: Int = 2): String {
        if (bytes == 0L) return "0 Bytes"

        val k = 1024.0
        val dm = if (decimals < 0) 0 else decimals
        val sizes = arrayOf("Bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")

        val i = floor(log(bytes.toDouble(), k)).toInt()

        return "%.${dm}f %s".format(Locale.US, bytes / k.pow(i), sizes[i])
    }
}
