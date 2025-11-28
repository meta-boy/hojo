package wtf.anurag.hojo.data

import android.util.Log
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import wtf.anurag.hojo.data.model.FileItem
import wtf.anurag.hojo.data.model.StorageStatus

class FileManagerRepository(private val client: OkHttpClient = OkHttpClient()) {
    private val gson = Gson()
    private val TAG = "FileManagerRepo"

    suspend fun fetchList(baseUrl: String, path: String): List<FileItem> =
            withContext(Dispatchers.IO) {
                val encodedPath = URLEncoder.encode(path, "UTF-8")
                val url = "$baseUrl/list?dir=$encodedPath"
                Log.d(TAG, "fetchList -> GET $url")

                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "fetchList -> response code: ${response.code}")
                    if (!response.isSuccessful) throw IOException("List failed: ${response.code}")
                    val json = response.body?.string() ?: "[]"
                    val type = object : TypeToken<List<FileItem>>() {}.type
                    val list: List<FileItem> = gson.fromJson(json, type)
                    list.sortedWith(compareBy({ it.type != "dir" }, { it.name }))
                }
            }

    suspend fun fetchStatus(baseUrl: String): StorageStatus =
            withContext(Dispatchers.IO) {
                val url = "$baseUrl/status"
                Log.d(TAG, "fetchStatus -> GET $url")

                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "fetchStatus -> response code: ${response.code}")
                    if (!response.isSuccessful) throw IOException("Status failed: ${response.code}")
                    val json = response.body?.string()
                    gson.fromJson(json, StorageStatus::class.java)
                }
            }

    suspend fun createFolder(baseUrl: String, path: String) =
            withContext(Dispatchers.IO) {
                val body =
                        MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("path", path)
                                .build()

                val request = Request.Builder().url("$baseUrl/edit").put(body).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Create failed: ${response.code}")
                }
            }

    suspend fun deleteItem(baseUrl: String, path: String) =
            withContext(Dispatchers.IO) {
                val body =
                        MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("path", path)
                                .build()

                val request = Request.Builder().url("$baseUrl/edit").delete(body).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Delete failed: ${response.code}")
                }
            }

    suspend fun renameItem(baseUrl: String, from: String, to: String) =
            withContext(Dispatchers.IO) {
                val body =
                        MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("path", to)
                                .addFormDataPart("src", from)
                                .build()

                val request = Request.Builder().url("$baseUrl/edit").put(body).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Rename failed: ${response.code}")
                }
            }

    suspend fun uploadFile(baseUrl: String, file: File, targetPath: String) =
            withContext(Dispatchers.IO) {
                val extension = file.extension
                val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                ?: "application/octet-stream"

                val body =
                        MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart(
                                        "data",
                                        targetPath,
                                        file.asRequestBody(mimeType.toMediaType())
                                )
                                .build()

                val request = Request.Builder().url("$baseUrl/edit").post(body).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Upload failed: ${response.code}")
                }
            }
}
