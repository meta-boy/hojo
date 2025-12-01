package wtf.anurag.hojo.ui.apps.filemanager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.ConnectivityViewModel
import wtf.anurag.hojo.ui.viewmodels.FileManagerViewModel

@Composable
fun FileManagerApp(onBack: () -> Unit, connectivityViewModel: ConnectivityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val colors = HojoTheme.colors

    val fileManagerViewModel: FileManagerViewModel = hiltViewModel()

    val currentPath by fileManagerViewModel.currentPath.collectAsState()
    val files by fileManagerViewModel.files.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()
    val modalVisible by fileManagerViewModel.modalVisible.collectAsState()
    val modalMode by fileManagerViewModel.modalMode.collectAsState()
    val inputText by fileManagerViewModel.inputText.collectAsState()
    val errorMessage by fileManagerViewModel.errorMessage.collectAsState()
    val uploadProgress by fileManagerViewModel.uploadProgress.collectAsState()

    // Single launcher and context used for uploads
    val launcher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let {
                    // Query name from content resolver
                    var name = "upload_${System.currentTimeMillis()}"
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex =
                                    cursor.getColumnIndex(
                                            android.provider.OpenableColumns.DISPLAY_NAME
                                    )
                            if (nameIndex != -1) name = cursor.getString(nameIndex)
                        }
                    }
                    fileManagerViewModel.handleUpload(it, name)
                }
            }

    Column(modifier = Modifier.fillMaxSize().background(colors.windowBg).statusBarsPadding()) {
        FileManagerHeader(
                currentPath = currentPath,
                onBack = {
                    if (!fileManagerViewModel.handleGoBack()) {
                        onBack()
                    }
                },
                onRefresh = { fileManagerViewModel.loadFiles() }
        )

        FileManagerToolbar(
                onCreateFolder = { fileManagerViewModel.handleCreateFolder() },
                onUpload = { launcher.launch("*/*") }
        )

        UploadProgressIndicator(uploadProgress = uploadProgress)

        FileGrid(
                files = files,
                isLoading = isLoading,
                onNavigate = { fileManagerViewModel.handleNavigate(it) },
                onRename = { fileManagerViewModel.handleRename(it) },
                onDelete = { fileManagerViewModel.handleDelete(it) },
                onDownload = { /* TODO */},
                errorMessage = errorMessage
        )
    }

    InputModal(
            visible = modalVisible,
            title = if (modalMode == "create") "New Folder" else "Rename Item",
            value = inputText,
            placeholder = "Enter name...",
            submitLabel = if (modalMode == "create") "Create" else "Rename",
            onClose = { fileManagerViewModel.setModalVisible(false) },
            onChangeText = { fileManagerViewModel.setInputText(it) },
            onSubmit = { fileManagerViewModel.handleModalSubmit() }
    )
}
