package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.UserProfile
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.IosSwitchRow
import com.example.ui.theme.LocalIosColors
import com.example.util.BiometricAuthHelper

@Composable
fun NicknameSetupScreen(
    suggestedNickname: String,
    googleEmail: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onComplete: (nickname: String, enableBiometric: Boolean) -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val ios = LocalIosColors.current
    val biometricHardwareReady = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

    var nickname by remember(suggestedNickname) { mutableStateOf(suggestedNickname.take(24)) }
    var enableBiometric by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "닉네임 설정",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (googleEmail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = googleEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ios.secondaryLabel
                )
            }

            Spacer(Modifier.height(22.dp))

            IosGroupedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = {
                            nickname = it.take(24)
                            onClearError()
                        },
                        label = { Text("닉네임") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = ios.separator
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_setup_input")
                    )
                }

                if (biometricHardwareReady) {
                    IosSwitchRow(
                        icon = Icons.Rounded.Fingerprint,
                        iconTint = ios.green,
                        title = "생체 인증 잠금",
                        checked = enableBiometric,
                        onCheckedChange = { enableBiometric = it },
                        testTagName = "register_biometric_switch"
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ios.red
                )
            }

            Spacer(Modifier.height(18.dp))

            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                IosPrimaryButton(
                    text = "시작하기",
                    enabled = nickname.trim().isNotEmpty(),
                    onClick = { onComplete(nickname.trim(), enableBiometric) },
                    testTagName = "nickname_setup_submit"
                )
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSignOut) {
                Text(
                    text = "다른 Google 계정",
                    style = MaterialTheme.typography.labelMedium,
                    color = ios.secondaryLabel
                )
            }
        }
    }
}

@Composable
fun BiometricLockScreen(
    profile: UserProfile,
    onBiometricUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val ios = LocalIosColors.current
    val biometricReady = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        if (activity != null && biometricReady) {
            errorMessage = null
            BiometricAuthHelper.authenticate(
                activity = activity,
                onSuccess = onBiometricUnlocked,
                onError = { msg -> errorMessage = msg }
            )
        } else {
            onBiometricUnlocked()
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.size(84.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_doojicar_logo),
                    contentDescription = "두지카 로고",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "두지카",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = profile.displayNickname(),
                style = MaterialTheme.typography.titleMedium,
                color = ios.secondaryLabel
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ios.red
                )
            }

            Spacer(Modifier.height(28.dp))

            Surface(
                onClick = { triggerBiometric() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("biometric_unlock_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "생체 인증",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "생체 인증으로 잠금 해제",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignOut) {
                Text(
                    text = "로그아웃",
                    style = MaterialTheme.typography.labelMedium,
                    color = ios.secondaryLabel
                )
            }
        }
    }
}
