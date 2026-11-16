package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.ConfidenceLevel
import com.example.model.VehicleCandidateSuggestion
import com.example.model.VehicleIdentificationResult
import com.example.model.VehicleInfo
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.ElevatedPanel
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HealthyEmerald
import com.example.ui.theme.IgnitionAmber
import com.example.ui.theme.VoidObsidian

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehiclePhotoIdentificationCard(
    photoStepPhase: PhotoStepPhase,
    photoPreviewBitmap: Bitmap?,
    identificationResult: VehicleIdentificationResult?,
    vehicleInfo: VehicleInfo,
    isLowConfidenceMatch: Boolean,
    openedFromNoPhoto: Boolean,
    photoError: String?,
    showVehicleRequiredHint: Boolean,
    onTakePhotoClick: () -> Unit,
    onPickFromGalleryClick: () -> Unit,
    onNoPhotoClick: () -> Unit,
    onCancelPhotoIdentification: () -> Unit,
    onConfirmYesClick: () -> Unit,
    onRejectNoClick: () -> Unit,
    onSelectSuggestion: (VehicleCandidateSuggestion) -> Unit,
    onUpdateVehicleFields: (
        brand: String?,
        model: String?,
        year: String?,
        displacementCc: String?,
        mileage: String?
    ) -> Unit,
    onToggleUnknownBrandModel: (Boolean) -> Unit,
    onResetPhotoStep: () -> Unit,
    onDismissPhotoError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                showVehicleRequiredHint && !vehicleInfo.canProceedToListen -> CautionAmber
                photoStepPhase == PhotoStepPhase.CONFIRM_MATCH -> AcousticCyan.copy(alpha = 0.6f)
                else -> HairlineBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (photoStepPhase) {
                PhotoStepPhase.PICK_OR_NO_PHOTO -> {
                    PickPhotoStepContent(
                        photoError = photoError,
                        onTakePhotoClick = onTakePhotoClick,
                        onPickFromGalleryClick = onPickFromGalleryClick,
                        onNoPhotoClick = onNoPhotoClick,
                        onDismissPhotoError = onDismissPhotoError
                    )
                }

                PhotoStepPhase.IDENTIFYING -> {
                    IdentifyingPhotoContent(
                        previewBitmap = photoPreviewBitmap,
                        onCancel = onCancelPhotoIdentification
                    )
                }

                PhotoStepPhase.CONFIRM_MATCH -> {
                    ConfirmMatchContent(
                        previewBitmap = photoPreviewBitmap,
                        identificationResult = identificationResult,
                        vehicleInfo = vehicleInfo,
                        onConfirmYesClick = onConfirmYesClick,
                        onRejectNoClick = onRejectNoClick,
                        onSelectSuggestion = onSelectSuggestion,
                        onUpdateMileage = { mileage ->
                            onUpdateVehicleFields(null, null, null, null, mileage)
                        },
                        onResetPhotoStep = onResetPhotoStep
                    )
                }

                PhotoStepPhase.MANUAL_OR_CORRECT -> {
                    ManualOrCorrectionContent(
                        previewBitmap = photoPreviewBitmap,
                        identificationResult = identificationResult,
                        vehicleInfo = vehicleInfo,
                        isLowConfidenceMatch = isLowConfidenceMatch,
                        openedFromNoPhoto = openedFromNoPhoto,
                        showVehicleRequiredHint = showVehicleRequiredHint,
                        onSelectSuggestion = onSelectSuggestion,
                        onUpdateVehicleFields = onUpdateVehicleFields,
                        onToggleUnknownBrandModel = onToggleUnknownBrandModel,
                        onResetPhotoStep = onResetPhotoStep
                    )
                }
            }
        }
    }
}

