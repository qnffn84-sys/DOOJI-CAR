package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppReleaseConfig
import com.example.data.ChangeLogType
import com.example.data.Company
import com.example.data.FleetRepository
import com.example.data.LocalPreferencesRepository
import com.example.data.MaintenanceCategory
import com.example.data.MaintenanceRecord
import com.example.data.ThemePreference
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.Vehicle
import com.example.data.VehicleChangeLog
import com.example.data.VehicleStatus
import com.example.util.OdometerRecognizer
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OdometerScanState {
    data object Idle : OdometerScanState
    data object Scanning : OdometerScanState
    data class Error(val message: String) : OdometerScanState
}

sealed interface FleetEvent {
    data class Toast(val message: String) : FleetEvent
    data class CheckOilAlerts(
        val vehicles: List<Vehicle>,
        val globalEnabled: Boolean,
        val thresholdKm: Long
    ) : FleetEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class FleetViewModel(
    private val repository: FleetRepository,
    private val localPrefs: LocalPreferencesRepository,
    val currentUserId: String,
    private val googleEmail: String,
    private val googleDisplayName: String
) : ViewModel() {

    private val _events = MutableSharedFlow<FleetEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FleetEvent> = _events.asSharedFlow()

    private val _isBiometricLocked = MutableStateFlow(false)
    val isBiometricLocked: StateFlow<Boolean> = _isBiometricLocked.asStateFlow()

    private var initialBiometricCheckDone = false

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeVehicleIdForMaintenance = MutableStateFlow<String?>(null)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _odometerScanState = MutableStateFlow<OdometerScanState>(OdometerScanState.Idle)
    val odometerScanState: StateFlow<OdometerScanState> = _odometerScanState.asStateFlow()

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("UserProfile")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val companiesState: StateFlow<UiState<List<Company>>> =
        repository.observeCompanies(currentUserId)
            .toUiStateFlow("Companies")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val vehiclesState: StateFlow<UiState<List<Vehicle>>> =
        repository.observeVehicles(currentUserId)
            .toUiStateFlow("Vehicles")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val maintenanceState: StateFlow<UiState<List<MaintenanceRecord>>> =
        _activeVehicleIdForMaintenance
            .flatMapLatest { vehicleId ->
                if (vehicleId.isNullOrBlank()) {
                    flowOf<UiState<List<MaintenanceRecord>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeMaintenance(currentUserId, vehicleId)
                        .toUiStateFlow("Maintenance")
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val historyState: StateFlow<UiState<List<VehicleChangeLog>>> =
        _activeVehicleIdForMaintenance
            .flatMapLatest { vehicleId ->
                if (vehicleId.isNullOrBlank()) {
                    flowOf<UiState<List<VehicleChangeLog>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeVehicleHistory(currentUserId, vehicleId)
                        .toUiStateFlow("VehicleHistory")
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val releaseConfigState: StateFlow<UiState<AppReleaseConfig?>> =
        repository.observeAppReleaseConfig(currentUserId)
            .toUiStateFlow("AppReleaseConfig")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.migrateLegacyPersonalDataIfNeeded(currentUserId)
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(label: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "데이터를 불러올 수 없습니다"))
            }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun observeMaintenanceFor(vehicleId: String?) {
        _activeVehicleIdForMaintenance.value = vehicleId
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearOdometerScanState() {
        _odometerScanState.value = OdometerScanState.Idle
    }

    // --- Google Profile Nickname & Biometric Lock ---

    fun syncInitialBiometricLock(profile: UserProfile?) {
        if (!initialBiometricCheckDone && profile != null) {
            initialBiometricCheckDone = true
            _isBiometricLocked.value = profile.biometricEnabled
        }
    }

    fun registerProfileWithNickname(
        nickname: String,
        enableBiometric: Boolean,
        currentTheme: ThemePreference
    ) {
        val cleanNick = nickname.trim()
        if (cleanNick.isEmpty() || cleanNick.length > 24) {
            _authError.value = "닉네임은 1~24자"
            return
        }
        val existingProfile = (profileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isSubmitting.value = true
            _authError.value = null
            val result = if (existingProfile == null) {
                repository.createUserProfile(
                    userId = currentUserId,
                    nickname = cleanNick,
                    email = googleEmail,
                    biometricEnabled = enableBiometric,
                    darkMode = currentTheme
                )
            } else {
                repository.updateNickname(currentUserId, cleanNick).also { nickRes ->
                    if (nickRes.isSuccess && existingProfile.biometricEnabled != enableBiometric) {
                        repository.updateUserPreferences(
                            userId = currentUserId,
                            biometricEnabled = enableBiometric,
                            darkMode = existingProfile.themePreference(),
                            oilAlertEnabled = existingProfile.oilAlertEnabled,
                            oilAlertThresholdKm = existingProfile.oilAlertThresholdKm
                        )
                    }
                }
            }
            result.onSuccess {
                initialBiometricCheckDone = true
                _isBiometricLocked.value = false
                _events.tryEmit(FleetEvent.Toast("${cleanNick}님 환영합니다"))
            }.onFailure { err ->
                _authError.value = err.localizedMessage ?: "등록 실패"
            }
            _isSubmitting.value = false
        }
    }

    fun unlockWithBiometric() {
        _authError.value = null
        _isBiometricLocked.value = false
    }

    fun lockWorkspace() {
        _isBiometricLocked.value = true
    }

    fun updateNickname(newNickname: String) {
        val cleanNick = newNickname.trim()
        if (cleanNick.isEmpty() || cleanNick.length > 24) {
            _events.tryEmit(FleetEvent.Toast("닉네임은 1~24자"))
            return
        }
        viewModelScope.launch {
            repository.updateNickname(currentUserId, cleanNick)
                .onSuccess { _events.tryEmit(FleetEvent.Toast("닉네임 변경됨")) }
                .onFailure { _events.tryEmit(FleetEvent.Toast("변경 실패")) }
        }
    }

    private fun currentEditorNickname(): String {
        val profile = (profileState.value as? UiState.Success)?.data
        val fromProfile = profile?.nickname?.trim().orEmpty()
        if (fromProfile.isNotEmpty()) return fromProfile
        val fromWorkspace = profile?.workspaceId?.trim().orEmpty()
        if (fromWorkspace.isNotEmpty()) return fromWorkspace
        val fromGoogle = googleDisplayName.trim()
        if (fromGoogle.isNotEmpty()) return fromGoogle.take(24)
        return googleEmail.substringBefore("@").ifBlank { "사용자" }.take(24)
    }

    fun updatePreferences(
        biometricEnabled: Boolean,
        darkMode: ThemePreference,
        oilAlertEnabled: Boolean,
        oilAlertThresholdKm: Long
    ) {
        viewModelScope.launch {
            localPrefs.setThemePreference(darkMode)
            val profile = (profileState.value as? UiState.Success)?.data
            if (profile != null) {
                repository.updateUserPreferences(
                    userId = currentUserId,
                    biometricEnabled = biometricEnabled,
                    darkMode = darkMode,
                    oilAlertEnabled = oilAlertEnabled,
                    oilAlertThresholdKm = oilAlertThresholdKm
                ).onFailure {
                    _events.tryEmit(FleetEvent.Toast("설정 저장 실패"))
                }
            }
        }
    }

    // --- Company CRUD ---

    fun addCompany(
        name: String,
        code: String,
        accentColorHex: String,
        backgroundPhotoPath: String? = null,
        backgroundPhotoBase64: String? = null
    ) {
        viewModelScope.launch {
            repository.addCompany(
                userId = currentUserId,
                name = name,
                code = code,
                accentColorHex = accentColorHex,
                backgroundPhotoPath = backgroundPhotoPath,
                backgroundPhotoBase64 = backgroundPhotoBase64
            )
                .onSuccess { _events.tryEmit(FleetEvent.Toast("회사 추가됨")) }
                .onFailure { _events.tryEmit(FleetEvent.Toast(it.localizedMessage ?: "추가 실패")) }
        }
    }

    fun updateCompany(
        companyId: String,
        name: String,
        code: String,
        accentColorHex: String,
        backgroundPhotoPath: String? = null,
        backgroundPhotoBase64: String? = null
    ) {
        viewModelScope.launch {
            repository.updateCompany(
                userId = currentUserId,
                companyId = companyId,
                name = name,
                code = code,
                accentColorHex = accentColorHex,
                backgroundPhotoPath = backgroundPhotoPath,
                backgroundPhotoBase64 = backgroundPhotoBase64
            )
                .onSuccess { _events.tryEmit(FleetEvent.Toast("회사 수정됨")) }
                .onFailure { _events.tryEmit(FleetEvent.Toast(it.localizedMessage ?: "수정 실패")) }
        }
    }

    fun deleteCompany(companyId: String) {
        viewModelScope.launch {
            repository.deleteCompany(currentUserId, companyId)
                .onSuccess { _events.tryEmit(FleetEvent.Toast("회사 삭제됨")) }
                .onFailure { _events.tryEmit(FleetEvent.Toast("삭제 실패")) }
        }
    }

    // --- Vehicle CRUD ---

    fun addVehicle(
        companyId: String,
        plateNumber: String,
        modelName: String,
        year: Long,
        parkingLocation: String,
        mileageKm: Long,
        status: VehicleStatus,
        lastOilChangeKm: Long,
        oilChangeIntervalKm: Long,
        oilAlertEnabled: Boolean,
        insuranceCompany: String,
        insurancePolicyNo: String,
        insuranceExpiryDate: String,
        insuranceCoverage: String,
        vehiclePhotoPath: String? = null,
        vehiclePhotoBase64: String? = null
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            repository.addVehicle(
                userId = currentUserId,
                companyId = companyId,
                plateNumber = plateNumber,
                modelName = modelName,
                year = year,
                parkingLocation = parkingLocation,
                mileageKm = mileageKm,
                status = status,
                createdByNickname = editor,
                lastOilChangeKm = lastOilChangeKm,
                oilChangeIntervalKm = oilChangeIntervalKm,
                oilAlertEnabled = oilAlertEnabled,
                insuranceCompany = insuranceCompany,
                insurancePolicyNo = insurancePolicyNo,
                insuranceExpiryDate = insuranceExpiryDate,
                insuranceCoverage = insuranceCoverage,
                vehiclePhotoPath = vehiclePhotoPath,
                vehiclePhotoBase64 = vehiclePhotoBase64
            ).onSuccess { vehicleId ->
                val nf = NumberFormat.getNumberInstance(Locale.KOREA)
                val initialDetails = buildList {
                    add("상태: ${status.labelKo}")
                    add("주행거리: ${nf.format(mileageKm)} km")
                    if (parkingLocation.isNotBlank()) {
                        add("주차 위치: ${parkingLocation.trim()}")
                    }
                }
                repository.addVehicleChangeLog(
                    userId = currentUserId,
                    vehicleId = vehicleId,
                    editorNickname = editor,
                    changeType = ChangeLogType.CREATED,
                    changes = initialDetails
                )
                _events.tryEmit(FleetEvent.Toast("차량 등록됨"))
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast(it.localizedMessage ?: "등록 실패"))
            }
        }
    }

    fun updateVehicleAdmin(
        vehicle: Vehicle,
        newMileageKm: Long,
        newStatus: VehicleStatus,
        fromOdometerScan: Boolean = false
    ) {
        viewModelScope.launch {
            applyVehicleAdminUpdate(vehicle, newMileageKm, newStatus, fromOdometerScan)
        }
    }

    fun scanAndApplyOdometerFromFile(
        vehicle: Vehicle,
        status: VehicleStatus,
        file: File,
        onApplied: () -> Unit
    ) {
        if (_odometerScanState.value is OdometerScanState.Scanning) return
        viewModelScope.launch {
            _odometerScanState.value = OdometerScanState.Scanning
            val result = try {
                OdometerRecognizer.recognizeFromFile(file)
            } finally {
                runCatching { if (file.exists()) file.delete() }
            }
            handleOdometerScanResult(vehicle, status, result, onApplied)
        }
    }

    fun scanAndApplyOdometerFromUri(
        context: Context,
        vehicle: Vehicle,
        status: VehicleStatus,
        uri: Uri,
        onApplied: () -> Unit
    ) {
        if (_odometerScanState.value is OdometerScanState.Scanning) return
        viewModelScope.launch {
            _odometerScanState.value = OdometerScanState.Scanning
            val result = OdometerRecognizer.recognizeFromUri(context.applicationContext, uri)
            handleOdometerScanResult(vehicle, status, result, onApplied)
        }
    }

    private suspend fun handleOdometerScanResult(
        vehicle: Vehicle,
        status: VehicleStatus,
        result: Result<Long>,
        onApplied: () -> Unit
    ) {
        result.onSuccess { recognizedKm ->
            val saved = applyVehicleAdminUpdate(
                vehicle = vehicle,
                newMileageKm = recognizedKm,
                newStatus = status,
                fromOdometerScan = true
            )
            if (saved) {
                _odometerScanState.value = OdometerScanState.Idle
                onApplied()
            } else {
                _odometerScanState.value = OdometerScanState.Error("주행거리 저장에 실패했습니다.")
            }
        }.onFailure { err ->
            _odometerScanState.value = OdometerScanState.Error(
                err.localizedMessage ?: "계기판 인식에 실패했습니다."
            )
        }
    }

    private suspend fun applyVehicleAdminUpdate(
        vehicle: Vehicle,
        newMileageKm: Long,
        newStatus: VehicleStatus,
        fromOdometerScan: Boolean
    ): Boolean {
        val editor = currentEditorNickname()
        val nf = NumberFormat.getNumberInstance(Locale.KOREA)
        val mileageLabel = if (fromOdometerScan) "주행거리 (계기판 인식)" else "주행거리"
        val diffs = buildList {
            if (vehicle.statusEnum() != newStatus) {
                add("상태: ${vehicle.statusEnum().labelKo} → ${newStatus.labelKo}")
            }
            if (vehicle.mileageKm != newMileageKm) {
                add("$mileageLabel: ${nf.format(vehicle.mileageKm)} km → ${nf.format(newMileageKm)} km")
            }
            if (isEmpty()) {
                val prefix = if (fromOdometerScan) "계기판 확인" else "상태 확인"
                add("$prefix: ${newStatus.labelKo} · ${nf.format(newMileageKm)} km")
            }
        }
        val res = repository.updateVehicleAdmin(
            userId = currentUserId,
            vehicleId = vehicle.id,
            mileageKm = newMileageKm,
            status = newStatus,
            updatedByNickname = editor
        )
        return res.fold(
            onSuccess = {
                repository.addVehicleChangeLog(
                    userId = currentUserId,
                    vehicleId = vehicle.id,
                    editorNickname = editor,
                    changeType = ChangeLogType.STATUS,
                    changes = diffs
                )
                val toastText = if (fromOdometerScan) {
                    "계기판 인식 · ${nf.format(newMileageKm)} km 반영됨 ($editor)"
                } else {
                    "${vehicle.plateNumber} 수정됨 ($editor)"
                }
                _events.tryEmit(FleetEvent.Toast(toastText))
                val profile = (profileState.value as? UiState.Success)?.data
                val updatedVehicle = vehicle.copy(mileageKm = newMileageKm, status = newStatus.raw)
                val threshold = profile?.oilAlertThresholdKm ?: 1000L
                val enabled = profile?.oilAlertEnabled ?: true
                if (updatedVehicle.needsOilAttention(threshold)) {
                    _events.tryEmit(
                        FleetEvent.CheckOilAlerts(
                            vehicles = listOf(updatedVehicle),
                            globalEnabled = enabled,
                            thresholdKm = threshold
                        )
                    )
                }
                true
            },
            onFailure = {
                _events.tryEmit(FleetEvent.Toast("업데이트 실패"))
                false
            }
        )
    }

    fun updateVehicleParking(
        vehicle: Vehicle,
        parkingLocation: String,
        parkingPhotoPath: String?,
        parkingPhotoBase64: String?
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            val oldLoc = vehicle.parkingLocation.trim()
            val newLoc = parkingLocation.trim()
            val photoChanged = parkingPhotoPath != vehicle.parkingPhotoPath ||
                parkingPhotoBase64 != vehicle.parkingPhotoBase64
            val diffs = buildList {
                if (oldLoc != newLoc) {
                    add("주차 위치: ${oldLoc.ifBlank { "미지정" }} → ${newLoc.ifBlank { "미지정" }}")
                }
                if (photoChanged) {
                    add("주차 사진: ${if (vehicle.hasParkingPhoto()) "사진 변경" else "사진 등록"}")
                }
                if (isEmpty()) {
                    add("주차 위치: ${newLoc.ifBlank { "미지정" }}")
                }
            }
            repository.updateVehicleParking(
                userId = currentUserId,
                vehicleId = vehicle.id,
                parkingLocation = parkingLocation,
                parkingPhotoPath = parkingPhotoPath,
                parkingPhotoBase64 = parkingPhotoBase64,
                updatedByNickname = editor
            ).onSuccess {
                repository.addVehicleChangeLog(
                    userId = currentUserId,
                    vehicleId = vehicle.id,
                    editorNickname = editor,
                    changeType = ChangeLogType.PARKING,
                    changes = diffs
                )
                _events.tryEmit(FleetEvent.Toast("주차 정보 저장됨"))
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast("저장 실패"))
            }
        }
    }

