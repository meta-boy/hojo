package wtf.anurag.hojo.ui.apps.renderer

import android.graphics.Bitmap
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtf.anurag.hojo.ui.apps.converter.ConverterSettings
import wtf.anurag.hojo.ui.apps.converter.XtcRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendererApp(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedFile by remember { mutableStateOf<java.io.File?>(null) }
    var fileInfo by remember { mutableStateOf<wtf.anurag.hojo.ui.apps.converter.XtcDecoder.XtcFileInfo?>(null) }
    var isRendering by remember { mutableStateOf(false) }
    val renderedPages = remember { mutableStateListOf<Bitmap>() }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    isRendering = true
                    renderedPages.clear()
                    
                    // Copy URI to temp file
                    val inputStream = context.contentResolver.openInputStream(it)
                    val tempFile = java.io.File(context.cacheDir, "temp_renderer.xtc")
                    val outputStream = java.io.FileOutputStream(tempFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    
                    selectedFile = tempFile
                    
                    // Read Info
                    val info = wtf.anurag.hojo.ui.apps.converter.XtcDecoder.readXtcInfo(tempFile)
                    fileInfo = info
                    
                    // Render all pages (lazy loading might be better for large files, but for now render all for simplicity as per plan)
                    // Actually, let's just load them one by one to show progress
                    val pages = mutableListOf<Bitmap>()
                    for (i in 0 until info.pageCount) {
                        val bitmap = wtf.anurag.hojo.ui.apps.converter.XtcDecoder.extractPage(tempFile, i)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                renderedPages.add(bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isRendering = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("XTC Renderer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            if (selectedFile == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Select an .xtc file to view",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { launcher.launch(arrayOf("*/*")) }, // Allow selecting any file, filtering can be tricky with MIME types
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Select File")
                        }
                    }
                }
            } else {
                // File Info
                if (fileInfo != null) {
                     Text(
                        text = "Title: ${fileInfo?.title ?: "Unknown"}",
                        style = MaterialTheme.typography.titleMedium
                    )
                     Text(
                        text = "Pages: ${renderedPages.size} / ${fileInfo?.pageCount}",
                        style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isRendering && renderedPages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(renderedPages) { bitmap ->
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .background(androidx.compose.ui.graphics.Color.Black) // Use black background for contrast or white?
                                    // XTC bitmaps are usually tailored for e-paper (white bg).
                                    // Let's us white container.
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page Preview",
                                    modifier = Modifier.fillMaxWidth(),
                                    Alignment.Center
                                )
                            }
                        }
                        
                         if (isRendering) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        selectedFile = null
                        renderedPages.clear()
                        fileInfo = null
                    },
                     colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Close File")
                }
            }
        }
    }
}
