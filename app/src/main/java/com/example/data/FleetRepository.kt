package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FleetRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Google 로그인이 필요합니다.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "계정이 변경되어 작업을 중단했습니다." }
        }
        return uid
    }

    // --- Real-Time Observation Flows ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeCompanies(userId: String): Flow<List<Company>> = flow {
        val uid = requireUserId(userId)
        val path = "companies"
        emitAll(
            db.collection("companies")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Company::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeVehicles(userId: String): Flow<List<Vehicle>> = flow {
        val uid = requireUserId(userId)
        val path = "vehicles"
        emitAll(
            db.collection("vehicles")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Vehicle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.plateNumber }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMaintenance(userId: String, vehicleId: String): Flow<List<MaintenanceRecord>> = flow {
        val uid = requireUserId(userId)
        require(vehicleId.isNotBlank())
        val path = "vehicles/$vehicleId/maintenance"
        emitAll(
            db.collection("vehicles").document(vehicleId)
                .collection("maintenance")
                .snapshots()
                .map { snap ->
                    snap.toObjects(MaintenanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.serviceDate }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeVehicleHistory(userId: String, vehicleId: String): Flow<List<VehicleChangeLog>> = flow {
        val uid = requireUserId(userId)
        require(vehicleId.isNotBlank())
        val path = "vehicles/$vehicleId/history"
        emitAll(
            db.collection("vehicles").document(vehicleId)
                .collection("history")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VehicleChangeLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAppReleaseConfig(userId: String): Flow<AppReleaseConfig?> = flow {
        val uid = requireUserId(userId)
        val path = "app_config/release"
        emitAll(
            db.collection("app_config").document("release")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(AppReleaseConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, uid, OperationType.GET, path)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        uid: String,
        op: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    // --- User Profile & Security ---

    suspend fun createUserProfile(
        userId: String,
        nickname: String,
        email: String,
        biometricEnabled: Boolean,
        darkMode: ThemePreference
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanNickname = nickname.trim()
            require(cleanNickname.length in 1..24) { "닉네임은 1~24자로 입력하세요" }

            val payload = mapOf(
                "userId" to uid,
                "nickname" to cleanNickname,
                "email" to email.trim().take(120),
                "biometricEnabled" to biometricEnabled,
                "darkMode" to darkMode.raw,
                "oilAlertEnabled" to true,
                "oilAlertThresholdKm" to 1000L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNickname(
        userId: String,
        nickname: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanNickname = nickname.trim()
            require(cleanNickname.length in 1..24) { "닉네임은 1~24자로 입력하세요" }

            db.collection("users").document(uid).update(
                mapOf(
                    "nickname" to cleanNickname,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        userId: String,
        biometricEnabled: Boolean,
        darkMode: ThemePreference,
        oilAlertEnabled: Boolean,
        oilAlertThresholdKm: Long
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val clampedThreshold = oilAlertThresholdKm.coerceIn(100L, 5000L)
            db.collection("users").document(uid).update(
                mapOf(
                    "biometricEnabled" to biometricEnabled,
                    "darkMode" to darkMode.raw,
                    "oilAlertEnabled" to oilAlertEnabled,
                    "oilAlertThresholdKm" to clampedThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Company CRUD (Shared) ---

    suspend fun addCompany(
        userId: String,
        name: String,
        code: String,
        accentColorHex: String,
        backgroundPhotoPath: String? = null,
        backgroundPhotoBase64: String? = null
    ): Result<String> {
        var path = "companies"
        return try {
            requireUserId(userId)
            val cleanName = name.trim()
            val cleanCode = code.trim().ifEmpty { cleanName.take(2) }
            require(cleanName.length in 1..60) { "회사명을 입력하세요" }
            require(cleanCode.length in 1..20) { "회사 코드를 확인하세요" }

            val docRef = db.collection("companies").document()
            path = docRef.path
            val rawPayload = mutableMapOf<String, Any?>(
                "name" to cleanName,
                "code" to cleanCode,
                "accentColorHex" to accentColorHex,
                "backgroundPhotoPath" to backgroundPhotoPath?.take(500),
                "backgroundPhotoBase64" to backgroundPhotoBase64?.take(350_000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            @Suppress("UNCHECKED_CAST")
            docRef.set(rawPayload as Map<String, Any>).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCompany(
        userId: String,
        companyId: String,
        name: String,
        code: String,
        accentColorHex: String,
        backgroundPhotoPath: String? = null,
        backgroundPhotoBase64: String? = null
    ): Result<Unit> {
        val path = "companies/$companyId"
        return try {
            requireUserId(userId)
            val cleanName = name.trim()
            val cleanCode = code.trim().ifEmpty { cleanName.take(2) }
            require(companyId.isNotBlank())
            require(cleanName.length in 1..60) { "회사명을 입력하세요" }

            db.collection("companies").document(companyId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "code" to cleanCode,
                        "accentColorHex" to accentColorHex,
                        "backgroundPhotoPath" to backgroundPhotoPath?.take(500),
                        "backgroundPhotoBase64" to backgroundPhotoBase64?.take(350_000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCompany(userId: String, companyId: String): Result<Unit> {
        val path = "companies/$companyId"
        return try {
            requireUserId(userId)
            require(companyId.isNotBlank())
            db.collection("companies").document(companyId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Vehicle CRUD (Shared) ---

    suspend fun addVehicle(
        userId: String,
        companyId: String,
        plateNumber: String,
        modelName: String,
        year: Long,
        parkingLocation: String,
        mileageKm: Long,
        status: VehicleStatus,
        createdByNickname: String?,
        lastOilChangeKm: Long,
        oilChangeIntervalKm: Long,
        oilAlertEnabled: Boolean,
        insuranceCompany: String,
        insurancePolicyNo: String,
        insuranceExpiryDate: String,
        insuranceCoverage: String,
        vehiclePhotoPath: String? = null,
        vehiclePhotoBase64: String? = null
    ): Result<String> {
        var path = "vehicles"
        return try {
            requireUserId(userId)
            val plate = plateNumber.trim()
            val model = modelName.trim()
            require(companyId.isNotBlank()) { "회사를 선택하세요" }
            require(plate.length in 2..20) { "차량 번호를 입력하세요 (2~20자)" }
            require(model.length in 1..60) { "차종을 입력하세요" }

            val docRef = db.collection("vehicles").document()
            path = docRef.path
            val cleanCreator = createdByNickname?.trim()?.take(24)?.takeIf { it.isNotEmpty() }
            val rawPayload = mutableMapOf<String, Any?>(
                "companyId" to companyId,
                "plateNumber" to plate,
                "modelName" to model,
                "year" to year.coerceIn(1990L, 2100L),
                "parkingLocation" to parkingLocation.trim().take(100),
                "vehiclePhotoPath" to vehiclePhotoPath?.take(500),
                "vehiclePhotoBase64" to vehiclePhotoBase64?.take(350_000),
                "mileageKm" to mileageKm.coerceIn(0L, 2_000_000L),
                "status" to status.raw,
                "statusUpdatedBy" to cleanCreator,
                "statusUpdatedAt" to if (cleanCreator != null) FieldValue.serverTimestamp() else null,
                "lastOilChangeKm" to lastOilChangeKm.coerceIn(0L, 2_000_000L),
                "oilChangeIntervalKm" to oilChangeIntervalKm.coerceIn(1000L, 50_000L),
                "oilAlertEnabled" to oilAlertEnabled,
                "insuranceCompany" to insuranceCompany.trim().take(60),
                "insurancePolicyNo" to insurancePolicyNo.trim().take(60),
                "insuranceExpiryDate" to insuranceExpiryDate.trim().take(20),
                "insuranceCoverage" to insuranceCoverage.trim().take(100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            @Suppress("UNCHECKED_CAST")
            docRef.set(rawPayload as Map<String, Any>).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehicleAdmin(
        userId: String,
        vehicleId: String,
        mileageKm: Long,
        status: VehicleStatus,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = updatedByNickname.trim().ifEmpty { "사용자" }.take(24)
            db.collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "mileageKm" to mileageKm.coerceIn(0L, 2_000_000L),
                        "status" to status.raw,
                        "statusUpdatedBy" to cleanNick,
                        "statusUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehicleParking(
        userId: String,
        vehicleId: String,
        parkingLocation: String,
        parkingPhotoPath: String?,
        parkingPhotoBase64: String?,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = updatedByNickname.trim().ifEmpty { "사용자" }.take(24)
            val updates = mutableMapOf<String, Any?>(
                "parkingLocation" to parkingLocation.trim().take(100),
                "parkingPhotoPath" to parkingPhotoPath?.take(500),
                "parkingPhotoBase64" to parkingPhotoBase64?.take(350_000),
                "statusUpdatedBy" to cleanNick,
                "statusUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            @Suppress("UNCHECKED_CAST")
            db.collection("vehicles").document(vehicleId)
                .update(updates as Map<String, Any>)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehiclePhoto(
        userId: String,
        vehicleId: String,
        vehiclePhotoPath: String?,
        vehiclePhotoBase64: String?,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = updatedByNickname.trim().ifEmpty { "사용자" }.take(24)
            db.collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "vehiclePhotoPath" to vehiclePhotoPath?.take(500),
                        "vehiclePhotoBase64" to vehiclePhotoBase64?.take(350_000),
                        "statusUpdatedBy" to cleanNick,
                        "statusUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehicleOilSettings(
        userId: String,
        vehicleId: String,
        lastOilChangeKm: Long,
        oilChangeIntervalKm: Long,
        oilAlertEnabled: Boolean,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = updatedByNickname.trim().ifEmpty { "사용자" }.take(24)
            db.collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "lastOilChangeKm" to lastOilChangeKm.coerceIn(0L, 2_000_000L),
                        "oilChangeIntervalKm" to oilChangeIntervalKm.coerceIn(1000L, 50_000L),
                        "oilAlertEnabled" to oilAlertEnabled,
                        "statusUpdatedBy" to cleanNick,
                        "statusUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVehicleInsurance(
        userId: String,
        vehicleId: String,
        insuranceCompany: String,
        insurancePolicyNo: String,
        insuranceExpiryDate: String,
        insuranceCoverage: String,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = updatedByNickname.trim().ifEmpty { "사용자" }.take(24)
            db.collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "insuranceCompany" to insuranceCompany.trim().take(60),
                        "insurancePolicyNo" to insurancePolicyNo.trim().take(60),
                        "insuranceExpiryDate" to insuranceExpiryDate.trim().take(20),
                        "insuranceCoverage" to insuranceCoverage.trim().take(100),
                        "statusUpdatedBy" to cleanNick,
                        "statusUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(userId: String, vehicleId: String): Result<Unit> {
        val path = "vehicles/$vehicleId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            db.collection("vehicles").document(vehicleId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Maintenance Records CRUD (Shared) ---

    suspend fun addMaintenanceRecord(
        userId: String,
        vehicleId: String,
        serviceDate: String,
        category: MaintenanceCategory,
        title: String,
        mileageKm: Long,
        costKrw: Long,
        shopName: String
    ): Result<String> {
        var path = "vehicles/$vehicleId/maintenance"
        return try {
            requireUserId(userId)
            val cleanTitle = title.trim()
            val cleanDate = serviceDate.trim()
            require(vehicleId.isNotBlank())
            require(cleanDate.length in 1..20) { "정비일을 입력하세요" }
            require(cleanTitle.length in 1..80) { "정비 항목을 입력하세요" }

            val docRef = db.collection("vehicles").document(vehicleId)
                .collection("maintenance").document()
            path = docRef.path
            val payload = mapOf(
                "vehicleId" to vehicleId,
                "serviceDate" to cleanDate,
                "category" to category.raw,
                "title" to cleanTitle,
                "mileageKm" to mileageKm.coerceIn(0L, 2_000_000L),
                "costKrw" to costKrw.coerceIn(0L, 100_000_000L),
                "shopName" to shopName.trim().take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMaintenanceRecord(
        userId: String,
        vehicleId: String,
        recordId: String
    ): Result<Unit> {
        val path = "vehicles/$vehicleId/maintenance/$recordId"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank() && recordId.isNotBlank())
            db.collection("vehicles").document(vehicleId)
                .collection("maintenance").document(recordId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Vehicle Change History (Shared Audit Log) ---

    suspend fun addVehicleChangeLog(
        userId: String,
        vehicleId: String,
        editorNickname: String,
        changeType: ChangeLogType,
        changes: List<String>
    ): Result<String> {
        var path = "vehicles/$vehicleId/history"
        return try {
            requireUserId(userId)
            require(vehicleId.isNotBlank())
            val cleanNick = editorNickname.trim().ifEmpty { "사용자" }.take(24)
            val cleanChanges = changes.map { it.trim().take(160) }.filter { it.isNotEmpty() }.take(10)
            require(cleanChanges.isNotEmpty()) { "변경 내역이 없습니다" }

            val docRef = db.collection("vehicles").document(vehicleId)
                .collection("history").document()
            path = docRef.path
            val payload = mapOf(
                "vehicleId" to vehicleId,
                "editorNickname" to cleanNick,
                "changeType" to changeType.raw,
                "changes" to cleanChanges,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- One-Time Legacy Personal-to-Shared Data Migration ---

    suspend fun migrateLegacyPersonalDataIfNeeded(userId: String): Result<Int> {
        return try {
            val uid = requireUserId(userId)
            val userDoc = db.collection("users").document(uid)
            val legacyCompanies = userDoc.collection("companies").get().await().documents
            val legacyVehicles = userDoc.collection("vehicles").get().await().documents
            if (legacyCompanies.isEmpty() && legacyVehicles.isEmpty()) {
                return Result.success(0)
            }

            var migratedCount = 0

            for (compDoc in legacyCompanies) {
                val targetRef = db.collection("companies").document(compDoc.id)
                if (!targetRef.get().await().exists()) {
                    val data = compDoc.data.orEmpty()
                    val name = (data["name"] as? String)?.trim()?.take(60).orEmpty()
                    if (name.isNotEmpty()) {
                        val code = (data["code"] as? String)?.trim()?.take(20)?.ifEmpty { name.take(2) } ?: name.take(2)
                        val color = (data["accentColorHex"] as? String)?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) } ?: "#007AFF"
                        val payload = mutableMapOf<String, Any?>(
                            "name" to name,
                            "code" to code,
                            "accentColorHex" to color,
                            "backgroundPhotoPath" to (data["backgroundPhotoPath"] as? String)?.take(500),
                            "backgroundPhotoBase64" to (data["backgroundPhotoBase64"] as? String)?.take(350_000),
                            "createdAt" to (compDoc.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ).filterValues { it != null }
                        @Suppress("UNCHECKED_CAST")
                        targetRef.set(payload as Map<String, Any>).await()
                        migratedCount++
                    }
                }
                compDoc.reference.delete().await()
            }

            for (vehDoc in legacyVehicles) {
                val targetRef = db.collection("vehicles").document(vehDoc.id)
                val data = vehDoc.data.orEmpty()
                val companyId = (data["companyId"] as? String).orEmpty()
                if (!targetRef.get().await().exists() && companyId.isNotBlank()) {
                    val parentExists = db.collection("companies").document(companyId).get().await().exists()
                    val plate = (data["plateNumber"] as? String)?.trim()?.take(20).orEmpty()
                    val model = (data["modelName"] as? String)?.trim()?.take(60).orEmpty()
                    if (parentExists && plate.length >= 2 && model.isNotEmpty()) {
                        val rawStatus = (data["status"] as? String)
                        val safeStatus = VehicleStatus.entries.firstOrNull { it.raw == rawStatus }?.raw ?: VehicleStatus.ACTIVE.raw
                        val payload = mutableMapOf<String, Any?>(
                            "companyId" to companyId,
                            "plateNumber" to plate,
                            "modelName" to model,
                            "year" to ((data["year"] as? Number)?.toLong() ?: 2025L).coerceIn(1990L, 2100L),
                            "parkingLocation" to (data["parkingLocation"] as? String)?.trim()?.take(100).orEmpty(),
                            "parkingPhotoPath" to (data["parkingPhotoPath"] as? String)?.take(500),
                            "parkingPhotoBase64" to (data["parkingPhotoBase64"] as? String)?.take(350_000),
                            "vehiclePhotoPath" to (data["vehiclePhotoPath"] as? String)?.take(500),
                            "vehiclePhotoBase64" to (data["vehiclePhotoBase64"] as? String)?.take(350_000),
                            "mileageKm" to ((data["mileageKm"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 2_000_000L),
                            "status" to safeStatus,
                            "statusUpdatedBy" to (data["statusUpdatedBy"] as? String)?.trim()?.take(24)?.takeIf { it.isNotEmpty() },
                            "statusUpdatedAt" to vehDoc.getTimestamp("statusUpdatedAt"),
                            "lastOilChangeKm" to ((data["lastOilChangeKm"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 2_000_000L),
                            "oilChangeIntervalKm" to ((data["oilChangeIntervalKm"] as? Number)?.toLong() ?: 10_000L).coerceIn(1000L, 50_000L),
                            "oilAlertEnabled" to ((data["oilAlertEnabled"] as? Boolean) ?: true),
                            "insuranceCompany" to (data["insuranceCompany"] as? String)?.trim()?.take(60).orEmpty(),
                            "insurancePolicyNo" to (data["insurancePolicyNo"] as? String)?.trim()?.take(60).orEmpty(),
                            "insuranceExpiryDate" to (data["insuranceExpiryDate"] as? String)?.trim()?.take(20).orEmpty(),
                            "insuranceCoverage" to (data["insuranceCoverage"] as? String)?.trim()?.take(100).orEmpty(),
                            "createdAt" to (vehDoc.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ).filterValues { it != null }
                        @Suppress("UNCHECKED_CAST")
                        targetRef.set(payload as Map<String, Any>).await()
                        migratedCount++
                    }
                }

                // Migrate maintenance subcollection
                val legacyMaint = vehDoc.reference.collection("maintenance").get().await().documents
                for (mDoc in legacyMaint) {
                    val mTarget = targetRef.collection("maintenance").document(mDoc.id)
                    if (!mTarget.get().await().exists()) {
                        val mData = mDoc.data.orEmpty()
                        val serviceDate = (mData["serviceDate"] as? String)?.trim()?.take(20).orEmpty()
                        val title = (mData["title"] as? String)?.trim()?.take(80).orEmpty()
                        val rawCat = (mData["category"] as? String)
                        val safeCat = MaintenanceCategory.entries.firstOrNull { it.raw == rawCat }?.raw ?: MaintenanceCategory.OTHER.raw
                        if (serviceDate.isNotEmpty() && title.isNotEmpty()) {
                            mTarget.set(
                                mapOf(
                                    "vehicleId" to vehDoc.id,
                                    "serviceDate" to serviceDate,
                                    "category" to safeCat,
                                    "title" to title,
                                    "mileageKm" to ((mData["mileageKm"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 2_000_000L),
                                    "costKrw" to ((mData["costKrw"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 100_000_000L),
                                    "shopName" to (mData["shopName"] as? String)?.trim()?.take(60).orEmpty(),
                                    "createdAt" to (mDoc.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                    mDoc.reference.delete().await()
                }

                // Migrate history subcollection
                val legacyHist = vehDoc.reference.collection("history").get().await().documents
                for (hDoc in legacyHist) {
                    val hTarget = targetRef.collection("history").document(hDoc.id)
                    if (!hTarget.get().await().exists()) {
                        val hData = hDoc.data.orEmpty()
                        val editor = (hData["editorNickname"] as? String)?.trim()?.take(24)?.ifEmpty { "사용자" } ?: "사용자"
                        val rawType = (hData["changeType"] as? String)
                        val safeType = ChangeLogType.entries.firstOrNull { it.raw == rawType }?.raw ?: ChangeLogType.STATUS.raw
                        val changes = (hData["changes"] as? List<*>)
                            ?.mapNotNull { (it as? String)?.trim()?.take(160)?.takeIf(String::isNotEmpty) }
                            ?.take(10)
                            .orEmpty()
                        if (changes.isNotEmpty()) {
                            hTarget.set(
                                mapOf(
                                    "vehicleId" to vehDoc.id,
                                    "editorNickname" to editor,
                                    "changeType" to safeType,
                                    "changes" to changes,
                                    "createdAt" to (hDoc.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
                                )
                            ).await()
                        }
                    }
                    hDoc.reference.delete().await()
                }

                vehDoc.reference.delete().await()
            }

            Result.success(migratedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    // --- Shared App Release Update Config ---

    suspend fun saveAppReleaseConfig(
        userId: String,
        existing: AppReleaseConfig?,
        latestVersionCode: Long,
        latestVersionName: String,
        downloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean,
        updatedByNickname: String
    ): Result<Unit> {
        val path = "app_config/release"
        return try {
            val uid = requireUserId(userId)
            val cleanVersionName = latestVersionName.trim()
            val cleanUrl = downloadUrl.trim()
            val cleanNotes = releaseNotes.trim().take(300)
            val cleanNick = updatedByNickname.trim().ifEmpty { "관리자" }.take(24)

            require(latestVersionCode in 1L..100_000L) { "버전 코드를 확인하세요 (1~100000)" }
            require(cleanVersionName.length in 1..20) { "버전명을 입력하세요 (예: 1.1)" }
            require(cleanUrl.startsWith("https://") && cleanUrl.length in 8..500) {
                "https://로 시작하는 다운로드 링크를 입력하세요"
            }

            val docRef = db.collection("app_config").document("release")
            if (existing == null) {
                docRef.set(
                    mapOf(
                        "adminUid" to uid,
                        "latestVersionCode" to latestVersionCode,
                        "latestVersionName" to cleanVersionName,
                        "downloadUrl" to cleanUrl,
                        "releaseNotes" to cleanNotes,
                        "forceUpdate" to forceUpdate,
                        "updatedBy" to cleanNick,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "latestVersionCode" to latestVersionCode,
                        "latestVersionName" to cleanVersionName,
                        "downloadUrl" to cleanUrl,
                        "releaseNotes" to cleanNotes,
                        "forceUpdate" to forceUpdate,
                        "updatedBy" to cleanNick,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
