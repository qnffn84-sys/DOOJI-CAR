package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.AppReleaseConfig
import com.example.data.ThemePreference
import com.example.data.UserProfile
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosSectionTitle
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.IosSettingRow
import com.example.ui.components.IosSwitchRow
import com.example.ui.theme.LocalIosColors
import com.example.util.BiometricAuthHelper
import com.example.util.OilNotificationHelper
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SettingsScreen(
    currentUserId: String,
    googleEmail: String,
    profile: UserProfile?,
    releaseConfig: AppReleaseConfig?,
    currentTheme: ThemePreference,
    onUpdatePreferences: (
        biometricEnabled: Boolean,
        darkMode: ThemePreference,
        oilAlertEnabled: Boolean,
        oilAlertThresholdKm: Long
    ) -> Unit,
    onTriggerOilCheckNow: () -> Unit,
    onOpenChangeNickname: () -> Unit,
    onOpenUpdateLink: (String) -> Unit,
    onOpenUpdateConfig: () -> Unit,
    onLockNow: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val biometricHardwareReady = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

    val biometricEnabled = profile?.biometricEnabled ?: false
    val oilAlertEnabled = profile?.oilAlertEnabled ?: true
    val oilThresholdKm = profile?.oilAlertThresholdKm ?: 1000L

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onTriggerOilCheckNow()
        } else {
            onShowMessage("알림 권한 필요")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "설정",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Theme Mode
        item {
            IosSectionTitle("화면 테마")
            IosGroupedCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    val modes = ThemePreference.entries
                    IosSegmentedControl(
                        options = modes.map { it.labelKo },
                        selectedIndex = modes.indexOf(currentTheme).coerceAtLeast(0),
                        onSelected = { idx ->
                            val next = modes[idx]
                            onUpdatePreferences(biometricEnabled, next, oilAlertEnabled, oilThresholdKm)
                        }
                    )
                }
            }
        }

        // Oil Alert Settings
        item {
            IosSectionTitle("엔진 오일 알림")
            IosGroupedCard {
                IosSwitchRow(
                    icon = Icons.Rounded.NotificationsActive,
                    iconTint = ios.orange,
                    title = "교체 시기 알림",
                    checked = oilAlertEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePreferences(biometricEnabled, currentTheme, enabled, oilThresholdKm)
                    },
                    testTagName = "settings_oil_alert_switch"
                )
                IosHairlineDivider(startIndent = 60.dp)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "알림 기준 거리",
                        style = MaterialTheme.typography.labelMedium,
                        color = ios.secondaryLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(500L, 1000L, 2000L).forEach { km ->
                            val selected = oilThresholdKm == km
                            Surface(
                                onClick = {
                                    onUpdatePreferences(biometricEnabled, currentTheme, oilAlertEnabled, km)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else ios.groupedSecondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("threshold_$km")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${nf.format(km)} km 전",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                IosHairlineDivider(startIndent = 60.dp)
                IosSettingRow(
                    icon = Icons.Rounded.OilBarrel,
                    iconTint = ios.green,
                    title = "알림 즉시 확인",
                    onClick = {
                        if (OilNotificationHelper.hasPermission(context)) {
                            onTriggerOilCheckNow()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    testTagName = "settings_check_oil_now"
                )
            }
        }

        // Security
        item {
            IosSectionTitle("보안")
            IosGroupedCard {
                IosSwitchRow(
                    icon = Icons.Rounded.Fingerprint,
                    iconTint = ios.indigo,
                    title = "생체 인증 잠금",
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !biometricHardwareReady) {
                            onShowMessage("기기에 생체 인증이 등록되지 않음")
                        } else {
                            onUpdatePreferences(enabled, currentTheme, oilAlertEnabled, oilThresholdKm)
                        }
                    },
                    testTagName = "settings_biometric_switch"
                )
                IosHairlineDivider(startIndent = 60.dp)
                IosSettingRow(
                    icon = Icons.Rounded.Lock,
                    iconTint = ios.orange,
                    title = "화면 잠금",
                    onClick = onLockNow,
                    testTagName = "settings_lock_now"
                )
            }
        }

        // Account
        item {
            IosSectionTitle("계정")
            IosGroupedCard {
                IosSettingRow(
                    icon = Icons.Rounded.Person,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "닉네임",
                    value = profile?.displayNickname().orEmpty(),
                    onClick = onOpenChangeNickname,
                    testTagName = "settings_change_nickname"
                )
                IosHairlineDivider(startIndent = 60.dp)
                IosSettingRow(
                    icon = Icons.Rounded.Key,
                    iconTint = ios.teal,
                    title = "Google",
                    value = googleEmail.ifBlank { "-" },
                    showChevron = false
                )
                IosHairlineDivider(startIndent = 60.dp)
                IosSettingRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    iconTint = ios.red,
                    title = "로그아웃",
                    showChevron = false,
                    onClick = onSignOut,
                    testTagName = "settings_sign_out_button"
                )
            }
        }

        // App Version & Distribution Update
        item {
            val hasUpdate = releaseConfig?.hasUpdateFor(BuildConfig.VERSION_CODE) == true
            val canManageRelease = releaseConfig == null || releaseConfig.adminUid == currentUserId
            IosSectionTitle("앱 업데이트")
            IosGroupedCard {
                IosSettingRow(
                    icon = Icons.Rounded.SystemUpdate,
                    iconTint = if (hasUpdate) ios.green else MaterialTheme.colorScheme.primary,
                    title = if (hasUpdate) "새 버전 v${releaseConfig?.latestVersionName}" else "현재 버전",
                    value = if (hasUpdate) "업데이트" else "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    showChevron = hasUpdate,
                    onClick = if (hasUpdate) {
                        { releaseConfig?.downloadUrl?.let(onOpenUpdateLink) }
                    } else null,
                    testTagName = "settings_app_version_row"
                )
                if (canManageRelease) {
                    IosHairlineDivider(startIndent = 60.dp)
                    IosSettingRow(
                        icon = Icons.Rounded.NewReleases,
                        iconTint = ios.indigo,
                        title = "배포 버전 관리",
                        value = releaseConfig?.let { "v${it.latestVersionName}" } ?: "미설정",
                        onClick = onOpenUpdateConfig,
                        testTagName = "settings_manage_release_row"
                    )
                }
            }
        }
    }
}
