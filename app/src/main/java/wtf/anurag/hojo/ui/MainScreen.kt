package wtf.anurag.hojo.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import wtf.anurag.hojo.ui.apps.wallpaper.WallpaperEditor
import wtf.anurag.hojo.ui.components.AppDock
import wtf.anurag.hojo.ui.components.FileGrid
import wtf.anurag.hojo.ui.components.FileManagerHeader
import wtf.anurag.hojo.ui.components.FileManagerToolbar
import wtf.anurag.hojo.ui.components.InputModal
import wtf.anurag.hojo.ui.components.QuickLinkModal
import wtf.anurag.hojo.ui.components.StatusWidget
import wtf.anurag.hojo.ui.components.UploadProgressIndicator
import wtf.anurag.hojo.ui.theme.HojoTheme
import wtf.anurag.hojo.ui.viewmodels.ConnectivityViewModel
import wtf.anurag.hojo.ui.viewmodels.FileManagerViewModel
import wtf.anurag.hojo.ui.viewmodels.QuickLinkViewModel

@Composable
fun MainScreen() {
        val navController = rememberNavController()
        val colors = HojoTheme.colors

        NavHost(navController = navController, startDestination = "home") {
                composable(
                        "home",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { -it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = viewModel()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val quickLinkViewModel: QuickLinkViewModel =
                                viewModel(
                                        factory =
                                                object :
                                                        androidx.lifecycle.ViewModelProvider.Factory {
                                                        override fun <
                                                                T : androidx.lifecycle.ViewModel> create(
                                                                modelClass: Class<T>
                                                        ): T {
                                                                @Suppress("UNCHECKED_CAST")
                                                                return QuickLinkViewModel(
                                                                        context.applicationContext as
                                                                                android.app.Application,
                                                                        connectivityViewModel
                                                                ) as
                                                                        T
                                                        }
                                                }
                                )

                        val isConnected by connectivityViewModel.isConnected.collectAsState()
                        val isConnecting by connectivityViewModel.isConnecting.collectAsState()
                        val storageStatus by connectivityViewModel.storageStatus.collectAsState()

                        val quickLinkVisible by quickLinkViewModel.quickLinkVisible.collectAsState()
                        val quickLinkUrl by quickLinkViewModel.quickLinkUrl.collectAsState()
                        val converting by quickLinkViewModel.converting.collectAsState()
                        val quickLinkError by quickLinkViewModel.errorMessage.collectAsState()

                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(colors.windowBg)
                                                .statusBarsPadding()
                                                .padding(16.dp)
                        ) {
                                StatusWidget(
                                        isConnected = isConnected,
                                        isConnecting = isConnecting,
                                        storageStatus = storageStatus,
                                        onConnect = { connectivityViewModel.handleConnect() }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                AppDock(
                                        onAction = { actionId ->
                                                when (actionId) {
                                                        "File Manager" ->
                                                                navController.navigate(
                                                                        "file_manager"
                                                                )
                                                        "Quick Link" ->
                                                                quickLinkViewModel
                                                                        .setQuickLinkVisible(true)
                                                        "Wallpaper Editor" ->
                                                                navController.navigate("wallpaper")
                                                }
                                        }
                                )
                        }

                        QuickLinkModal(
                                visible = quickLinkVisible,
                                url = quickLinkUrl,
                                converting = converting,
                                errorMessage = quickLinkError,
                                onClose = { quickLinkViewModel.setQuickLinkVisible(false) },
                                onChangeUrl = { quickLinkViewModel.setQuickLinkUrl(it) },
                                onSubmit = { quickLinkViewModel.handleConvertAndUpload() },
                                onDismissError = { quickLinkViewModel.clearError() }
                        )
                }

                composable(
                        "file_manager",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) {
                        val connectivityViewModel: ConnectivityViewModel = viewModel()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val fileManagerViewModel: FileManagerViewModel =
                                viewModel(
                                        factory =
                                                object :
                                                        androidx.lifecycle.ViewModelProvider.Factory {
                                                        override fun <
                                                                T : androidx.lifecycle.ViewModel> create(
                                                                modelClass: Class<T>
                                                        ): T {
                                                                @Suppress("UNCHECKED_CAST")
                                                                return FileManagerViewModel(
                                                                        context.applicationContext as
                                                                                android.app.Application,
                                                                        connectivityViewModel
                                                                ) as
                                                                        T
                                                        }
                                                }
                                )

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
                                androidx.activity.compose.rememberLauncherForActivityResult(
                                        contract =
                                                androidx.activity.result.contract
                                                        .ActivityResultContracts.GetContent()
                                ) { uri ->
                                        uri?.let {
                                                // Query name from content resolver
                                                var name = "upload_${System.currentTimeMillis()}"
                                                context.contentResolver.query(
                                                                it,
                                                                null,
                                                                null,
                                                                null,
                                                                null
                                                        )
                                                        ?.use { cursor ->
                                                                if (cursor.moveToFirst()) {
                                                                        val nameIndex =
                                                                                cursor.getColumnIndex(
                                                                                        android.provider
                                                                                                .OpenableColumns
                                                                                                .DISPLAY_NAME
                                                                                )
                                                                        if (nameIndex != -1)
                                                                                name =
                                                                                        cursor.getString(
                                                                                                nameIndex
                                                                                        )
                                                                }
                                                        }
                                                fileManagerViewModel.handleUpload(it, name)
                                        }
                                }

                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(colors.windowBg)
                                                .statusBarsPadding()
                        ) {
                                FileManagerHeader(
                                        currentPath = currentPath,
                                        onBack = {
                                                if (!fileManagerViewModel.handleGoBack()) {
                                                        navController.popBackStack()
                                                }
                                        },
                                        onRefresh = { fileManagerViewModel.loadFiles() }
                                )

                                FileManagerToolbar(
                                        onCreateFolder = {
                                                fileManagerViewModel.handleCreateFolder()
                                        },
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

                composable(
                        "wallpaper",
                        enterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { it }
                                )
                        },
                        exitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        },
                        popEnterTransition = {
                                slideInHorizontally(
                                        animationSpec = tween(300),
                                        initialOffsetX = { -it }
                                )
                        },
                        popExitTransition = {
                                slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { it }
                                )
                        }
                ) { WallpaperEditor(onBack = { navController.popBackStack() }) }
        }
}