    fun updateVehiclePhoto(
        vehicle: Vehicle,
        vehiclePhotoPath: String?,
        vehiclePhotoBase64: String?
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            val hadPhoto = vehicle.hasVehiclePhoto()
            val hasNewPhoto = !vehiclePhotoPath.isNullOrBlank() || !vehiclePhotoBase64.isNullOrBlank()
            val detail = when {
                !hasNewPhoto -> "대표 사진: 사진 삭제"
                hadPhoto -> "대표 사진: 사진 변경"
                else -> "대표 사진: 사진 등록"
            }
            repository.updateVehiclePhoto(
                userId = currentUserId,
                vehicleId = vehicle.id,
                vehiclePhotoPath = vehiclePhotoPath,
                vehiclePhotoBase64 = vehiclePhotoBase64,
                updatedByNickname = editor
            ).onSuccess {
                repository.addVehicleChangeLog(
                    userId = currentUserId,
                    vehicleId = vehicle.id,
                    editorNickname = editor,
                    changeType = ChangeLogType.STATUS,
                    changes = listOf(detail)
                )
                _events.tryEmit(
                    FleetEvent.Toast(if (hasNewPhoto) "대표 사진 저장됨" else "대표 사진 삭제됨")
                )
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast("저장 실패"))
            }
        }
    }

    fun updateVehicleOilSettings(
        vehicle: Vehicle,
        lastOilChangeKm: Long,
        oilChangeIntervalKm: Long,
        oilAlertEnabled: Boolean
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            val nf = NumberFormat.getNumberInstance(Locale.KOREA)
            val diffs = buildList {
                if (vehicle.lastOilChangeKm != lastOilChangeKm) {
                    add("오일 교체 시점: ${nf.format(vehicle.lastOilChangeKm)} km → ${nf.format(lastOilChangeKm)} km")
                }
                if (vehicle.oilChangeIntervalKm != oilChangeIntervalKm) {
                    add("교체 주기: ${nf.format(vehicle.oilChangeIntervalKm)} km → ${nf.format(oilChangeIntervalKm)} km")
                }
                if (vehicle.oilAlertEnabled != oilAlertEnabled) {
                    add("오일 알림: ${if (vehicle.oilAlertEnabled) "켜짐" else "꺼짐"} → ${if (oilAlertEnabled) "켜짐" else "꺼짐"}")
                }
            }
            repository.updateVehicleOilSettings(
                userId = currentUserId,
                vehicleId = vehicle.id,
                lastOilChangeKm = lastOilChangeKm,
                oilChangeIntervalKm = oilChangeIntervalKm,
                oilAlertEnabled = oilAlertEnabled,
                updatedByNickname = editor
            ).onSuccess {
                if (diffs.isNotEmpty()) {
                    repository.addVehicleChangeLog(
                        userId = currentUserId,
                        vehicleId = vehicle.id,
                        editorNickname = editor,
                        changeType = ChangeLogType.OIL,
                        changes = diffs
                    )
                }
                _events.tryEmit(FleetEvent.Toast("오일 설정 저장됨"))
                val profile = (profileState.value as? UiState.Success)?.data
                val updated = vehicle.copy(
                    lastOilChangeKm = lastOilChangeKm,
                    oilChangeIntervalKm = oilChangeIntervalKm,
                    oilAlertEnabled = oilAlertEnabled
                )
                val threshold = profile?.oilAlertThresholdKm ?: 1000L
                val enabled = profile?.oilAlertEnabled ?: true
                if (updated.needsOilAttention(threshold)) {
                    _events.tryEmit(
                        FleetEvent.CheckOilAlerts(
                            vehicles = listOf(updated),
                            globalEnabled = enabled,
                            thresholdKm = threshold
                        )
                    )
                }
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast("저장 실패"))
            }
        }
    }

    fun updateVehicleInsurance(
        vehicle: Vehicle,
        insuranceCompany: String,
        insurancePolicyNo: String,
        insuranceExpiryDate: String,
        insuranceCoverage: String
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            val diffs = buildList {
                if (vehicle.insuranceCompany.trim() != insuranceCompany.trim()) {
                    add("보험사: ${vehicle.insuranceCompany.ifBlank { "미등록" }} → ${insuranceCompany.ifBlank { "미등록" }}")
                }
                if (vehicle.insurancePolicyNo.trim() != insurancePolicyNo.trim()) {
                    add("증권 번호: ${vehicle.insurancePolicyNo.ifBlank { "-" }} → ${insurancePolicyNo.ifBlank { "-" }}")
                }
                if (vehicle.insuranceExpiryDate.trim() != insuranceExpiryDate.trim()) {
                    add("만기일: ${vehicle.insuranceExpiryDate.ifBlank { "-" }} → ${insuranceExpiryDate.ifBlank { "-" }}")
                }
                if (vehicle.insuranceCoverage.trim() != insuranceCoverage.trim()) {
                    add("보장 내용: ${vehicle.insuranceCoverage.ifBlank { "-" }} → ${insuranceCoverage.ifBlank { "-" }}")
                }
            }
            repository.updateVehicleInsurance(
                userId = currentUserId,
                vehicleId = vehicle.id,
                insuranceCompany = insuranceCompany,
                insurancePolicyNo = insurancePolicyNo,
                insuranceExpiryDate = insuranceExpiryDate,
                insuranceCoverage = insuranceCoverage,
                updatedByNickname = editor
            ).onSuccess {
                if (diffs.isNotEmpty()) {
                    repository.addVehicleChangeLog(
                        userId = currentUserId,
                        vehicleId = vehicle.id,
                        editorNickname = editor,
                        changeType = ChangeLogType.INSURANCE,
                        changes = diffs
                    )
                }
                _events.tryEmit(FleetEvent.Toast("보험 정보 저장됨"))
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast("저장 실패"))
            }
        }
    }

    fun deleteVehicle(vehicleId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteVehicle(currentUserId, vehicleId)
                .onSuccess {
                    _events.tryEmit(FleetEvent.Toast("차량 삭제됨"))
                    onDeleted()
                }
                .onFailure { _events.tryEmit(FleetEvent.Toast("삭제 실패")) }
        }
    }

    // --- Maintenance Records ---

    fun addMaintenanceRecord(
        vehicle: Vehicle,
        serviceDate: String,
        category: MaintenanceCategory,
        title: String,
        mileageKm: Long,
        costKrw: Long,
        shopName: String
    ) {
        viewModelScope.launch {
            val editor = currentEditorNickname()
            repository.addMaintenanceRecord(
                userId = currentUserId,
                vehicleId = vehicle.id,
                serviceDate = serviceDate,
                category = category,
                title = title,
                mileageKm = mileageKm,
                costKrw = costKrw,
                shopName = shopName
            ).onSuccess {
                if (category == MaintenanceCategory.ENGINE_OIL) {
                    val nf = NumberFormat.getNumberInstance(Locale.KOREA)
                    repository.updateVehicleOilSettings(
                        userId = currentUserId,
                        vehicleId = vehicle.id,
                        lastOilChangeKm = mileageKm,
                        oilChangeIntervalKm = vehicle.oilChangeIntervalKm,
                        oilAlertEnabled = vehicle.oilAlertEnabled,
                        updatedByNickname = editor
                    )
                    if (vehicle.lastOilChangeKm != mileageKm) {
                        repository.addVehicleChangeLog(
                            userId = currentUserId,
                            vehicleId = vehicle.id,
                            editorNickname = editor,
                            changeType = ChangeLogType.OIL,
                            changes = listOf("오일 교체 시점: ${nf.format(vehicle.lastOilChangeKm)} km → ${nf.format(mileageKm)} km")
                        )
                    }
                }
                _events.tryEmit(FleetEvent.Toast("정비 이력 추가됨"))
            }.onFailure {
                _events.tryEmit(FleetEvent.Toast(it.localizedMessage ?: "추가 실패"))
            }
        }
    }

    fun deleteMaintenanceRecord(vehicleId: String, recordId: String) {
        viewModelScope.launch {
            repository.deleteMaintenanceRecord(currentUserId, vehicleId, recordId)
                .onSuccess { _events.tryEmit(FleetEvent.Toast("이력 삭제됨")) }
                .onFailure { _events.tryEmit(FleetEvent.Toast("삭제 실패")) }
        }
    }

    fun triggerOilAlertCheckNow() {
        val vehicles = (vehiclesState.value as? UiState.Success)?.data.orEmpty()
        val profile = (profileState.value as? UiState.Success)?.data
        _events.tryEmit(
            FleetEvent.CheckOilAlerts(
                vehicles = vehicles,
                globalEnabled = profile?.oilAlertEnabled ?: true,
                thresholdKm = profile?.oilAlertThresholdKm ?: 1000L
            )
        )
    }

    fun saveAppReleaseConfig(
        latestVersionCode: Long,
        latestVersionName: String,
        downloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean
    ) {
        viewModelScope.launch {
            val existing = (releaseConfigState.value as? UiState.Success)?.data
            repository.saveAppReleaseConfig(
                userId = currentUserId,
                existing = existing,
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                forceUpdate = forceUpdate,
                updatedByNickname = currentEditorNickname()
            ).onSuccess {
                _events.tryEmit(FleetEvent.Toast("배포 버전 저장됨"))
            }.onFailure { err ->
                _events.tryEmit(FleetEvent.Toast(err.localizedMessage ?: "저장 실패"))
            }
        }
    }

    companion object {
        private const val TAG = "FleetViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
