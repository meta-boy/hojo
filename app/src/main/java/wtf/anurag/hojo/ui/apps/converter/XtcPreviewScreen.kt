package wtf.anurag.hojo.ui.apps.converter

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import wtf.anurag.hojo.ui.theme.HojoTheme
import java.io.File
import java.util.Locale

@Suppress("DEPRECATION")
@Composable
fun XtcPreviewScreen(
    file: File,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onSaveToDownloads: () -> Unit,
    isSaved: Boolean = false
) {
    var fileInfo by remember { mutableStateOf<XtcDecoder.XtcFileInfo?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var selectedPageIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val info = XtcDecoder.readXtcInfo(file)
                fileInfo = info
                pageCount = info.pageCount
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HojoTheme.colors.windowBg)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HojoTheme.colors.text
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = fileInfo?.title ?: "Preview",
                    color = HojoTheme.colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (fileInfo?.author?.isNotEmpty() == true) {
                    Text(
                        text = "by ${fileInfo?.author}",
                        color = HojoTheme.colors.subText,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HojoTheme.colors.primary)
            }
        } else if (fileInfo == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load file",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }
        } else {
            // File info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HojoTheme.colors.headerBg
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("Pages", "$pageCount")
                    InfoRow("File Size", formatFileSize(fileInfo?.totalSize ?: 0))
                    InfoRow("Format", "XTC (E-Paper)")
                }
            }

            // Page thumbnails
            Text(
                text = "Pages Preview",
                color = HojoTheme.colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val gridState = rememberLazyGridState()

            // Limit concurrent thumbnail decoding to avoid resource exhaustion
            val thumbnailSemaphore = remember { Semaphore(2) }

            // Simple in-memory LRU cache for thumbnails (by page index)
            val thumbnailCache = remember {
                // use a fraction of the available heap (in KB)
                val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
                val cacheSizeKb = maxMemoryKb / 8
                object : LruCache<Int, Bitmap>(cacheSizeKb) {
                    override fun sizeOf(key: Int, value: Bitmap): Int {
                        return try {
                            // size in KB
                            value.allocationByteCount / 1024
                        } catch (_: Throwable) {
                            1
                        }
                    }
                }
            }
            val thumbnailKeys = remember { mutableStateListOf<Int>() }

            // Recycle cached bitmaps when file/screen disposes
            DisposableEffect(file) {
                onDispose {
                    for (k in thumbnailKeys) {
                        safeCacheRemove(thumbnailCache, k)?.let { bmp ->
                            try {
                                bmp.recycle()
                            } catch (_: Exception) {
                            }
                        }
                     }
                     thumbnailKeys.clear()
                 }
             }

             // Only load thumbnails when they are visible to avoid wasted work during fast scrolls
             val visibleIndices by remember(gridState) {
                 derivedStateOf {
                     gridState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
                 }
             }

             LazyVerticalGrid(
                 columns = GridCells.Fixed(3),
                 state = gridState,
                 modifier = Modifier
                     .weight(1f)
                     .fillMaxWidth()
                     .padding(horizontal = 8.dp),
                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                 verticalArrangement = Arrangement.spacedBy(12.dp)
             ) {
                 itemsIndexed(List(pageCount) { it }) { index, pageIndex ->
                    PageThumbnail(
                        file = file,
                        pageIndex = pageIndex,
                        onClick = { selectedPageIndex = pageIndex },
                        semaphore = thumbnailSemaphore,
                        cache = thumbnailCache,
                        cacheKeys = thumbnailKeys,
                        visibleIndices = visibleIndices,
                        modifier = Modifier
                            .aspectRatio(3f / 4f)
                    )
                 }

                 item {
                     Spacer(modifier = Modifier.height(80.dp))
                 }
             }

            // Prefetch neighbor pages for currently visible items to reduce blank tiles during fast scrolling
            LaunchedEffect(visibleIndices, file) {
                // for each visible index, prefetch neighbors (index-1, index+1)
                for (v in visibleIndices) {
                    listOf(v - 1, v + 1).forEach { neighbor ->
                        if (neighbor < 0 || neighbor >= pageCount) return@forEach
                        // skip if already cached
                        if (safeCacheGet(thumbnailCache, neighbor) != null) return@forEach

                        // perform a sequential prefetch respecting the semaphore
                        try {
                            withContext(Dispatchers.IO) {
                                thumbnailSemaphore.withPermit {
                                    try {
                                        val rawBmp = XtcDecoder.extractPage(file, neighbor)
                                        if (rawBmp != null) {
                                            val bmp = scaledThumbnail(rawBmp)
                                            if (bmp !== rawBmp) {
                                                try { rawBmp.recycle() } catch (_: Exception) {}
                                            }
                                            safeCachePut(thumbnailCache, neighbor, bmp)
                                            if (!thumbnailKeys.contains(neighbor)) thumbnailKeys.add(neighbor)
                                        }
                                    } catch (_: Throwable) {
                                        // ignore per-page prefetch failures
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

             // Action buttons
             Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HojoTheme.colors.headerBg)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveToDownloads,
                    enabled = !isSaved,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = HojoTheme.colors.text
                    )
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSaved) "Saved" else "Save")
                }

                Button(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HojoTheme.colors.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload")
                }
            }
        }
    }

    // Full page preview dialog
    if (selectedPageIndex != null) {
        FullPagePreviewDialog(
            file = file,
            pageIndex = selectedPageIndex!!,
            pageCount = pageCount,
            onDismiss = { selectedPageIndex = null },
            onNavigate = { newIndex -> selectedPageIndex = newIndex }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = HojoTheme.colors.subText,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = HojoTheme.colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PageThumbnail(
    file: File,
    pageIndex: Int,
    onClick: () -> Unit,
    semaphore: Semaphore,
    cache: LruCache<Int, Bitmap>,
    cacheKeys: MutableList<Int>,
    visibleIndices: Set<Int>,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var loadKey by remember { mutableStateOf(0) } // increment to retry

    // If thumbnail already cached (prefetch or previous load), show it immediately without starting a load
    LaunchedEffect(cache, pageIndex) {
        safeCacheGet(cache, pageIndex)?.let { cached ->
            bitmap = cached
            isLoading = false
        }
    }

    // If this item previously failed, automatically retry when it becomes visible again
    LaunchedEffect(visibleIndices, failed) {
        if (visibleIndices.contains(pageIndex) && failed) {
            loadKey++
        }
    }

    // Start loading when the item becomes visible. Wait until visible by checking visibleIndices; this effect re-runs when visibleIndices or loadKey changes.
    LaunchedEffect(file, pageIndex, loadKey, visibleIndices) {
        if (!visibleIndices.contains(pageIndex)) {
            // not visible yet — do not start decoding
            return@LaunchedEffect
        }

        isLoading = true
        failed = false

        // quick cache check
        safeCacheGet(cache, pageIndex)?.let { cached ->
            bitmap = cached
            isLoading = false
            return@LaunchedEffect
        }

        try {
            withContext(Dispatchers.IO) {
                var attempts = 0
                val maxAttempts = 3
                while (attempts < maxAttempts) {
                    try {
                        semaphore.withPermit {
                            val rawBmp = XtcDecoder.extractPage(file, pageIndex)
                            if (rawBmp != null) {
                                // scale to thumbnail size to reduce memory and speed up rendering
                                val bmp = scaledThumbnail(rawBmp)
                                // if scaling produced a new bitmap, recycle the raw one
                                if (bmp !== rawBmp) {
                                    try {
                                        rawBmp.recycle()
                                    } catch (_: Exception) {}
                                }

                                bitmap = bmp
                                // store in cache only when non-null — defensive try/catch to avoid NPE from underlying cache
                                try {
                                    safeCachePut(cache, pageIndex, bmp)
                                    if (!cacheKeys.contains(pageIndex)) cacheKeys.add(pageIndex)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                // decoded to null — treat as a failed attempt so retry/backoff applies
                                throw Exception("Decoded bitmap was null for page $pageIndex")
                            }
                        }

                        failed = false
                        break
                    } catch (e: OutOfMemoryError) {
                        // Clear cache and GC, then retry
                        try {
                            cache.evictAll()
                        } catch (_: Exception) {}
                        System.gc()
                        attempts++
                        if (attempts < maxAttempts) {
                            delay(300L * attempts)
                        }
                    } catch (e: CancellationException) {
                        // If the coroutine is cancelled (e.g., user scrolled away), stop loading without marking a failure
                        return@withContext
                    } catch (_: Exception) {
                        attempts++
                        if (attempts < maxAttempts) {
                            delay(250L * attempts)
                        }
                    }
                }
            }
        } finally {
            // Ensure spinner is cleared regardless of cancellation
            if (bitmap == null) {
                // only mark failed if not cancelled
                failed = bitmap == null
            }
            isLoading = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HojoTheme.colors.headerBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        color = HojoTheme.colors.primary,
                        strokeWidth = 3.dp
                    )
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                failed -> {
                    // show retry affordance
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Failed to load", color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { loadKey++ }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Failed to load",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Page number badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = HojoTheme.colors.primary
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FullPagePreviewDialog(
    file: File,
    pageIndex: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onNavigate: (Int) -> Unit
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(file, pageIndex) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                bitmap = XtcDecoder.extractPage(file, pageIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HojoTheme.colors.headerBg)
                .padding(16.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${pageIndex + 1} of $pageCount",
                    color = HojoTheme.colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = HojoTheme.colors.text
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, HojoTheme.colors.border, RoundedCornerShape(8.dp))
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = HojoTheme.colors.primary
                        )
                    }
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        Text(
                            text = "Failed to load page",
                            color = Color.Red,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { onNavigate(pageIndex - 1) },
                    enabled = pageIndex > 0
                ) {
                    Text("← Previous")
                }

                TextButton(
                    onClick = { onNavigate(pageIndex + 1) },
                    enabled = pageIndex < pageCount - 1
                ) {
                    Text("Next →")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun safeCachePut(cache: LruCache<Int, Bitmap>, key: Int?, value: Bitmap?) {
    if (key == null || value == null) return
    try {
        cache.put(key, value)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun safeCacheGet(cache: LruCache<Int, Bitmap>, key: Int?): Bitmap? {
    if (key == null) return null
    return try {
        cache.get(key)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun safeCacheRemove(cache: LruCache<Int, Bitmap>, key: Int?): Bitmap? {
    if (key == null) return null
    return try {
        cache.remove(key)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Scale a bitmap down if it's larger than max dimensions to reduce memory footprint for thumbnails.
private fun scaledThumbnail(bmp: Bitmap): Bitmap {
    val maxDim = 600 // reasonable thumbnail max dimension (px)
    val w = bmp.width
    val h = bmp.height
    if (w <= maxDim && h <= maxDim) return bmp

    val scale = if (w >= h) maxDim.toFloat() / w.toFloat() else maxDim.toFloat() / h.toFloat()
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    return try {
        Bitmap.createScaledBitmap(bmp, newW, newH, true)
    } catch (e: Throwable) {
        e.printStackTrace()
        bmp
    }
}
