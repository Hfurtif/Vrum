package com.vroum.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vroum.app.model.ApiKeys
import com.vroum.app.ui.theme.AcousticCyan
import com.vroum.app.ui.theme.CarbonSurface
import com.vroum.app.ui.theme.ElevatedPanel
import com.vroum.app.ui.theme.HairlineBorder
import com.vroum.app.ui.theme.HealthyEmerald
import com.vroum.app.ui.theme.IgnitionAmber
import com.vroum.app.ui.theme.VoidObsidian
import kotlinx.coroutines.launch

@Composable
fun KeysScreen(
    initialKeys: ApiKeys,
    showBackButton: Boolean,
    onSaveKeys: (ApiKeys) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var geminiKey by rememberSaveable { mutableStateOf(initialKeys.geminiKey) }
    var openAiKey by rememberSaveable { mutableStateOf(initialKeys.openAiKey) }
    var deepSeekKey by rememberSaveable { mutableStateOf(initialKeys.deepSeekKey) }

    val canSave = geminiKey.trim().isNotEmpty() && openAiKey.trim().isNotEmpty()

    fun openHelpUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(intent) }
    }

    fun pasteFromClipboard(label: String, onTextPasted: (String) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val pasted = item?.coerceToText(context)?.toString()?.trim().orEmpty()
        if (pasted.isNotEmpty()) {
            onTextPasted(pasted)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("$label collée")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Le presse-papiers est vide. Copie d'abord ta clé.")
            }
        }
    }

    Scaffold(
        containerColor = VoidObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VoidObsidian)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("keys_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Retour",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tes clés IA",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Vroum • Écoute ton moteur.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AcousticCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = ElevatedPanel,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = HealthyEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tes clés sont chiffrées dans le Keystore Android de ton téléphone. Aucun serveur intermédiaire.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                ApiKeyInputCard(
                    label = "Clé Gemini",
                    badge = "Obligatoire",
                    badgeRequired = true,
                    description = "Identifie ta moto sur la photo et écoute l'audio du moteur pour décrire chaque bruit.",
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    onPasteClick = { pasteFromClipboard("Clé Gemini") { geminiKey = it } },
                    testTag = "input_gemini_key",
                    onHelpClick = { openHelpUrl("https://aistudio.google.com/apikey") }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ApiKeyInputCard(
                    label = "Clé OpenAI",
                    badge = "Obligatoire",
                    badgeRequired = true,
                    description = "Synthétise les mesures audio et rédige le diagnostic final en JSON.",
                    value = openAiKey,
                    onValueChange = { openAiKey = it },
                    onPasteClick = { pasteFromClipboard("Clé OpenAI") { openAiKey = it } },
                    testTag = "input_openai_key",
                    onHelpClick = { openHelpUrl("https://platform.openai.com/api-keys") }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ApiKeyInputCard(
                    label = "Clé DeepSeek",
                    badge = "Optionnel",
                    badgeRequired = false,
                    description = "Apporte des connaissances sur les pannes courantes du modèle (texte uniquement).",
                    value = deepSeekKey,
                    onValueChange = { deepSeekKey = it },
                    onPasteClick = { pasteFromClipboard("Clé DeepSeek") { deepSeekKey = it } },
                    testTag = "input_deepseek_key",
                    onHelpClick = { openHelpUrl("https://platform.deepseek.com/api_keys") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        onSaveKeys(
                            ApiKeys(
                                geminiKey = geminiKey.trim(),
                                openAiKey = openAiKey.trim(),
                                deepSeekKey = deepSeekKey.trim()
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("save_keys_button"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IgnitionAmber,
                        contentColor = VoidObsidian,
                        disabledContainerColor = ElevatedPanel,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "Enregistrer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (!canSave) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Clés obligatoires : Gemini et OpenAI. La clé DeepSeek est optionnelle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ApiKeyInputCard(
    label: String,
    badge: String,
    badgeRequired: Boolean,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    testTag: String,
    onHelpClick: () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VroumIcons.Key,
                        contentDescription = null,
                        tint = if (badgeRequired) IgnitionAmber else AcousticCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (badgeRequired) {
                        IgnitionAmber.copy(alpha = 0.16f)
                    } else {
                        AcousticCyan.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badgeRequired) IgnitionAmber else AcousticCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Colle ta clé ici…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Effacer la clé",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (visible) VroumIcons.VisibilityOff else VroumIcons.Visibility,
                                contentDescription = if (visible) "Masquer la clé" else "Afficher la clé",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IgnitionAmber,
                    unfocusedBorderColor = HairlineBorder,
                    focusedContainerColor = VoidObsidian,
                    unfocusedContainerColor = VoidObsidian
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onHelpClick)
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comment obtenir cette clé ?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AcousticCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = VroumIcons.OpenInNew,
                        contentDescription = "Ouvrir le lien officiel",
                        tint = AcousticCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_paste"),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, AcousticCyan.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = VroumIcons.ContentPaste,
                        contentDescription = "Coller $label",
                        tint = AcousticCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Coller",
                        style = MaterialTheme.typography.labelLarge,
                        color = AcousticCyan
                    )
                }
            }
        }
    }
}
