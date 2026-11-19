package com.vroum.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vroum.app.R
import com.vroum.app.audio.EngineAudioRecorder
import com.vroum.app.model.ConfidenceLevel
import com.vroum.app.model.DiagnosticResult
import com.vroum.app.model.VehicleCandidateSuggestion
import com.vroum.app.model.VehicleType
import com.vroum.app.network.PipelineStep
import com.vroum.app.photo.VehiclePhotoHelper
import com.vroum.app.ui.theme.AcousticCyan
import com.vroum.app.ui.theme.AlertRed
import com.vroum.app.ui.theme.CarbonSurface
import com.vroum.app.ui.theme.CautionAmber
import com.vroum.app.ui.theme.ElevatedPanel
import com.vroum.app.ui.theme.HairlineBorder
import com.vroum.app.ui.theme.HealthyEmerald
import com.vroum.app.ui.theme.IgnitionAmber
import com.vroum.app.ui.theme.JetBrainsMonoFontFamily
import com.vroum.app.ui.theme.VoidObsidian
import com.vroum.app.ui.theme.WarmSpark
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    uiState: VroumUiState,
    history: List<DiagnosticResult>,
    onSelectVehicleType: (VehicleType) -> Unit,
    onUpdateVehicleFields: (
        brand: String?,
        model: String?,
        year: String?,
        displacementCc: String?,
        mileage: String?
    ) -> Unit,
    onToggleUnknownBrandModel: (Boolean) -> Unit,
    onAnalyzeVehiclePhoto: (Uri) -> Unit,
    onCancelPhotoIdentification: () -> Unit,
    onSelectSuggestion: (VehicleCandidateSuggestion) -> Unit,
    onRejectIdentifiedVehicle: () -> Unit,
    onNoPhotoSelected: () -> Unit,
    onResetPhotoStep: () -> Unit,
    onDismissPhotoError: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onStartListening: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onCancelSession: () -> Unit,
    onDismissError: () -> Unit,
    onClearMicPermissionError: () -> Unit,
    onOpenKeysSettings: () -> Unit,
    onOpenHistoryItem: (DiagnosticResult) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit
) {
    val context = LocalContext.current
    var showVehicleRequiredHint by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showCameraDeniedDialog by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraUriString
        pendingCameraUriString = null
        if (success && !uriStr.isNullOrBlank()) {
            showVehicleRequiredHint = false
            onAnalyzeVehiclePhoto(Uri.parse(uriStr))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = VehiclePhotoHelper.createTempCameraUri(context)
            pendingCameraUriString = uri.toString()
            runCatching { cameraLauncher.launch(uri) }
                .onFailure { pendingCameraUriString = null }
        } else {
            showCameraDeniedDialog = true
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            showVehicleRequiredHint = false
            onAnalyzeVehiclePhoto(uri)
        }
    }

    fun launchCameraCapture() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            val uri = VehiclePhotoHelper.createTempCameraUri(context)
            pendingCameraUriString = uri.toString()
            runCatching { cameraLauncher.launch(uri) }
                .onFailure { pendingCameraUriString = null }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGalleryPicker() {
        runCatching {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    if (showCameraDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDeniedDialog = false },
            containerColor = CarbonSurface,
            title = {
                Text(
                    text = "Autorisation appareil photo requise",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Vroum a besoin de l'appareil photo pour photographier ta moto. Tu peux l'autoriser dans les paramètres Android ou choisir une photo depuis la galerie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCameraDeniedDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IgnitionAmber,
                        contentColor = VoidObsidian
                    )
                ) {
                    Text("Paramètres")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraDeniedDialog = false }) {
                    Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    fun handleListenTap() {
        if (!uiState.vehicleInfo.canProceedToListen) {
            showVehicleRequiredHint = true
            return
        }
        showVehicleRequiredHint = false
        onStartListening()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            VroumTopBar(
                activeTab = uiState.activeTab,
                historyCount = history.size,
                isSessionBusy = uiState.sessionState is EngineSessionState.PreparingSearch ||
                    uiState.sessionState is EngineSessionState.Recording ||
                    uiState.sessionState is EngineSessionState.Analyzing,
                onSelectTab = onSelectTab,
                onOpenKeysSettings = onOpenKeysSettings
            )

            AnimatedContent(
                targetState = Pair(uiState.activeTab, uiState.sessionState),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "vroum_home_content",
                modifier = Modifier.weight(1f)
            ) { (tab, session) ->
                when {
                    session is EngineSessionState.PreparingSearch -> {
                        PreparingSearchSessionContent(
                            state = session,
                            onCancel = onCancelSession
                        )
                    }
                    session is EngineSessionState.Recording -> {
                        RecordingSessionContent(
                            state = session,
                            onCancel = onCancelSession
                        )
                    }
                    session is EngineSessionState.Analyzing -> {
                        AnalyzingSessionContent(
                            state = session,
                            includeDeepSeek = uiState.apiKeys.deepSeekKey.isNotBlank() &&
                                uiState.vehicleInfo.hasBrandAndModel,
                            onCancel = onCancelSession
                        )
                    }
                    tab == HomeTab.HISTORY -> {
                        HistoryListContent(
                            history = history,
                            onOpenItem = onOpenHistoryItem,
                            onDeleteItem = onDeleteHistoryItem,
                            onStartNewClick = { onSelectTab(HomeTab.LISTEN) }
                        )
                    }
                    else -> {
                        ListenHomeContent(
                            uiState = uiState,
                            showVehicleRequiredHint = showVehicleRequiredHint,
                            onSelectVehicleType = onSelectVehicleType,
                            onUpdateVehicleFields = { brand, model, year, cc, mileage ->
                                if (brand != null || model != null) {
                                    showVehicleRequiredHint = false
                                }
                                onUpdateVehicleFields(brand, model, year, cc, mileage)
                            },
                            onToggleUnknownBrandModel = { unknown ->
                                if (unknown) showVehicleRequiredHint = false
                                onToggleUnknownBrandModel(unknown)
                            },
                            onSkipAndListen = {
                                showVehicleRequiredHint = false
                                onToggleUnknownBrandModel(true)
                                onStartListening()
                            },
                            onTakePhotoClick = ::launchCameraCapture,
                            onPickFromGalleryClick = ::launchGalleryPicker,
                            onNoPhotoClick = {
                                showVehicleRequiredHint = false
                                onNoPhotoSelected()
                            },
                            onCancelPhotoIdentification = onCancelPhotoIdentification,
                            onConfirmYesClick = ::handleListenTap,
                            onRejectNoClick = onRejectIdentifiedVehicle,
                            onSelectSuggestion = { suggestion ->
                                showVehicleRequiredHint = false
                                onSelectSuggestion(suggestion)
                            },
                            onResetPhotoStep = {
                                showVehicleRequiredHint = false
                                onResetPhotoStep()
                            },
                            onDismissPhotoError = onDismissPhotoError,
                            onListenClick = ::handleListenTap,
                            onDismissError = onDismissError,
                            onOpenKeysSettings = onOpenKeysSettings,
                            onOpenAppSettings = {
                                onClearMicPermissionError()
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                runCatching { context.startActivity(intent) }
                            },
                            onRetryMic = onRequestMicPermission
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VroumTopBar(
    activeTab: HomeTab,
    historyCount: Int,
    isSessionBusy: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenKeysSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = IgnitionAmber.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = VroumIcons.GraphicEq,
                            contentDescription = null,
                            tint = IgnitionAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_slogan),
                        style = MaterialTheme.typography.labelSmall,
                        color = AcousticCyan
                    )
                }
            }

            IconButton(
                onClick = onOpenKeysSettings,
                enabled = !isSessionBusy,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_keys_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configurer les clés API",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isSessionBusy) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = CarbonSurface,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    SegmentedTabItem(
                        label = "Écouter",
                        icon = VroumIcons.Mic,
                        selected = activeTab == HomeTab.LISTEN,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_listen"),
                        onClick = { onSelectTab(HomeTab.LISTEN) }
                    )
                    SegmentedTabItem(
                        label = if (historyCount > 0) "Historique ($historyCount)" else "Historique",
                        icon = VroumIcons.History,
                        selected = activeTab == HomeTab.HISTORY,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_history"),
                        onClick = { onSelectTab(HomeTab.HISTORY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) ElevatedPanel else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) IgnitionAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListenHomeContent(
    uiState: VroumUiState,
    showVehicleRequiredHint: Boolean,
    onSelectVehicleType: (VehicleType) -> Unit,
    onUpdateVehicleFields: (
        brand: String?,
        model: String?,
        year: String?,
        displacementCc: String?,
        mileage: String?
    ) -> Unit,
    onToggleUnknownBrandModel: (Boolean) -> Unit,
    onSkipAndListen: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onPickFromGalleryClick: () -> Unit,
    onNoPhotoClick: () -> Unit,
    onCancelPhotoIdentification: () -> Unit,
    onConfirmYesClick: () -> Unit,
    onRejectNoClick: () -> Unit,
    onSelectSuggestion: (VehicleCandidateSuggestion) -> Unit,
    onResetPhotoStep: () -> Unit,
    onDismissPhotoError: () -> Unit,
    onListenClick: () -> Unit,
    onDismissError: () -> Unit,
    onOpenKeysSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryMic: () -> Unit
) {
    val vehicleInfo = uiState.vehicleInfo
    val errorState = uiState.sessionState as? EngineSessionState.Error
    val micDeniedMessage = uiState.micPermissionDeniedMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (micDeniedMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VroumIcons.Mic, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = micDeniedMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onRetryMic) {
                            Text("Réessayer", color = IgnitionAmber)
                        }
                        TextButton(onClick = onOpenAppSettings) {
                            Text("Paramètres Android", color = AcousticCyan)
                        }
                    }
                }
            }
        }

        if (errorState != null) {
            var showErrorDetails by rememberSaveable(errorState.message, errorState.technicalDetails) {
                mutableStateOf(false)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .testTag("error_banner"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VroumIcons.ErrorOutline, contentDescription = null, tint = AlertRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!errorState.technicalDetails.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showErrorDetails = !showErrorDetails }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showErrorDetails) {
                                    "Masquer le détail technique"
                                } else {
                                    "Voir le détail technique (code HTTP / API)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = IgnitionAmber
                            )
                            Icon(
                                imageVector = if (showErrorDetails) VroumIcons.ExpandLess else VroumIcons.ExpandMore,
                                contentDescription = "Afficher ou masquer les détails techniques",
                                tint = IgnitionAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(visible = showErrorDetails) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = VoidObsidian.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Text(
                                    text = errorState.technicalDetails,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (errorState.isKeyError) {
                            TextButton(onClick = onOpenKeysSettings) {
                                Text("Ouvrir les clés", color = IgnitionAmber)
                            }
                        }
                        TextButton(onClick = onDismissError) {
                            Text("Fermer", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // 3 Vehicle Type buttons: Moto / Voiture / Autre
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VehicleTypeButton(
                type = VehicleType.MOTO,
                icon = VroumIcons.TwoWheeler,
                selected = vehicleInfo.type == VehicleType.MOTO,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vehicle_moto"),
                onClick = { onSelectVehicleType(VehicleType.MOTO) }
            )
            VehicleTypeButton(
                type = VehicleType.VOITURE,
                icon = VroumIcons.DirectionsCar,
                selected = vehicleInfo.type == VehicleType.VOITURE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vehicle_voiture"),
                onClick = { onSelectVehicleType(VehicleType.VOITURE) }
            )
            VehicleTypeButton(
                type = VehicleType.AUTRE,
                icon = Icons.Rounded.Build,
                selected = vehicleInfo.type == VehicleType.AUTRE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vehicle_autre"),
                onClick = { onSelectVehicleType(VehicleType.AUTRE) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Prominent central hero button: "Démarrer l'analyse"
        if (uiState.photoStepPhase != PhotoStepPhase.IDENTIFYING) {
            ShazamListenButton(onClick = onListenClick)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (vehicleInfo.canProceedToListen) {
                    "Prêt • Enregistrement guidé de ${EngineAudioRecorder.RECORD_SECONDS} s avec forme d'onde en direct"
                } else {
                    "Enregistrement guidé en ${EngineAudioRecorder.RECORD_SECONDS} s (ralenti + accélération + arrêt)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))
        }

        // Photo identification / confirmation / manual fallback step
        VehiclePhotoIdentificationCard(
            photoStepPhase = uiState.photoStepPhase,
            photoPreviewBitmap = uiState.photoPreviewBitmap,
            identificationResult = uiState.identificationResult,
            vehicleInfo = vehicleInfo,
            isLowConfidenceMatch = uiState.isLowConfidenceMatch,
            openedFromNoPhoto = uiState.openedFromNoPhoto,
            photoError = uiState.photoError,
            photoErrorDetails = uiState.photoErrorDetails,
            showVehicleRequiredHint = showVehicleRequiredHint,
            onTakePhotoClick = onTakePhotoClick,
            onPickFromGalleryClick = onPickFromGalleryClick,
            onNoPhotoClick = onNoPhotoClick,
            onCancelPhotoIdentification = onCancelPhotoIdentification,
            onConfirmYesClick = onConfirmYesClick,
            onRejectNoClick = onRejectNoClick,
            onSelectSuggestion = onSelectSuggestion,
            onUpdateVehicleFields = onUpdateVehicleFields,
            onToggleUnknownBrandModel = onToggleUnknownBrandModel,
            onSkipAndListen = onSkipAndListen,
            onResetPhotoStep = onResetPhotoStep,
            onDismissPhotoError = onDismissPhotoError
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun VehicleTypeButton(
    type: VehicleType,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(),
        label = "vehicle_scale"
    )
    Surface(
        modifier = modifier
            .height(86.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) IgnitionAmber.copy(alpha = 0.16f) else CarbonSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) IgnitionAmber else HairlineBorder
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = type.label,
                tint = if (selected) IgnitionAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShazamListenButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "listen_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val idleWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_wave_phase"
    )

    Box(
        modifier = Modifier.size(236.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(226.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(IgnitionAmber.copy(alpha = pulseAlpha))
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringRadius = size.minDimension * 0.43f
            val barCount = 32
            for (i in 0 until barCount) {
                val angle = 2.0 * PI * i / barCount
                val ripple = 0.5f + 0.5f * sin(idleWavePhase + i * 0.6f)
                val barLen = 4.dp.toPx() + 8.dp.toPx() * ripple
                val start = Offset(
                    x = center.x + (ringRadius * cos(angle)).toFloat(),
                    y = center.y + (ringRadius * sin(angle)).toFloat()
                )
                val end = Offset(
                    x = center.x + ((ringRadius + barLen) * cos(angle)).toFloat(),
                    y = center.y + ((ringRadius + barLen) * sin(angle)).toFloat()
                )
                drawLine(
                    color = (if (i % 2 == 0) AcousticCyan else IgnitionAmber).copy(alpha = 0.45f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Box(
            modifier = Modifier
                .size(196.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AcousticCyan.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Surface(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("listen_engine_button"),
            shape = CircleShape,
            shadowElevation = 16.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(WarmSpark, IgnitionAmber, Color(0xFFE04810))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = VroumIcons.GraphicEq,
                        contentDescription = stringResource(R.string.btn_listen_engine),
                        tint = VoidObsidian,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.btn_listen_engine),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = VoidObsidian,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingSessionContent(
    state: EngineSessionState.Recording,
    onCancel: () -> Unit
) {
    val phaseDuration = (state.totalDurationSeconds / 3).coerceAtLeast(1)
    val phase3Duration = (state.totalDurationSeconds - phaseDuration * 2).coerceAtLeast(1)

    val animatedAmp by animateFloatAsState(
        targetValue = state.amplitude,
        animationSpec = tween(90),
        label = "amp_smooth"
    )
    val remainingFractionTarget = if (state.totalDurationSeconds > 0 && state.phase < 4) {
        (state.totalSecondsRemaining.toFloat() / state.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedRemainingFraction by animateFloatAsState(
        targetValue = remainingFractionTarget,
        animationSpec = tween(450),
        label = "countdown_arc"
    )
    val totalCountdownText = remember(state.totalSecondsRemaining, state.phase) {
        formatCountdownSeconds(if (state.phase < 4) state.totalSecondsRemaining else 0)
    }
    val totalDurationText = remember(state.totalDurationSeconds) {
        formatCountdownSeconds(state.totalDurationSeconds)
    }

    val waveTransition = rememberInfiniteTransition(label = "recording_wave")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = AcousticCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AcousticCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = VroumIcons.VolumeUp,
                        contentDescription = null,
                        tint = AcousticCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (state.phase) {
                            1 -> "Étape 1 / 3 • Ralenti ($phaseDuration s)"
                            2 -> "Étape 2 / 3 • Accélération ($phaseDuration s)"
                            3 -> "Étape 3 / 3 • Retour au ralenti ($phase3Duration s)"
                            else -> "Capture terminée"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = AcousticCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = state.isTooNoisy) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = CautionAmber.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, CautionAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ambient_noise_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = VroumIcons.WarningAmber,
                            contentDescription = null,
                            tint = CautionAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trop de bruit autour, éloignez-vous.",
                            style = MaterialTheme.typography.labelLarge,
                            color = CautionAmber
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier.size(214.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.46f
                val arcDiameter = outerRadius * 2f
                val arcTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)

                drawCircle(
                    color = HairlineBorder.copy(alpha = 0.45f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )

                if (animatedRemainingFraction > 0.005f) {
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(IgnitionAmber, WarmSpark, AcousticCyan)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedRemainingFraction,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                val baseRadius = size.minDimension * 0.28f
                val pulseRadius = baseRadius + (size.minDimension * 0.08f * animatedAmp)

                drawCircle(
                    color = IgnitionAmber.copy(alpha = 0.14f),
                    radius = pulseRadius * 1.1f,
                    center = center
                )
                drawCircle(
                    color = AcousticCyan.copy(alpha = 0.30f),
                    radius = pulseRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val barCount = 28
                for (i in 0 until barCount) {
                    val angle = (2.0 * PI * i / barCount)
                    val harmonic = 0.42f + 0.58f * abs(sin(i * 1.15f + wavePhase)).toFloat()
                    val energy = (0.14f + animatedAmp * 0.95f).coerceIn(0.14f, 1.1f)
                    val barLen = 5.dp.toPx() + (16.dp.toPx() * energy * harmonic)
                    val startR = baseRadius * 0.90f
                    val start = Offset(
                        x = center.x + (startR * cos(angle)).toFloat(),
                        y = center.y + (startR * sin(angle)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + ((startR + barLen) * cos(angle)).toFloat(),
                        y = center.y + ((startR + barLen) * sin(angle)).toFloat()
                    )
                    drawLine(
                        color = if (i % 2 == 0) IgnitionAmber else AcousticCyan,
                        start = start,
                        end = end,
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.phase < 4) "TEMPS RESTANT" else "TERMINÉ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = totalCountdownText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum",
                        fontSize = 34.sp
                    ),
                    color = if (state.phase < 4) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        HealthyEmerald
                    },
                    modifier = Modifier.testTag("recording_total_countdown")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = CarbonSurface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = if (state.phase < 4) {
                            "Phase ${state.phase}/3 : ${state.secondsRemaining} s"
                        } else {
                            "WAV 16 kHz prêt"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = AcousticCyan,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .testTag("recording_phase_countdown")
                    )
                }
            }
        }

        LiveSoundWaveformPanel(
            amplitude = state.amplitude,
            animatedAmp = animatedAmp,
            wavePhase = wavePhase,
            isActive = state.phase < 4
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recording_phase_progress")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = VroumIcons.Timer,
                            contentDescription = null,
                            tint = IgnitionAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.phase < 4) {
                                "Fin du scan dans ${state.totalSecondsRemaining} s"
                            } else {
                                "Scan moteur terminé"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$totalCountdownText / $totalDurationText",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val phaseLabels = listOf("Ralenti", "Accél.", "Retour")
                    phaseLabels.forEachIndexed { index, label ->
                        val stepNumber = index + 1
                        val currentPhaseTotal = if (stepNumber == 3) phase3Duration else phaseDuration
                        val segmentFill = when {
                            state.phase > stepNumber -> 1f
                            state.phase == stepNumber -> (
                                (currentPhaseTotal - state.secondsRemaining)
                                    .coerceIn(0, currentPhaseTotal) / currentPhaseTotal.toFloat()
                                )
                            else -> 0f
                        }
                        val animatedSegmentFill by animateFloatAsState(
                            targetValue = segmentFill,
                            animationSpec = tween(350),
                            label = "phase_seg_$stepNumber"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(ElevatedPanel)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedSegmentFill)
                                        .height(6.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (state.phase > stepNumber) AcousticCyan else IgnitionAmber
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    state.phase == stepNumber -> IgnitionAmber
                                    state.phase > stepNumber -> AcousticCyan
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = state.instructionText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("cancel_recording_button"),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Annuler",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LiveSoundWaveformPanel(
    amplitude: Float,
    animatedAmp: Float,
    wavePhase: Float,
    isActive: Boolean
) {
    val barCount = 38
    val latestAmp by rememberUpdatedState(amplitude)
    val ampHistory = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.08f) }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            if (ampHistory.size >= barCount) {
                ampHistory.removeAt(0)
            }
            ampHistory.add(latestAmp.coerceIn(0.04f, 1f))
            delay(55L)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CarbonSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .testTag("recording_waveform_animation")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val midY = size.height / 2f
            val maxHalfHeight = size.height * 0.44f

            drawLine(
                color = HairlineBorder.copy(alpha = 0.55f),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.dp.toPx()
            )

            val slotWidth = size.width / barCount
            val barWidth = (slotWidth * 0.52f).coerceAtLeast(2.dp.toPx())
            for (i in 0 until barCount) {
                val histAmp = ampHistory.getOrElse(i) { 0.08f }
                val shimmer = 0.85f + 0.15f * sin(wavePhase * 1.5f + i * 0.45f)
                val normalized = if (isActive) {
                    ((0.10f + histAmp * 0.90f) * shimmer).coerceIn(0.08f, 1f)
                } else {
                    0.06f
                }
                val halfBar = maxHalfHeight * normalized
                val x = i * slotWidth + slotWidth / 2f
                val barColor = when {
                    histAmp > 0.6f -> IgnitionAmber
                    histAmp > 0.3f -> WarmSpark
                    else -> AcousticCyan
                }
                drawLine(
                    color = barColor.copy(alpha = 0.55f),
                    start = Offset(x, midY - halfBar),
                    end = Offset(x, midY + halfBar),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }

            val waveSamples = 64
            val primaryPath = Path()
            val harmonicPath = Path()
            val liveEnvelope = if (isActive) {
                (0.16f + animatedAmp * 0.84f).coerceIn(0.16f, 1f)
            } else {
                0.05f
            }

            for (s in 0..waveSamples) {
                val progress = s.toFloat() / waveSamples.toFloat()
                val x = progress * size.width
                val window = sin(progress * PI).toFloat()
                val yPrimary = midY + sin(progress * 4.5 * PI + wavePhase)
                    .toFloat() * maxHalfHeight * liveEnvelope * window
                val yHarmonic = midY + sin(progress * 7.5 * PI - wavePhase * 1.4f)
                    .toFloat() * maxHalfHeight * (liveEnvelope * 0.62f) * window

                if (s == 0) {
                    primaryPath.moveTo(x, yPrimary)
                    harmonicPath.moveTo(x, yHarmonic)
                } else {
                    primaryPath.lineTo(x, yPrimary)
                    harmonicPath.lineTo(x, yHarmonic)
                }
            }

            drawPath(
                path = harmonicPath,
                color = IgnitionAmber.copy(alpha = 0.85f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = primaryPath,
                color = AcousticCyan,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun formatCountdownSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}

@Composable
private fun PreparingSearchSessionContent(
    state: EngineSessionState.PreparingSearch,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("preparing_search_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(58.dp),
            color = AcousticCyan,
            trackColor = ElevatedPanel,
            strokeWidth = 5.dp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Pannes courantes (connaissances de l'IA)…",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.vehicleLabel,
            style = MaterialTheme.typography.titleMedium,
            color = IgnitionAmber,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Consultation des pannes connues sur ce modèle (max 15 s) avant l'enregistrement audio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onCancel) {
            Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyzingSessionContent(
    state: EngineSessionState.Analyzing,
    includeDeepSeek: Boolean,
    onCancel: () -> Unit
) {
    val visibleSteps = remember(includeDeepSeek) {
        PipelineStep.entries.filter { step ->
            when (step) {
                PipelineStep.DEEPSEEK_SEARCH -> includeDeepSeek
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = IgnitionAmber,
            trackColor = ElevatedPanel,
            strokeWidth = 5.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Analyse en cours…",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enregistrement de ${EngineAudioRecorder.RECORD_SECONDS} s terminé • Traitement acoustique et IA",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                visibleSteps.forEach { step ->
                    val isDone = step in state.completedSteps
                    val isCurrent = step == state.currentStep
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            isDone -> Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = HealthyEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            isCurrent -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = IgnitionAmber,
                                strokeWidth = 2.5.dp
                            )
                            else -> Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(HairlineBorder)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = when {
                                isDone -> HealthyEmerald
                                isCurrent -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onCancel) {
            Text("Annuler l'analyse", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryListContent(
    history: List<DiagnosticResult>,
    onOpenItem: (DiagnosticResult) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onStartNewClick: () -> Unit
) {
    if (history.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vroum_hero),
                        contentDescription = "Illustration Vroum",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .alpha(0.85f),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun diagnostic pour l'instant",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Chaque écoute enregistrée s'affichera ici pour suivre la santé de ton moteur dans le temps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onStartNewClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IgnitionAmber,
                            contentColor = VoidObsidian
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Écouter le moteur",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    } else {
        val formatter = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.FRENCH) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.id }) { item ->
                val confColor = when (item.confidence) {
                    ConfidenceLevel.ELEVE -> HealthyEmerald
                    ConfidenceLevel.MOYEN -> CautionAmber
                    ConfidenceLevel.FAIBLE -> AlertRed
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onOpenItem(item) }
                        .testTag("history_item_${item.id}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.vehicleInfo.displayTitle(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatter.format(Date(item.timestampMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = confColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "Confiance : ${item.confidence.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = confColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteItem(item.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = VroumIcons.DeleteOutline,
                                        contentDescription = "Supprimer ce diagnostic",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        item.probableCauses.firstOrNull()?.let { topCause ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Piste principale : ${topCause.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = AcousticCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
