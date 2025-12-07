package wtf.anurag.hojo.ui

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.io.File
import wtf.anurag.hojo.data.model.StorageStatus
import wtf.anurag.hojo.ui.apps.converter.ConverterApp
import wtf.anurag.hojo.ui.apps.converter.XtcPreviewIntermediate
import wtf.anurag.hojo.ui.apps.filemanager.FileManagerApp
import wtf.anurag.hojo.ui.apps.quicklink.QuickLinkModal
import wtf.anurag.hojo.ui.apps.settings.SettingsApp
import wtf.anurag.hojo.ui.apps.tasks.TasksApp
import wtf.anurag.hojo.ui.apps.wallpaper.WallpaperEditor
import wtf.anurag.hojo.ui.components.AppDock
import wtf.anurag.hojo.ui.components.ConnectivityBottomBar
import wtf.anurag.hojo.ui.components.StatusWidget
import wtf.anurag.hojo.ui.viewmodels.ConnectivityViewModel
import wtf.anurag.hojo.ui.viewmodels.QuickLinkViewModel

@Composable
fun MainScreen() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val connectivityViewModel: ConnectivityViewModel? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        hiltViewModel()
                } else {
                        null
                }

        val isConnected by
                if (connectivityViewModel != null) {
                        connectivityViewModel.isConnected.collectAsState()
                } else {
                        remember { mutableStateOf(false) }
                }

        Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                        if (currentRoute != "home" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ) {
                                ConnectivityBottomBar(isConnected = isConnected)
                        }
                }
        ) { innerPadding ->
                NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                ) {
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
                                val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
                                val quickLinkViewModel: QuickLinkViewModel = hiltViewModel()

                                // Guard API 29 properties/calls with runtime checks and fallbacks
                                val isConnected by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                connectivityViewModel.isConnected.collectAsState()
                                        } else {
                                                remember { mutableStateOf(false) }
                                        }

                                val isConnecting by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                connectivityViewModel.isConnecting.collectAsState()
                                        } else {
                                                remember { mutableStateOf(false) }
                                        }

                                val storageStatus by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                connectivityViewModel.storageStatus.collectAsState()
                                        } else {
                                                remember { mutableStateOf<StorageStatus?>(null) }
                                        }

                                val quickLinkVisible by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                quickLinkViewModel.quickLinkVisible.collectAsState()
                                        } else {
                                                remember { mutableStateOf(false) }
                                        }

                                val quickLinkUrl by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                quickLinkViewModel.quickLinkUrl.collectAsState()
                                        } else {
                                                remember { mutableStateOf("") }
                                        }

                                val converting by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                quickLinkViewModel.converting.collectAsState()
                                        } else {
                                                remember { mutableStateOf(false) }
                                        }

                                val quickLinkError by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                quickLinkViewModel.errorMessage.collectAsState()
                                        } else {
                                                remember { mutableStateOf("") }
                                        }

                                val previewFile by
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                quickLinkViewModel.previewFile.collectAsState()
                                        } else {
                                                remember { mutableStateOf<File?>(null) }
                                        }

                                // Show preview screen if preview file is available
                                if (previewFile != null) {
                                        XtcPreviewIntermediate(
                                                file = previewFile!!,
                                                onBack = { quickLinkViewModel.closePreview() },
                                                onUpload = {
                                                        if (Build.VERSION.SDK_INT >=
                                                                        Build.VERSION_CODES.Q
                                                        ) {
                                                                quickLinkViewModel
                                                                        .uploadPreviewFile()
                                                        }
                                                },
                                                onSaveToDownloads = { /* Not implemented for quick links */
                                                },
                                                isSaved = false
                                        )
                                } else {
                                        Column(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .background(
                                                                        androidx.compose.material3
                                                                                .MaterialTheme
                                                                                .colorScheme
                                                                                .background
                                                                )
                                                                .statusBarsPadding()
                                                                .padding(16.dp)
                                        ) {
                                                StatusWidget(
                                                        isConnected = isConnected,
                                                        isConnecting = isConnecting,
                                                        storageStatus = storageStatus,
                                                        onConnect = {
                                                                if (Build.VERSION.SDK_INT >=
                                                                                Build.VERSION_CODES
                                                                                        .Q
                                                                ) {
                                                                        connectivityViewModel
                                                                                .handleConnect()
                                                                }
                                                        }
                                                )

                                                Spacer(modifier = Modifier.height(24.dp))

                                                AppDock(
                                                        onAction = { actionId ->
                                                                when (actionId) {
                                                                        "File Manager" ->
                                                                                navController
                                                                                        .navigate(
                                                                                                "file_manager"
                                                                                        )
                                                                        "Quick Link" ->
                                                                                quickLinkViewModel
                                                                                        .setQuickLinkVisible(
                                                                                                true
                                                                                        )
                                                                        "Wallpaper Editor" ->
                                                                                navController
                                                                                        .navigate(
                                                                                                "wallpaper"
                                                                                        )
                                                                        "Tasks" ->
                                                                                navController
                                                                                        .navigate(
                                                                                                "tasks"
                                                                                        )
                                                                        "Converter" ->
                                                                                navController
                                                                                        .navigate(
                                                                                                "converter"
                                                                                        )
                                                                        "Settings" ->
                                                                                navController
                                                                                        .navigate(
                                                                                                "settings"
                                                                                        )
                                                                }
                                                        }
                                                )
                                        }

                                        QuickLinkModal(
                                                visible = quickLinkVisible,
                                                url = quickLinkUrl,
                                                converting = converting,
                                                errorMessage = quickLinkError,
                                                onClose = {
                                                        quickLinkViewModel.setQuickLinkVisible(
                                                                false
                                                        )
                                                },
                                                onChangeUrl = {
                                                        quickLinkViewModel.setQuickLinkUrl(it)
                                                },
                                                onSubmit = {
                                                        if (Build.VERSION.SDK_INT >=
                                                                        Build.VERSION_CODES.Q
                                                        ) {
                                                                quickLinkViewModel.handleConvert()
                                                        }
                                                },
                                                onDismissError = { quickLinkViewModel.clearError() }
                                        )
                                }
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
                        ) { FileManagerApp(onBack = { navController.popBackStack() }) }

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

                        composable(
                                "converter",
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
                                val connectivityViewModel: ConnectivityViewModel = hiltViewModel()

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        ConverterApp(
                                                onBack = { navController.popBackStack() },
                                                connectivityViewModel = connectivityViewModel
                                        )
                                } else {
                                        Column(
                                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                                Text(
                                                        "Converter requires Android 10 (API 29) or newer."
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(onClick = { navController.popBackStack() }) {
                                                        Text("Back")
                                                }
                                        }
                                }
                        }

                        composable(
                                "settings",
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
                        ) { SettingsApp(onBack = { navController.popBackStack() }) }

                        composable(
                                "tasks",
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
                        ) { TasksApp(onBack = { navController.popBackStack() }) }
                }
        }
}
