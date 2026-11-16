package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.HomeScreen
import com.example.ui.HomeTab
import com.example.ui.KeysScreen
import com.example.ui.ResultScreen
import com.example.ui.SplashScreen
import com.example.ui.VroumViewModel
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.IgnitionAmber
import com.example.ui.theme.VoidObsidian
import com.example.ui.theme.VroumTheme

object VroumRoutes {
    const val SPLASH = "splash"
    const val KEYS = "keys"
    const val HOME = "home"
    const val RESULT = "result"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VroumTheme {
                VroumApp()
            }
        }
    }
}

@Composable
fun VroumApp(
    viewModel: VroumViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var hasMicPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showMicRationaleDialog by rememberSaveable { mutableStateOf(false) }
    var hasPromptedOnHomeEntry by rememberSaveable { mutableStateOf(false) }
    var startListenOnGrant by rememberSaveable { mutableStateOf(false) }

    // Refresh microphone permission when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasMicPermission = grantedNow
                if (grantedNow) {
                    viewModel.clearMicPermissionError()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.clearMicPermissionError()
            if (startListenOnGrant) {
                startListenOnGrant = false
                viewModel.startListeningAndDiagnose { diagnosticId ->
                    navController.navigate("${VroumRoutes.RESULT}/$diagnosticId")
                }
            }
        } else {
            startListenOnGrant = false
            viewModel.onMicPermissionDenied()
        }
    }

    if (showMicRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showMicRationaleDialog = false
                startListenOnGrant = false
            },
            containerColor = CarbonSurface,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Autorisation microphone",
                    tint = IgnitionAmber,
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    text = "Vroum a besoin de ton micro",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Salut ! Pour écouter ton moteur pendant 30 secondes et repérer les bruits inhabituels (cliquetis, ratés, sifflements), Vroum doit accéder au microphone de ton téléphone.\n\nL'audio sert uniquement à ton diagnostic et n'est jamais conservé sans ton accord.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicRationaleDialog = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("confirm_mic_permission_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IgnitionAmber,
                        contentColor = VoidObsidian
                    )
                ) {
                    Text("Autoriser le micro")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMicRationaleDialog = false
                        startListenOnGrant = false
                    },
                    modifier = Modifier.testTag("dismiss_mic_permission_button")
                ) {
                    Text("Plus tard", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = VroumRoutes.SPLASH,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {
        composable(VroumRoutes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val target = if (viewModel.hasRequiredKeys()) {
                        VroumRoutes.HOME
                    } else {
                        "${VroumRoutes.KEYS}?fromSettings=false"
                    }
                    navController.navigate(target) {
                        popUpTo(VroumRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${VroumRoutes.KEYS}?fromSettings={fromSettings}",
            arguments = listOf(
                navArgument("fromSettings") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            KeysScreen(
                initialKeys = uiState.apiKeys,
                showBackButton = fromSettings && uiState.apiKeys.hasRequiredKeys,
                onSaveKeys = { keys ->
                    viewModel.saveApiKeys(keys)
                    if (fromSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(VroumRoutes.HOME) {
                            popUpTo("${VroumRoutes.KEYS}?fromSettings={fromSettings}") {
                                inclusive = true
                            }
                        }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(VroumRoutes.HOME) {
            LaunchedEffect(hasMicPermission) {
                if (!hasMicPermission && !hasPromptedOnHomeEntry) {
                    hasPromptedOnHomeEntry = true
                    startListenOnGrant = false
                    showMicRationaleDialog = true
                }
            }

            HomeScreen(
                uiState = uiState,
                history = history,
                onSelectVehicleType = viewModel::selectVehicleType,
                onUpdateVehicleFields = viewModel::updateVehicleFields,
                onToggleUnknownBrandModel = viewModel::setUnknownBrandModel,
                onSelectTab = viewModel::selectHomeTab,
                onStartListening = {
                    if (hasMicPermission) {
                        viewModel.startListeningAndDiagnose { diagnosticId ->
                            navController.navigate("${VroumRoutes.RESULT}/$diagnosticId")
                        }
                    } else {
                        startListenOnGrant = true
                        showMicRationaleDialog = true
                    }
                },
                onRequestMicPermission = {
                    viewModel.clearMicPermissionError()
                    startListenOnGrant = false
                    showMicRationaleDialog = true
                },
                onCancelSession = viewModel::cancelSession,
                onDismissError = viewModel::dismissError,
                onClearMicPermissionError = viewModel::clearMicPermissionError,
                onOpenKeysSettings = {
                    navController.navigate("${VroumRoutes.KEYS}?fromSettings=true")
                },
                onOpenHistoryItem = { item ->
                    viewModel.openHistoryItem(item)
                    navController.navigate("${VroumRoutes.RESULT}/${item.id}")
                },
                onDeleteHistoryItem = viewModel::deleteDiagnostic
            )
        }

        composable(
            route = "${VroumRoutes.RESULT}/{diagnosticId}",
            arguments = listOf(
                navArgument("diagnosticId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val diagnosticId = backStackEntry.arguments?.getLong("diagnosticId") ?: 0L
            LaunchedEffect(diagnosticId) {
                viewModel.loadDiagnosticById(diagnosticId)
            }
            ResultScreen(
                result = uiState.activeResult,
                onBack = { navController.popBackStack() },
                onNewAnalysis = {
                    viewModel.selectHomeTab(HomeTab.LISTEN)
                    navController.popBackStack(VroumRoutes.HOME, inclusive = false)
                }
            )
        }
    }
}
