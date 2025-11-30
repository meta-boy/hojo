package wtf.anurag.hojo.ui.apps.converter

import java.io.File

data class ConverterSettings(
        val fontSize: Int = 100,
        val lineHeight: Float = 1.4f,
        val margin: Int = 10,
        val enableDithering: Boolean = true,
        val ditherStrength: Int = 70,
        val fontFamily: String = ""
)

sealed class ConverterStatus {
    object Idle : ConverterStatus()
    object ReadingFile : ConverterStatus()
    data class Converting(val progress: Int, val total: Int) : ConverterStatus()
    data class Success(val outputFile: File, val isSaved: Boolean = false) : ConverterStatus()
    object Uploading : ConverterStatus()
    object UploadSuccess : ConverterStatus()
    data class Error(val message: String) : ConverterStatus()
}
