package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.Company
import com.example.data.MaintenanceCategory
import com.example.data.Vehicle
import com.example.data.VehicleStatus
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.parseHexColor
import com.example.ui.components.statusColor
import com.example.ui.theme.CompanyAccentPresets
import com.example.ui.theme.LocalIosColors
import com.example.util.ParkingPhotoHelper
import com.example.viewmodel.OdometerScanState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyFormSheet(
    initial: Company?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (
        name: String,
        code: String,
        accentHex: String,
        backgroundPhotoPath: String?,
        backgroundPhotoBase64: String?
    ) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var selectedHex by remember { mutableStateOf(initial?.accentColorHex ?: CompanyAccentPresets.first()) }
    var bgPhotoPath by remember { mutableStateOf(initial?.backgroundPhotoPath) }
    var bgPhotoBase64 by remember { mutableStateOf(initial?.backgroundPhotoBase64) }

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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initial == null) "회사 추가" else "회사 수정",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (initial != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("삭제", color = LocalIosColors.current.red)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("회사명") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("company_name_input")
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(6) },
                label = { Text("약어 (선택)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("company_code_input")
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompanyAccentPresets.forEach { hex ->
                    val color = parseHexColor(hex)
                    val selected = hex.equals(selectedHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            PhotoPickerBlock(
                title = "배경 사진 (선택)",
                photoKey = "company_bg_${initial?.id ?: "new"}",
                photoPath = bgPhotoPath,
                photoBase64 = bgPhotoBase64,
                onPhotoChanged = { path, b64 ->
                    bgPhotoPath = path
                    bgPhotoBase64 = b64
                },
                onShowMessage = onShowMessage,
                cameraTestTag = "company_bg_camera_button",
                galleryTestTag = "company_bg_gallery_button"
            )

            Spacer(Modifier.height(20.dp))

            IosPrimaryButton(
                text = "저장",
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onSave(
                        name.trim(),
                        code.trim(),
                        selectedHex,
                        bgPhotoPath,
                        bgPhotoBase64
                    )
                },
                testTagName = "company_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleFormSheet(
    companies: List<Company>,
    initialCompanyId: String,
    onDismiss: () -> Unit,
    onSave: (
        companyId: String,
        plateNumber: String,
        modelName: String,
        year: Long,
        parkingLocation: String,
        mileageKm: Long,
        status: VehicleStatus,
        lastOilChangeKm: Long,
        oilIntervalKm: Long,
        oilAlertEnabled: Boolean,
        insuranceCompany: String,
        insurancePolicyNo: String,
        insuranceExpiryDate: String,
        insuranceCoverage: String,
        vehiclePhotoPath: String?,
        vehiclePhotoBase64: String?
    ) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var companyId by remember { mutableStateOf(initialCompanyId.ifBlank { companies.firstOrNull()?.id.orEmpty() }) }
    var plate by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2025") }
    var parking by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("0") }
    var status by remember { mutableStateOf(VehicleStatus.ACTIVE) }
    var insCompany by remember { mutableStateOf("") }
    var insPolicy by remember { mutableStateOf("") }
    var insExpiry by remember { mutableStateOf("") }
    var insCoverage by remember { mutableStateOf("") }
    var vehiclePhotoPath by remember { mutableStateOf<String?>(null) }
    var vehiclePhotoBase64 by remember { mutableStateOf<String?>(null) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "차량 등록",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            if (companies.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    companies.forEach { comp ->
                        val selected = comp.id == companyId
                        Surface(
                            onClick = { companyId = comp.id },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else LocalIosColors.current.groupedSecondary
                        ) {
                            Text(
                                text = comp.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it },
                label = { Text("차량 번호 (예: 123가 4567)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("vehicle_plate_input")
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("차종 (예: 제네시스 G80)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("vehicle_model_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("연식") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("주행거리 (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f)
                )
            }

            OutlinedTextField(
                value = parking,
                onValueChange = { parking = it },
                label = { Text("주차 위치 (예: 본사 B2 A-14)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("vehicle_parking_input")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleStatus.entries.forEach { st ->
                    val selected = st == status
                    val tint = statusColor(st)
                    Surface(
                        onClick = { status = st },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) tint else tint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = st.labelKo,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Color.White else tint,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = insCompany,
                onValueChange = { insCompany = it },
                label = { Text("보험사 (선택)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = insPolicy,
                    onValueChange = { insPolicy = it },
                    label = { Text("증권 번호") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = insExpiry,
                    onValueChange = { insExpiry = it },
                    label = { Text("만기일 (YYYY-MM-DD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = insCoverage,
                onValueChange = { insCoverage = it },
                label = { Text("보장 내용 (선택)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            PhotoPickerBlock(
                title = "차량 대표 사진 (선택)",
                photoKey = "vehicle_profile_new",
                photoPath = vehiclePhotoPath,
                photoBase64 = vehiclePhotoBase64,
                onPhotoChanged = { path, b64 ->
                    vehiclePhotoPath = path
                    vehiclePhotoBase64 = b64
                },
                onShowMessage = onShowMessage,
                cameraTestTag = "vehicle_photo_camera_button",
                galleryTestTag = "vehicle_photo_gallery_button"
            )

            Spacer(Modifier.height(6.dp))

            val mileageVal = mileage.toLongOrNull() ?: 0L
            IosPrimaryButton(
                text = "등록",
                enabled = companyId.isNotBlank() && plate.trim().length >= 2 && model.trim().isNotEmpty(),
                onClick = {
                    onSave(
                        companyId,
                        plate.trim(),
                        model.trim(),
                        year.toLongOrNull() ?: 2025L,
                        parking.trim(),
                        mileageVal,
                        status,
                        mileageVal,
                        10000L,
                        true,
                        insCompany.trim(),
                        insPolicy.trim(),
                        insExpiry.trim(),
                        insCoverage.trim(),
                        vehiclePhotoPath,
                        vehiclePhotoBase64
                    )
                },
                testTagName = "vehicle_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminMileageStatusSheet(
    vehicle: Vehicle,
    scanState: OdometerScanState,
    onDismiss: () -> Unit,
    onSave: (mileageKm: Long, status: VehicleStatus) -> Unit,
    onScanOdometerFile: (file: File, status: VehicleStatus) -> Unit,
    onScanOdometerUri: (uri: Uri, status: VehicleStatus) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val ios = LocalIosColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mileage by remember(vehicle.id, vehicle.mileageKm) { mutableStateOf(vehicle.mileageKm.toString()) }
    var status by remember(vehicle.id, vehicle.status) { mutableStateOf(vehicle.statusEnum()) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    val isScanning = scanState is OdometerScanState.Scanning

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null && file.exists()) {
            onScanOdometerFile(file, status)
        } else if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, "odometer_${vehicle.id}")
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
            onScanOdometerUri(uri, status)
        }
    }

    fun launchOdometerCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, "odometer_${vehicle.id}")
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isScanning) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "${vehicle.plateNumber} · ${vehicle.modelName}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = mileage,
                onValueChange = { mileage = it.filter { c -> c.isDigit() }.take(7) },
                label = { Text("주행거리 (km)") },
                singleLine = true,
                enabled = !isScanning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_mileage_input")
            )

            // Odometer Auto-Recognition Block
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.55f else 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "계기판 자동 인식",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ios.secondaryLabel
                    )

                    if (isScanning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "계기판 인식 중...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = { launchOdometerCamera() },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("odometer_camera_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = "계기판 촬영",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "계기판 촬영",
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
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("odometer_gallery_button")
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

                    if (scanState is OdometerScanState.Error) {
                        Text(
                            text = scanState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = ios.red
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleStatus.entries.forEach { st ->
                    val selected = st == status
                    val tint = statusColor(st)
                    Surface(
                        onClick = { if (!isScanning) status = st },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) tint else tint.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("admin_status_${st.name}")
                    ) {
                        Text(
                            text = st.labelKo,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else tint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            IosPrimaryButton(
                text = "저장",
                enabled = !isScanning && mileage.toLongOrNull() != null,
                onClick = {
                    val km = mileage.toLongOrNull() ?: vehicle.mileageKm
                    onSave(km, status)
                },
                testTagName = "admin_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceFormSheet(
    currentMileageKm: Long,
    onDismiss: () -> Unit,
    onSave: (
        date: String,
        category: MaintenanceCategory,
        title: String,
        mileageKm: Long,
        costKrw: Long,
        shopName: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var date by remember { mutableStateOf(today) }
    var category by remember { mutableStateOf(MaintenanceCategory.ENGINE_OIL) }
    var title by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf(currentMileageKm.toString()) }
    var cost by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("") }

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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "정비 이력 추가",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaintenanceCategory.entries.forEach { cat ->
                    val selected = cat == category
                    Surface(
                        onClick = { category = cat },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else LocalIosColors.current.groupedSecondary
                    ) {
                        Text(
                            text = cat.labelKo,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("정비 내용") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("maint_title_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("정비일") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("주행거리") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("비용 (원)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shop,
                    onValueChange = { shop = it },
                    label = { Text("정비소") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            IosPrimaryButton(
                text = "저장",
                enabled = title.trim().isNotEmpty() && date.trim().isNotEmpty(),
                onClick = {
                    onSave(
                        date.trim(),
                        category,
                        title.trim(),
                        mileage.toLongOrNull() ?: currentMileageKm,
                        cost.toLongOrNull() ?: 0L,
                        shop.trim()
                    )
                },
                testTagName = "maint_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsuranceFormSheet(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onSave: (company: String, policyNo: String, expiry: String, coverage: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var company by remember { mutableStateOf(vehicle.insuranceCompany) }
    var policyNo by remember { mutableStateOf(vehicle.insurancePolicyNo) }
    var expiry by remember { mutableStateOf(vehicle.insuranceExpiryDate) }
    var coverage by remember { mutableStateOf(vehicle.insuranceCoverage) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "보험 정보",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("보험사") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = policyNo,
                onValueChange = { policyNo = it },
                label = { Text("증권 번호") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = it },
                label = { Text("만기일 (YYYY-MM-DD)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = coverage,
                onValueChange = { coverage = it },
                label = { Text("보장 내용") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            IosPrimaryButton(
                text = "저장",
                onClick = { onSave(company.trim(), policyNo.trim(), expiry.trim(), coverage.trim()) },
                testTagName = "insurance_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingLocationSheet(
    initialLocation: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var location by remember { mutableStateOf(initialLocation) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "주차 위치",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("주차 구역 (예: 본사 B2 A-14)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("parking_location_input")
            )
            IosPrimaryButton(
                text = "저장",
                onClick = { onSave(location.trim()) },
                testTagName = "parking_location_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknameChangeSheet(
    currentNickname: String,
    onDismiss: () -> Unit,
    onSave: (newNickname: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nickname by remember { mutableStateOf(currentNickname) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "닉네임 변경",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(24) },
                label = { Text("닉네임") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("nickname_edit_input")
            )
            IosPrimaryButton(
                text = "저장",
                enabled = nickname.trim().isNotEmpty(),
                onClick = { onSave(nickname.trim()) },
                testTagName = "nickname_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOilIntervalSheet(
    currentIntervalKm: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var intervalText by remember(currentIntervalKm) { mutableStateOf(currentIntervalKm.toString()) }
    val parsed = intervalText.toLongOrNull()
    val isValid = parsed != null && parsed in 1000L..50_000L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "교체 주기 직접 입력",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("교체 주기 (1,000 ~ 50,000 km)") },
                singleLine = true,
                isError = intervalText.isNotEmpty() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_oil_interval_input")
            )
            IosPrimaryButton(
                text = "저장",
                enabled = isValid,
                onClick = { if (parsed != null) onSave(parsed) },
                testTagName = "custom_oil_interval_save_button"
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclePhotoSheet(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onUpdatePhoto: (photoPath: String?, photoBase64: String?) -> Unit,
    onViewFullScreen: (Bitmap) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ios = LocalIosColors.current
    val bitmap = remember(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "차량 대표 사진",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (bitmap != null) {
                    TextButton(
                        onClick = {
                            onUpdatePhoto(null, null)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("vehicle_photo_remove_button")
                    ) {
                        Text("삭제", color = ios.red)
                    }
                }
            }

            PhotoPickerBlock(
                title = "${vehicle.plateNumber} · ${vehicle.modelName}",
                photoKey = "vehicle_profile_${vehicle.id}",
                photoPath = vehicle.vehiclePhotoPath,
                photoBase64 = vehicle.vehiclePhotoBase64,
                previewHeightDp = 180,
                onPreviewClick = if (bitmap != null) {
                    { onViewFullScreen(bitmap) }
                } else null,
                onPhotoChanged = { path, b64 ->
                    onUpdatePhoto(path, b64)
                    onDismiss()
                },
                onShowMessage = onShowMessage,
                cameraTestTag = "detail_vehicle_photo_camera_button",
                galleryTestTag = "detail_vehicle_photo_gallery_button",
                showInlineClear = false
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PhotoPickerBlock(
    title: String,
    photoKey: String,
    photoPath: String?,
    photoBase64: String?,
    onPhotoChanged: (path: String?, base64: String?) -> Unit,
    onShowMessage: (String) -> Unit,
    cameraTestTag: String,
    galleryTestTag: String,
    previewHeightDp: Int = 120,
    onPreviewClick: (() -> Unit)? = null,
    showInlineClear: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ios = LocalIosColors.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val previewBitmap = remember(photoPath, photoBase64) {
        ParkingPhotoHelper.loadBitmap(photoPath, photoBase64)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.exists()) {
            scope.launch {
                val saved = ParkingPhotoHelper.processCapturedFile(file)
                onPhotoChanged(saved.localPath, saved.thumbnailBase64)
            }
        } else if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, photoKey)
            pendingFile = file
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
                val saved = ParkingPhotoHelper.copyFromUri(context, photoKey, uri)
                if (saved != null) {
                    onPhotoChanged(saved.localPath, saved.thumbnailBase64)
                } else {
                    onShowMessage("사진 저장 실패")
                }
            }
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (file, uri) = ParkingPhotoHelper.createCaptureUri(context, photoKey)
            pendingFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.55f else 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ios.secondaryLabel
                )
                if (showInlineClear && previewBitmap != null) {
                    Surface(
                        onClick = { onPhotoChanged(null, null) },
                        shape = RoundedCornerShape(999.dp),
                        color = ios.red.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "사진 제거",
                                tint = ios.red,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "제거",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ios.red
                            )
                        }
                    }
                }
            }

            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeightDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (onPreviewClick != null) Modifier.clickable(onClick = onPreviewClick)
                            else Modifier
                        )
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))
                                )
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = ios.secondaryLabel,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "등록된 사진 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = ios.secondaryLabel
                        )
                    }
                }
            }

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
                        .height(44.dp)
                        .testTag(cameraTestTag)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "촬영",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag(galleryTestTag)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = "앨범",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
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

@Composable
fun ParkingPhotoFullScreenDialog(
    bitmap: Bitmap,
    location: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "주차 사진 전체 보기",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = location.ifBlank { "주차 사진" },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "닫기",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