@Composable
private fun PickPhotoStepContent(
    photoError: String?,
    onTakePhotoClick: () -> Unit,
    onPickFromGalleryClick: () -> Unit,
    onNoPhotoClick: () -> Unit,
    onDismissPhotoError: () -> Unit
) {
    Text(
        text = "Étape 1 • Identifier le véhicule",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )

    // Side-view framing tip requested by user
    Surface(
        shape = MaterialTheme.shapes.small,
        color = AcousticCyan.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AcousticCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = AcousticCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Photo de côté, moto entière visible.",
                style = MaterialTheme.typography.bodyMedium,
                color = AcousticCyan
            )
        }
    }

    if (photoError != null) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("photo_error_banner")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = AlertRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = photoError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissPhotoError) {
                        Text("Fermer", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TextButton(onClick = onNoPhotoClick) {
                        Text("Saisir sans photo", color = IgnitionAmber)
                    }
                }
            }
        }
    }

    Button(
        onClick = onTakePhotoClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("take_photo_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = IgnitionAmber,
            contentColor = VoidObsidian
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Prendre une photo de la moto",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }

    OutlinedButton(
        onClick = onPickFromGalleryClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("pick_gallery_button"),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = AcousticCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Choisir depuis la galerie",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    HorizontalDivider(color = HairlineBorder.copy(alpha = 0.6f))

    TextButton(
        onClick = onNoPhotoClick,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("no_photo_button")
    ) {
        Icon(
            imageVector = Icons.Rounded.NoPhotography,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Je n'ai pas la photo",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdentifyingPhotoContent(
    previewBitmap: Bitmap?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (previewBitmap != null) {
            PhotoThumbnail(
                bitmap = previewBitmap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }

        CircularProgressIndicator(
            color = IgnitionAmber,
            strokeWidth = 3.dp,
            modifier = Modifier.size(34.dp)
        )

        Text(
            text = "Gemini identifie le modèle...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Détection de la marque, année, cylindrée et type de moteur.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("cancel_photo_id_button"),
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Annuler", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmMatchContent(
    previewBitmap: Bitmap?,
    identificationResult: VehicleIdentificationResult?,
    vehicleInfo: VehicleInfo,
    onConfirmYesClick: () -> Unit,
    onRejectNoClick: () -> Unit,
    onSelectSuggestion: (VehicleCandidateSuggestion) -> Unit,
    onUpdateMileage: (String) -> Unit,
    onResetPhotoStep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val conf = identificationResult?.confidence ?: ConfidenceLevel.MOYEN
        val confColor = if (conf == ConfidenceLevel.ELEVE) HealthyEmerald else AcousticCyan
        Surface(
            shape = MaterialTheme.shapes.small,
            color = confColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, confColor.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = confColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Certitude : ${conf.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = confColor
                )
            }
        }

        TextButton(
            onClick = onResetPhotoStep,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Autre photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (previewBitmap != null) {
        PhotoThumbnail(
            bitmap = previewBitmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        )
    }

    val confirmationParts = listOfNotNull(
        vehicleInfo.brand.trim().takeIf { it.isNotEmpty() },
        vehicleInfo.model.trim().takeIf { it.isNotEmpty() },
        vehicleInfo.year.trim().takeIf { it.isNotEmpty() }
    ).joinToString(" ")

    Text(
        text = "C'est une $confirmationParts ?",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("photo_confirmation_question")
    )

    val specsSummary = listOfNotNull(
        vehicleInfo.displacementCc.takeIf { it.isNotBlank() }?.let { "$it cc" },
        vehicleInfo.engineType.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

    if (specsSummary.isNotBlank()) {
        Text(
            text = "Moteur estimé : $specsSummary",
            style = MaterialTheme.typography.bodyMedium,
            color = AcousticCyan
        )
    }

    // If Gemini hesitated between multiple models, show all candidates cleanly
    val alternatives = identificationResult?.suggestions.orEmpty().filterNot {
        it.brand.equals(vehicleInfo.brand, ignoreCase = true) &&
            it.model.equals(vehicleInfo.model, ignoreCase = true) &&
            it.year.equals(vehicleInfo.year, ignoreCase = true)
    }
    if (alternatives.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Autres modèles possibles détectés :",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alternatives.forEach { candidate ->
                    SuggestionPill(
                        label = candidate.displayLabel(),
                        onClick = { onSelectSuggestion(candidate) }
                    )
                }
            }
        }
    }

    // Optional mileage field directly accessible before confirming
    PhotoStepTextField(
        value = vehicleInfo.mileage,
        onValueChange = onUpdateMileage,
        label = "Kilométrage (optionnel)",
        placeholder = "Ex: 24000",
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("input_mileage")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRejectNoClick,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("confirm_photo_no_button"),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Non, corriger",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Button(
            onClick = onConfirmYesClick,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("confirm_photo_yes_button"),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = IgnitionAmber,
                contentColor = VoidObsidian
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Oui",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualOrCorrectionContent(
    previewBitmap: Bitmap?,
    identificationResult: VehicleIdentificationResult?,
    vehicleInfo: VehicleInfo,
    isLowConfidenceMatch: Boolean,
    openedFromNoPhoto: Boolean,
    showVehicleRequiredHint: Boolean,
    onSelectSuggestion: (VehicleCandidateSuggestion) -> Unit,
    onUpdateVehicleFields: (
        brand: String?,
        model: String?,
        year: String?,
        displacementCc: String?,
        mileage: String?
    ) -> Unit,
    onToggleUnknownBrandModel: (Boolean) -> Unit,
    onResetPhotoStep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                isLowConfidenceMatch -> "Vérifier l'identification"
                openedFromNoPhoto -> "Sans photo • Infos véhicule"
                else -> "Corriger le modèle"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onResetPhotoStep,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = AcousticCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (openedFromNoPhoto) "Prendre une photo" else "Autre photo",
                color = AcousticCyan
            )
        }
    }

    if (previewBitmap != null) {
        PhotoThumbnail(
            bitmap = previewBitmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }

    if (isLowConfidenceMatch) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = CautionAmber.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("low_confidence_banner")
        ) {
            Text(
                text = "Certitude faible : complète la marque et le modèle ou choisis une suggestion de l'IA.",
                style = MaterialTheme.typography.bodyMedium,
                color = CautionAmber,
                modifier = Modifier.padding(10.dp)
            )
        }

        val suggestions = identificationResult?.suggestions.orEmpty()
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Suggestions de l'IA :",
                    style = MaterialTheme.typography.labelMedium,
                    color = AcousticCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { candidate ->
                        val isSelected = candidate.brand.equals(vehicleInfo.brand, ignoreCase = true) &&
                            candidate.model.equals(vehicleInfo.model, ignoreCase = true)
                        SuggestionPill(
                            label = candidate.displayLabel(),
                            selected = isSelected,
                            onClick = { onSelectSuggestion(candidate) }
                        )
                    }
                }
            }
        }
    }

    if (showVehicleRequiredHint && !vehicleInfo.canProceedToListen) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = CautionAmber.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Indique la marque et le modèle, ou appuie sur « Passer l'étape » pour lancer l'écoute.",
                style = MaterialTheme.typography.bodyMedium,
                color = CautionAmber,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    if (!vehicleInfo.unknownBrandModel) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoStepTextField(
                value = vehicleInfo.brand,
                onValueChange = { onUpdateVehicleFields(it, null, null, null, null) },
                label = "Marque *",
                placeholder = "Ex: Yamaha",
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_brand")
            )
            PhotoStepTextField(
                value = vehicleInfo.model,
                onValueChange = { onUpdateVehicleFields(null, it, null, null, null) },
                label = "Modèle *",
                placeholder = "Ex: MT-07",
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_model")
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoStepTextField(
                value = vehicleInfo.year,
                onValueChange = { onUpdateVehicleFields(null, null, it, null, null) },
                label = "Année (optionnel)",
                placeholder = "Ex: 2020",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_year")
            )
            PhotoStepTextField(
                value = vehicleInfo.displacementCc,
                onValueChange = { onUpdateVehicleFields(null, null, null, it, null) },
                label = "Cylindrée cc (opt.)",
                placeholder = "Ex: 689",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_displacement_cc")
            )
        }

        PhotoStepTextField(
            value = vehicleInfo.mileage,
            onValueChange = { onUpdateVehicleFields(null, null, null, null, it) },
            label = "Kilométrage (optionnel)",
            placeholder = "Ex: 32000",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_mileage")
        )

        OutlinedButton(
            onClick = { onToggleUnknownBrandModel(true) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("skip_identification_button"),
            border = BorderStroke(1.dp, HairlineBorder),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Passer l'étape",
                style = MaterialTheme.typography.labelLarge,
                color = AcousticCyan
            )
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = ElevatedPanel,
            border = BorderStroke(1.dp, AcousticCyan.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Étape passée • Analyse générale (confiance plus basse)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AcousticCyan,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onToggleUnknownBrandModel(false) }) {
                    Text("Saisir", color = IgnitionAmber)
                }
            }
        }

        PhotoStepTextField(
            value = vehicleInfo.mileage,
            onValueChange = { onUpdateVehicleFields(null, null, null, null, it) },
            label = "Kilométrage (optionnel)",
            placeholder = "Ex: 32000",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_mileage")
        )
    }
}

@Composable
private fun PhotoThumbnail(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VoidObsidian)
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Photo du véhicule",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SuggestionPill(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) IgnitionAmber.copy(alpha = 0.2f) else ElevatedPanel,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) IgnitionAmber else HairlineBorder
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) IgnitionAmber else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PhotoStepTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = IgnitionAmber,
            unfocusedBorderColor = HairlineBorder,
            focusedContainerColor = VoidObsidian,
            unfocusedContainerColor = VoidObsidian
        )
    )
}
