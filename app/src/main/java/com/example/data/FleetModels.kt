package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemePreference(val raw: String, val labelKo: String) {
    SYSTEM("SYSTEM", "자동"),
    DARK("DARK", "다크"),
    LIGHT("LIGHT", "라이트");

    companion object {
        fun fromRaw(raw: String?): ThemePreference =
            entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

enum class VehicleStatus(val raw: String, val labelKo: String) {
    ACTIVE("ACTIVE", "운행 중"),
    IDLE("IDLE", "대기 중"),
    MAINTENANCE("MAINTENANCE", "정비 중"),
    ATTENTION("ATTENTION", "점검 필요");

    companion object {
        fun fromRaw(raw: String?): VehicleStatus =
            entries.firstOrNull { it.raw == raw } ?: ACTIVE
    }
}

enum class MaintenanceCategory(val raw: String, val labelKo: String) {
    ENGINE_OIL("ENGINE_OIL", "엔진 오일"),
    TIRE("TIRE", "타이어"),
    BRAKE("BRAKE", "브레이크"),
    INSPECTION("INSPECTION", "정기 점검"),
    REPAIR("REPAIR", "수리"),
    OTHER("OTHER", "기타");

    companion object {
        fun fromRaw(raw: String?): MaintenanceCategory =
            entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val email: String = "",
    val workspaceId: String = "",
    val passwordHash: String = "",
    val biometricEnabled: Boolean = false,
    val darkMode: String = ThemePreference.SYSTEM.raw,
    val oilAlertEnabled: Boolean = true,
    val oilAlertThresholdKm: Long = 1000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun themePreference(): ThemePreference = ThemePreference.fromRaw(darkMode)
    fun displayNickname(): String = nickname.ifBlank { workspaceId }.ifBlank { "사용자" }
}

@Keep
@IgnoreExtraProperties
data class Company(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val accentColorHex: String = "#007AFF",
    val backgroundPhotoPath: String? = null,
    val backgroundPhotoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasBackgroundPhoto(): Boolean =
        !backgroundPhotoPath.isNullOrBlank() || !backgroundPhotoBase64.isNullOrBlank()
}

@Keep
@IgnoreExtraProperties
data class Vehicle(
    @DocumentId val id: String = "",
    val companyId: String = "",
    val plateNumber: String = "",
    val modelName: String = "",
    val year: Long = 2025L,
    val parkingLocation: String = "",
    val parkingPhotoPath: String? = null,
    val parkingPhotoBase64: String? = null,
    val vehiclePhotoPath: String? = null,
    val vehiclePhotoBase64: String? = null,
    val mileageKm: Long = 0L,
    val status: String = VehicleStatus.ACTIVE.raw,
    val statusUpdatedBy: String? = null,
    @ServerTimestamp val statusUpdatedAt: Timestamp? = null,
    val lastOilChangeKm: Long = 0L,
    val oilChangeIntervalKm: Long = 10000L,
    val oilAlertEnabled: Boolean = true,
    val insuranceCompany: String = "",
    val insurancePolicyNo: String = "",
    val insuranceExpiryDate: String = "",
    val insuranceCoverage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): VehicleStatus = VehicleStatus.fromRaw(status)

    fun nextOilChangeKm(): Long = lastOilChangeKm + oilChangeIntervalKm

    fun remainingOilKm(): Long = nextOilChangeKm() - mileageKm

    fun oilUsageProgress(): Float {
        val interval = oilChangeIntervalKm.coerceAtLeast(1000L).toFloat()
        val used = (mileageKm - lastOilChangeKm).coerceAtLeast(0L).toFloat()
        return (used / interval).coerceIn(0f, 1f)
    }

    fun needsOilAttention(thresholdKm: Long = 1000L): Boolean =
        oilAlertEnabled && remainingOilKm() <= thresholdKm

    fun hasParkingPhoto(): Boolean =
        !parkingPhotoPath.isNullOrBlank() || !parkingPhotoBase64.isNullOrBlank()

    fun hasVehiclePhoto(): Boolean =
        !vehiclePhotoPath.isNullOrBlank() || !vehiclePhotoBase64.isNullOrBlank()

    fun formattedStatusAudit(): String? {
        val editor = statusUpdatedBy?.trim().orEmpty()
        if (editor.isEmpty()) return null
        val ts = statusUpdatedAt ?: updatedAt
        return if (ts != null) {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
            "$editor · ${fmt.format(ts.toDate())}"
        } else {
            editor
        }
    }
}

@Keep
@IgnoreExtraProperties
data class MaintenanceRecord(
    @DocumentId val id: String = "",
    val vehicleId: String = "",
    val serviceDate: String = "",
    val category: String = MaintenanceCategory.ENGINE_OIL.raw,
    val title: String = "",
    val mileageKm: Long = 0L,
    val costKrw: Long = 0L,
    val shopName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): MaintenanceCategory = MaintenanceCategory.fromRaw(category)
}

enum class ChangeLogType(val raw: String, val labelKo: String) {
    STATUS("STATUS", "상태 · 주행거리"),
    PARKING("PARKING", "주차 위치"),
    OIL("OIL", "엔진 오일"),
    INSURANCE("INSURANCE", "보험 정보"),
    CREATED("CREATED", "차량 등록");

    companion object {
        fun fromRaw(raw: String?): ChangeLogType =
            entries.firstOrNull { it.raw == raw } ?: STATUS
    }
}

@Keep
@IgnoreExtraProperties
data class VehicleChangeLog(
    @DocumentId val id: String = "",
    val vehicleId: String = "",
    val editorNickname: String = "",
    val changeType: String = ChangeLogType.STATUS.raw,
    val changes: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun changeTypeEnum(): ChangeLogType = ChangeLogType.fromRaw(changeType)

    fun formattedDateTime(): String {
        val ts = createdAt ?: return ""
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
        return fmt.format(ts.toDate())
    }
}

@Keep
@IgnoreExtraProperties
data class AppReleaseConfig(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val latestVersionCode: Long = 1L,
    val latestVersionName: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasUpdateFor(currentVersionCode: Int): Boolean =
        latestVersionCode > currentVersionCode.toLong() && downloadUrl.startsWith("https://")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
