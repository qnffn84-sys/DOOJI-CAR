package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.AppReleaseConfig
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.IosSwitchRow
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily

fun openUpdateDownloadLink(context: Context, url: String): Boolean {
    val clean = url.trim()
    if (!clean.startsWith("https://")) return false
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun AppUpdatePromptDialog(
    config: AppReleaseConfig,
    onOpenDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ios = LocalIosColors.current
    Dialog(
        onDismissRequest = { if (!config.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !config.forceUpdate,
            dismissOnClickOutside = !config.forceUpdate
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "새 버전 업데이트",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(6.dp))

                IosStatusBadge(
                    label = "v${config.latestVersionName}",
                    color = MaterialTheme.colorScheme.primary
                )

                if (config.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = config.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ios.secondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                IosPrimaryButton(
                    text = "업데이트",
                    onClick = { onOpenDownload(config.downloadUrl) },
                    testTagName = "app_update_confirm_button"
                )

                if (!config.forceUpdate) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("app_update_later_button")
                    ) {
                        Text(
                            text = "나중에",
                            color = ios.secondaryLabel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateConfigSheet(
    existing: AppReleaseConfig?,
    onDismiss: () -> Unit,
    onSave: (
        latestVersionCode: Long,
        latestVersionName: String,
        downloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ios = LocalIosColors.current

    var versionCodeText by remember {
        mutableStateOf((existing?.latestVersionCode ?: (BuildConfig.VERSION_CODE + 1).toLong()).toString())
    }
    var versionName by remember {
        mutableStateOf(existing?.latestVersionName?.ifBlank { "" } ?: "1.1")
    }
    var downloadUrl by remember {
        mutableStateOf(existing?.downloadUrl.orEmpty())
    }
    var releaseNotes by remember {
        mutableStateOf(existing?.releaseNotes.orEmpty())
    }
    var forceUpdate by remember {
        mutableStateOf(existing?.forceUpdate ?: false)
    }

    val parsedCode = versionCodeText.toLongOrNull()
    val isUrlValid = downloadUrl.trim().startsWith("https://") && downloadUrl.trim().length >= 8
    val canSave = parsedCode != null && parsedCode in 1L..100_000L &&
        versionName.trim().isNotEmpty() && isUrlValid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("app_update_config_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "배포 버전 관리",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ios.groupedSecondary
                ) {
                    Text(
                        text = "현재 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = versionCodeText,
                    onValueChange = { versionCodeText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("버전 코드") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("release_version_code_input")
                )
                OutlinedTextField(
                    value = versionName,
                    onValueChange = { versionName = it.take(20) },
                    label = { Text("버전명 (예: 1.1)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("release_version_name_input")
                )
            }

            OutlinedTextField(
                value = downloadUrl,
                onValueChange = { downloadUrl = it.take(500) },
                label = { Text("다운로드 링크 (https://...)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = downloadUrl.isNotBlank() && !isUrlValid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("release_download_url_input")
            )

            OutlinedTextField(
                value = releaseNotes,
                onValueChange = { releaseNotes = it.take(300) },
                label = { Text("업데이트 내용 (선택)") },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("release_notes_input")
            )

            IosGroupedCard {
                IosSwitchRow(
                    icon = Icons.Rounded.NewReleases,
                    iconTint = ios.orange,
                    title = "필수 업데이트",
                    checked = forceUpdate,
                    onCheckedChange = { forceUpdate = it },
                    testTagName = "release_force_update_switch"
                )
            }

            Spacer(Modifier.height(4.dp))

            IosPrimaryButton(
                text = "저장",
                enabled = canSave,
                onClick = {
                    if (parsedCode != null) {
                        onSave(
                            parsedCode,
                            versionName.trim(),
                            downloadUrl.trim(),
                            releaseNotes.trim(),
                            forceUpdate
                        )
                    }
                },
                testTagName = "release_save_button"
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
