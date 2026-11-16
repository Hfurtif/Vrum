package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConfidenceLevel
import com.example.model.DiagnosticResult
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.ElevatedPanel
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HealthyEmerald
import com.example.ui.theme.IgnitionAmber
import com.example.ui.theme.VoidObsidian
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultScreen(
    result: DiagnosticResult?,
    onBack: () -> Unit,
    onNewAnalysis: () -> Unit
) {
    val context = LocalContext.current
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("result_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Résultat du diagnostic",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (result != null) {
                        val formatter = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.FRENCH) }
                        Text(
                            text = "${result.vehicleInfo.displayTitle()} • ${formatter.format(Date(result.timestampMillis))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (result == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Diagnostic introuvable.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Confidence level badge
                val confidenceColor = when (result.confidence) {
                    ConfidenceLevel.ELEVE -> HealthyEmerald
                    ConfidenceLevel.MOYEN -> CautionAmber
                    ConfidenceLevel.FAIBLE -> AlertRed
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = confidenceColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, confidenceColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confidence_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = confidenceColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Niveau de confiance : ${result.confidence.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = confidenceColor
                        )
                    }
                }

                // Plain-language summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Ce que Vroum en pense",
                            style = MaterialTheme.typography.titleMedium,
                            color = AcousticCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Mechanic advice banner
                val mechanicColor = if (result.seeMechanicRecommended) CautionAmber else HealthyEmerald
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = mechanicColor.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, mechanicColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (result.seeMechanicRecommended) Icons.Rounded.WarningAmber else Icons.Rounded.Build,
                            contentDescription = null,
                            tint = mechanicColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (result.seeMechanicRecommended) {
                                    "À voir chez un mécanicien"
                                } else {
                                    "Conseil d'atelier"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = mechanicColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.mechanicAdvice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Probable causes ranked list
                if (result.probableCauses.isNotEmpty()) {
                    Text(
                        text = "Causes probables (de la + à la - probable)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    result.probableCauses.forEachIndexed { index, cause ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(28.dp),
                                            shape = CircleShape,
                                            color = IgnitionAmber.copy(alpha = 0.18f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = IgnitionAmber
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cause.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (cause.probabilityLabel.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = ElevatedPanel
                                        ) {
                                            Text(
                                                text = cause.probabilityLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AcousticCyan,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                if (cause.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = cause.explanation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Self-checks
                if (result.selfChecks.isNotEmpty()) {
                    Text(
                        text = "Vérifications à faire soi-même",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            result.selfChecks.forEach { check ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = HealthyEmerald,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = check,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional warnings if Perplexity/OpenAI failed gracefully
                if (result.optionalWarnings.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = ElevatedPanel,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            result.optionalWarnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Expandable technical details & sources
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTechnicalDetails = !showTechnicalDetails }
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mesures audio & notes des experts IA",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (showTechnicalDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Afficher les détails techniques",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showTechnicalDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Fréquence dominante : ${result.acousticMetrics.dominantFrequencyHz} Hz • " +
                                        "Régime estimé : ~${result.acousticMetrics.estimatedRpm} tr/min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AcousticCyan
                                )
                                Text(
                                    text = "Régularité : ${result.acousticMetrics.regularityLabel} " +
                                        "(${(result.acousticMetrics.regularityScore * 100).toInt()}%) • " +
                                        "Pics anormaux : ${if (result.acousticMetrics.abnormalPeaksDetected) "Oui (${result.acousticMetrics.peakCount})" else "Non"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (result.geminiDescription.isNotBlank()) {
                                    Text(
                                        text = "Oreille Gemini : ${result.geminiDescription}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!result.openAiDescription.isNullOrBlank()) {
                                    Text(
                                        text = "Second avis OpenAI : ${result.openAiDescription}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!result.perplexitySummary.isNullOrBlank()) {
                                    Text(
                                        text = "Pannes connues (Perplexity) : ${result.perplexitySummary}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (result.perplexitySources.isNotEmpty()) {
                                    Text(
                                        text = "Sources web :",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AcousticCyan
                                    )
                                    result.perplexitySources.take(5).forEach { url ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                        )
                                                    }
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null,
                                                tint = AcousticCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = url,
                                                style = MaterialTheme.typography.bodyMedium,
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

                // Mandatory disclaimer
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ElevatedPanel,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disclaimer_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.disclaimer_estimate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Bottom Primary CTA: "Nouvelle analyse"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoidObsidian)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = onNewAnalysis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("new_analysis_button"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IgnitionAmber,
                        contentColor = VoidObsidian
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nouvelle analyse",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
