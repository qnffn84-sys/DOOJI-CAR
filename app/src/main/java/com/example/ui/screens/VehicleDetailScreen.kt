package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.MaintenanceRecord
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.data.VehicleChangeLog
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosKeyValueRow
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.IosSectionTitle
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.IosSwitchRow
import com.example.ui.components.statusColor
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.OilNotificationHelper
import com.example.util.ParkingPhotoHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun VehicleDetailScreen(
    vehicle: Vehicle,
    companyName: String,
    maintenanceState: UiState<List<MaintenanceRecord>>,
    historyState: UiState<List<VehicleChangeLog>>,
    oilThresholdKm: Long,
    onBack: () -> Unit,
    onDeleteVehicle: () -> Unit,
    onUpdateParking: (location: String, photoPath: String?, photoBase64: String?) -> Unit,
    onUpdateVehiclePhoto: (photoPath: String?, photoBase64: String?) -> Unit,
    onUpdateOilSettings: (lastOilChangeKm: Long, intervalKm: Long, alertEnabled: Boolean) -> Unit,
    onOpenEditParkingLocation: () -> Unit,
    onOpenAdminEdit: () -> Unit,
    onOpenAddMaintenance: () -> Unit,
    onDeleteMaintenance: (String) -> Unit,
    onOpenEditInsurance: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fullScreenPhoto by remember { mutableStateOf<Pair<Bitmap, String>?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showCustomOilIntervalSheet by remember { mutableStateOf(false) }
    var showVehiclePhotoSheet by remember { mutableStateOf(false) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    val parkingBitmap = remember(vehicle.parkingPhotoPath, vehicle.parkingPhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.parkingPhotoPath, vehicle.parkingPhotoBase64)
    }
    val vehicleBitmap = remember(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null && file.exists()) {
            scope.launch {
                val saved = ParkingPhotoHelper.processCapturedFile(file)
                onUpdateParking(vehicle.parkingLocation, saved.localPath, saved.thumbnailBase64)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, vehicle.id)
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        } else {
            onShowMessage("카메라 권한 필요")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = ParkingPhotoHelper.copyFromUri(context, vehicle.id, uri)
                if (saved != null) {
                    onUpdateParking(vehicle.parkingLocation, saved.localPath, saved.thumbnailBase64)
                } else {
                    onShowMessage("사진 저장 실패")
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            OilNotificationHelper.sendSingleVehicleTestAlert(context, vehicle)
            onShowMessage("알림 발송됨")
        } else {
            onShowMessage("알림 권한 필요")
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, vehicle.id)
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun triggerOilTestNotification() {
        if (OilNotificationHelper.hasPermission(context)) {
            OilNotificationHelper.sendSingleVehicleTestAlert(context, vehicle)
            onShowMessage("알림 발송됨")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // iOS Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "뒤로",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = companyName.ifBlank { "목록" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("detail_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "차량 삭제",
                    tint = ios.red
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Vehicle Hero Card
            item {
                val status = vehicle.statusEnum()
                IosGroupedCard {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { showVehiclePhotoSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.7f else 0.55f),
                                modifier = Modifier
                                    .size(66.dp)
                                    .testTag("hero_vehicle_photo_button")
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (vehicleBitmap != null) {
                                        Image(
                                            bitmap = vehicleBitmap.asImageBitmap(),
                                            contentDescription = "차량 대표 사진",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.58f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = "대표 사진 변경",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DirectionsCar,
                                                contentDescription = "차량 대표 사진 추가",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "대표 사진",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = ios.secondaryLabel
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = vehicle.plateNumber,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable(onClick = onOpenAdminEdit)
                                            .testTag("hero_status_badge")
                                    ) {
                                        IosStatusBadge(label = status.labelKo, color = statusColor(status))
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = "${vehicle.modelName} · ${vehicle.year}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ios.secondaryLabel
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricMiniPill(
                                label = "주행거리",
                                value = "${nf.format(vehicle.mileageKm)} km",
                                onClick = onOpenAdminEdit,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hero_mileage_button")
                            )
                            MetricMiniPill(
                                label = "주차 위치",
                                value = vehicle.parkingLocation.ifBlank { "미지정" },
                                onClick = onOpenEditParkingLocation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hero_parking_location_button")
                            )
                        }

                        val statusAudit = remember(vehicle.statusUpdatedBy, vehicle.statusUpdatedAt, vehicle.updatedAt) {
                            vehicle.formattedStatusAudit()
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            onClick = { showHistorySheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.55f else 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vehicle_status_audit_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = "수정 내역",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "수정 내역",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = statusAudit ?: "내역 보기",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (statusAudit != null) MaterialTheme.colorScheme.onSurface else ios.secondaryLabel
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = ios.secondaryLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // iOS Segmented Picker
            item {
                IosSegmentedControl(
                    options = listOf("주차 · 오일", "정비", "보험"),
                    selectedIndex = selectedTab,
                    onSelected = { selectedTab = it }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Parking Location + Photo Card
                    item {
                        IosSectionTitle("주차 위치")
                        IosGroupedCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(onClick = onOpenEditParkingLocation)
                                        .padding(vertical = 6.dp)
                                        .testTag("parking_location_row"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = "주차 위치",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = vehicle.parkingLocation.ifBlank { "위치 미지정" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                if (parkingBitmap != null) {
                                    Image(
                                        bitmap = parkingBitmap.asImageBitmap(),
                                        contentDescription = "주차 위치 사진",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                fullScreenPhoto = parkingBitmap to vehicle.parkingLocation.ifBlank { "주차 사진" }
                                            }
                                            .testTag("parking_photo_preview")
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ios.groupedSecondary.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = null,
                                                tint = ios.secondaryLabel,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "주차 사진 없음",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ios.secondaryLabel
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        onClick = { launchCamera() },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("take_parking_photo_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = "촬영",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "촬영",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = ios.groupedSecondary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("pick_parking_photo_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = "앨범",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "앨범",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Engine Oil Card
                    item {
                        val rem = vehicle.remainingOilKm()
                        val oilColor = when {
                            rem <= 0L -> ios.red
                            rem <= oilThresholdKm -> ios.orange
                            else -> ios.green
                        }
                        IosSectionTitle("엔진 오일")
                        IosGroupedCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (rem <= 0L) "${nf.format(-rem)} km 초과" else "${nf.format(rem)} km 남음",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = oilColor
                                    )
                                    Text(
                                        text = "다음 ${nf.format(vehicle.nextOilChangeKm())} km",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                        color = ios.secondaryLabel
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { vehicle.oilUsageProgress() },
                                    color = oilColor,
                                    trackColor = ios.groupedSecondary,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(Modifier.height(14.dp))

                                val presetIntervals = listOf(5000L, 8000L, 10000L, 15000L)
                                val isCustomInterval = vehicle.oilChangeIntervalKm !in presetIntervals

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "교체 주기 (km)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ios.secondaryLabel
                                    )
                                    Surface(
                                        onClick = { showCustomOilIntervalSheet = true },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (isCustomInterval) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        },
                                        modifier = Modifier.testTag("oil_interval_custom_button")
                                    ) {
                                        Text(
                                            text = if (isCustomInterval) {
                                                "직접 입력 · ${nf.format(vehicle.oilChangeIntervalKm)}"
                                            } else {
                                                "직접 입력"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = if (isCustomInterval) MonoFontFamily else MaterialTheme.typography.labelSmall.fontFamily,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = if (isCustomInterval) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    presetIntervals.forEach { interval ->
                                        val selected = vehicle.oilChangeIntervalKm == interval
                                        Surface(
                                            onClick = {
                                                onUpdateOilSettings(
                                                    vehicle.lastOilChangeKm,
                                                    interval,
                                                    vehicle.oilAlertEnabled
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else ios.groupedSecondary,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .testTag("oil_interval_$interval")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = nf.format(interval),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = MonoFontFamily,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            IosHairlineDivider(startIndent = 16.dp)

                            IosSwitchRow(
                                icon = Icons.Rounded.NotificationsActive,
                                iconTint = ios.orange,
                                title = "교체 시기 알림",
                                checked = vehicle.oilAlertEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateOilSettings(
                                        vehicle.lastOilChangeKm,
                                        vehicle.oilChangeIntervalKm,
                                        enabled
                                    )
                                },
                                testTagName = "vehicle_oil_alert_switch"
                            )

                            IosHairlineDivider(startIndent = 16.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        onUpdateOilSettings(
                                            vehicle.mileageKm,
                                            vehicle.oilChangeIntervalKm,
                                            vehicle.oilAlertEnabled
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = ios.green.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("mark_oil_changed_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OilBarrel,
                                            contentDescription = null,
                                            tint = ios.green,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "교체 완료",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ios.green
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { triggerOilTestNotification() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = ios.groupedSecondary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("test_oil_alert_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "알림 테스트",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Maintenance Tab
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IosSectionTitle("정비 이력")
                            Surface(
                                onClick = onOpenAddMaintenance,
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("add_maintenance_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "추가",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    val records = (maintenanceState as? UiState.Success)?.data.orEmpty()
                    if (records.isEmpty()) {
                        item {
                            IosGroupedCard {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "정비 이력 없음",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ios.secondaryLabel
                                    )
                                }
                            }
                        }
                    } else {
                        items(records, key = { it.id }) { record ->
                            MaintenanceRecordCard(
                                record = record,
                                onDelete = { onDeleteMaintenance(record.id) }
                            )
                        }
                    }
                }

                2 -> {
                    // Insurance Tab
                    item {
                        val dDay = computeDaysUntil(vehicle.insuranceExpiryDate)
                        val dDayColor = when {
                            dDay == null -> ios.secondaryLabel
                            dDay < 0 -> ios.red
                            dDay <= 30 -> ios.orange
                            else -> ios.green
                        }
                        IosSectionTitle("보험 정보")
                        IosGroupedCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = vehicle.insuranceCompany.ifBlank { "보험 미등록" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (dDay != null) {
                                    IosStatusBadge(
                                        label = if (dDay < 0) "만료" else "D-$dDay",
                                        color = dDayColor
                                    )
                                }
                            }
                            IosHairlineDivider()
                            IosKeyValueRow("증권 번호", vehicle.insurancePolicyNo)
                            IosHairlineDivider()
                            IosKeyValueRow("만기일", vehicle.insuranceExpiryDate, valueColor = dDayColor)
                            IosHairlineDivider()
                            IosKeyValueRow("보장 내용", vehicle.insuranceCoverage)
                        }
                        Spacer(Modifier.height(12.dp))
                        IosPrimaryButton(
                            text = "보험 정보 수정",
                            onClick = onOpenEditInsurance,
                            testTagName = "edit_insurance_button"
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("차량 삭제") },
            text = { Text("${vehicle.plateNumber} 차량을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteVehicle()
                    }
                ) {
                    Text("삭제", color = ios.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    fullScreenPhoto?.let { (bmp, label) ->
        ParkingPhotoFullScreenDialog(
            bitmap = bmp,
            location = label,
            onDismiss = { fullScreenPhoto = null }
        )
    }

    if (showHistorySheet) {
        VehicleChangeHistorySheet(
            vehicle = vehicle,
            historyState = historyState,
            onDismiss = { showHistorySheet = false }
        )
    }

    if (showCustomOilIntervalSheet) {
        CustomOilIntervalSheet(
            currentIntervalKm = vehicle.oilChangeIntervalKm,
            onDismiss = { showCustomOilIntervalSheet = false },
            onSave = { customInterval ->
                showCustomOilIntervalSheet = false
                onUpdateOilSettings(
                    vehicle.lastOilChangeKm,
                    customInterval,
                    vehicle.oilAlertEnabled
                )
            }
        )
    }

    if (showVehiclePhotoSheet) {
        VehiclePhotoSheet(
            vehicle = vehicle,
            onDismiss = { showVehiclePhotoSheet = false },
            onUpdatePhoto = onUpdateVehiclePhoto,
            onViewFullScreen = { bmp ->
                showVehiclePhotoSheet = false
                fullScreenPhoto = bmp to "${vehicle.plateNumber} · ${vehicle.modelName}"
            },
            onShowMessage = onShowMessage
        )
    }
}

@Composable
private fun MetricMiniPill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.7f else 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ios.secondaryLabel
                )
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "$label 수정",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MaintenanceRecordCard(
    record: MaintenanceRecord,
    onDelete: () -> Unit
) {
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val cat = record.categoryEnum()

    IosGroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosStatusBadge(label = cat.labelKo, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = record.serviceDate,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${nf.format(record.mileageKm)} km",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel
                    )
                    if (record.costKrw > 0L) {
                        Text(
                            text = "${nf.format(record.costKrw)}원",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (record.shopName.isNotBlank()) {
                        Text(
                            text = record.shopName,
                            style = MaterialTheme.typography.bodySmall,
                            color = ios.secondaryLabel
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "삭제",
                    tint = ios.secondaryLabel
                )
            }
        }
    }
}

fun computeDaysUntil(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val target = sdf.parse(dateStr.trim()) ?: return null
        val diffMs = target.time - Date().time
        TimeUnit.MILLISECONDS.toDays(diffMs)
    } catch (_: Exception) {
        null
    }
}
