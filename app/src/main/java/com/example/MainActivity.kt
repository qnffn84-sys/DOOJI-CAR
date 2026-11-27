package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.Company
import com.example.data.FleetRepository
import com.example.data.LocalPreferencesRepository
import com.example.data.ThemePreference
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.ui.screens.AdminMileageStatusSheet
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AppUpdateConfigSheet
import com.example.ui.screens.AppUpdatePromptDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.openUpdateDownloadLink
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.CompaniesScreen
import com.example.ui.screens.CompanyFormSheet
import com.example.ui.screens.CompanyVehiclesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InsuranceFormSheet
import com.example.ui.screens.MaintenanceFormSheet
import com.example.ui.screens.NicknameChangeSheet
import com.example.ui.screens.NicknameSetupScreen
import com.example.ui.screens.ParkingLocationSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VehicleDetailScreen
import com.example.ui.screens.VehicleFormSheet
import com.example.ui.theme.DoojicarTheme
import com.example.ui.theme.LocalIosColors
import com.example.util.GoogleAuthHelper
import com.example.util.OilNotificationHelper
import com.example.util.configureAppCheck
import com.example.viewmodel.FleetEvent
import com.example.viewmodel.FleetViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

const val ROUTE_COMPANIES = "companies"
const val ROUTE_COMPANY_VEHICLES = "company_vehicles/{companyId}"
const val ROUTE_VEHICLE_DETAIL = "vehicle_detail/{vehicleId}"
const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_ADMIN = "admin"
const val ROUTE_SETTINGS = "settings"

private enum class DoojicarTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    COMPANIES(ROUTE_COMPANIES, "회사", Icons.Rounded.Business, Icons.Outlined.Business),
    DASHBOARD(ROUTE_DASHBOARD, "대시보드", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    ADMIN(ROUTE_ADMIN, "관리자", Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
    SETTINGS(ROUTE_SETTINGS, "설정", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        OilNotificationHelper.ensureChannel(applicationContext)
        setContent {
            DoojicarRootApp()
        }
    }
}

@Composable
fun DoojicarRootApp() {
    val context = LocalContext.current
    val localPrefs = remember(context) { LocalPreferencesRepository(context.applicationContext) }
    val localTheme by localPrefs.themePreference.collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
    val systemDark = isSystemInDarkTheme()

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val isDark = when (localTheme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> systemDark
    }

    DoojicarTheme(darkTheme = isDark) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            val uid = user.uid
            key(uid) {
                AuthenticatedDoojicarApp(
                    currentUserId = uid,
                    googleEmail = user.email.orEmpty(),
                    googleDisplayName = user.displayName.orEmpty(),
                    localTheme = localTheme,
                    localPrefs = localPrefs
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedDoojicarApp(
    currentUserId: String,
    googleEmail: String,
    googleDisplayName: String,
    localTheme: ThemePreference,
    localPrefs: LocalPreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FleetViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FleetViewModel(
                    repository = FleetRepository(db),
                    localPrefs = localPrefs,
                    currentUserId = currentUserId,
                    googleEmail = googleEmail,
                    googleDisplayName = googleDisplayName
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val companiesState by viewModel.companiesState.collectAsStateWithLifecycle()
    val vehiclesState by viewModel.vehiclesState.collectAsStateWithLifecycle()
    val maintenanceState by viewModel.maintenanceState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val releaseConfigState by viewModel.releaseConfigState.collectAsStateWithLifecycle()
    val isBiometricLocked by viewModel.isBiometricLocked.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val odometerScanState by viewModel.odometerScanState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val profile = (profileState as? UiState.Success)?.data
    LaunchedEffect(profile?.id) {
        viewModel.syncInitialBiometricLock(profile)
    }

    // Sync Firestore theme preference to local DataStore when profile loads
    LaunchedEffect(profile?.darkMode) {
        val remoteTheme = profile?.themePreference()
        if (remoteTheme != null && remoteTheme != localTheme) {
            localPrefs.setThemePreference(remoteTheme)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FleetEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is FleetEvent.CheckOilAlerts -> {
                    val count = OilNotificationHelper.notifyDueVehicles(
                        context = context,
                        vehicles = event.vehicles,
                        globalEnabled = event.globalEnabled,
                        thresholdKm = event.thresholdKm
                    )
                    if (count > 0) {
                        snackbarHostState.showSnackbar("오일 점검 차량 ${count}대 알림 발송")
                    } else {
                        snackbarHostState.showSnackbar("오일 교체 임박 차량 없음")
                    }
                }
            }
        }
    }

    fun performSignOut() {
        GoogleAuthHelper.signOut(credentialManager = credentialManager, scope = scope)
    }

    when {
        profileState is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        profileState is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = (profileState as UiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    androidx.compose.material3.TextButton(onClick = { performSignOut() }) {
                        Text("다른 Google 계정으로 로그인")
                    }
                }
            }
        }
        profile == null || profile.nickname.isBlank() -> {
            val suggested = profile?.workspaceId?.ifBlank { "" }.orEmpty()
                .ifBlank { googleDisplayName }
                .ifBlank { googleEmail.substringBefore("@") }
            NicknameSetupScreen(
                suggestedNickname = suggested,
                googleEmail = googleEmail,
                isSubmitting = isSubmitting,
                errorMessage = authError,
                onComplete = { nickname, enableBiometric ->
                    viewModel.registerProfileWithNickname(nickname, enableBiometric, localTheme)
                },
                onClearError = { viewModel.clearAuthError() },
                onSignOut = { performSignOut() }
            )
        }
        isBiometricLocked -> {
            BiometricLockScreen(
                profile = profile,
                onBiometricUnlocked = { viewModel.unlockWithBiometric() },
                onSignOut = { performSignOut() }
            )
        }
        else -> {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: ROUTE_COMPANIES

            val companies = (companiesState as? UiState.Success)?.data.orEmpty()
            val vehicles = (vehiclesState as? UiState.Success)?.data.orEmpty()
            val releaseConfig = (releaseConfigState as? UiState.Success)?.data
            val oilThresholdKm = profile.oilAlertThresholdKm

            // Sheet & Dialog States
            var showCompanyForm by remember { mutableStateOf(false) }
            var editingCompany by remember { mutableStateOf<Company?>(null) }
            var showVehicleFormForCompanyId by remember { mutableStateOf<String?>(null) }
            var adminEditingVehicle by remember { mutableStateOf<Vehicle?>(null) }
            var maintenanceTargetVehicle by remember { mutableStateOf<Vehicle?>(null) }
            var insuranceTargetVehicle by remember { mutableStateOf<Vehicle?>(null) }
            var parkingLocationTargetVehicle by remember { mutableStateOf<Vehicle?>(null) }
            var showChangeNickname by remember { mutableStateOf(false) }
            var showUpdateConfigSheet by remember { mutableStateOf(false) }
            var dismissedUpdateVersionCode by remember { mutableStateOf<Long?>(null) }

            val showMainTabs = currentRoute in listOf(
                ROUTE_COMPANIES,
                ROUTE_DASHBOARD,
                ROUTE_ADMIN,
                ROUTE_SETTINGS
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val ios = LocalIosColors.current

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showMainTabs && !useRail) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = ios.separator.copy(alpha = 0.55f)
                                )
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                    tonalElevation = 0.dp
                                ) {
                                    DoojicarTab.entries.forEach { tab ->
                                        val selected = currentRoute == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                if (!selected) {
                                                    navController.navigate(tab.route) {
                                                        popUpTo(ROUTE_COMPANIES) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = Color.Transparent,
                                                unselectedIconColor = ios.secondaryLabel,
                                                unselectedTextColor = ios.secondaryLabel
                                            ),
                                            modifier = Modifier.testTag("tab_${tab.route}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (showMainTabs && useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DoojicarTab.entries.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                navController.navigate(tab.route) {
                                                    popUpTo(ROUTE_COMPANIES) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_COMPANIES,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_COMPANIES) {
                                CompaniesScreen(
                                    companiesState = companiesState,
                                    vehiclesState = vehiclesState,
                                    searchQuery = searchQuery,
                                    oilThresholdKm = oilThresholdKm,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onCompanyClick = { company ->
                                        navController.navigate("company_vehicles/${company.id}")
                                    },
                                    onEditCompanyClick = { company ->
                                        editingCompany = company
                                        showCompanyForm = true
                                    },
                                    onVehicleClick = { vehicle ->
                                        navController.navigate("vehicle_detail/${vehicle.id}")
                                    },
                                    onAddCompanyClick = {
                                        editingCompany = null
                                        showCompanyForm = true
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_COMPANY_VEHICLES,
                                arguments = listOf(navArgument("companyId") { type = NavType.StringType })
                            ) { entry ->
                                val companyId = entry.arguments?.getString("companyId").orEmpty()
                                val company = companies.firstOrNull { it.id == companyId }
                                if (company == null) {
                                    LaunchedEffect(companyId) { navController.popBackStack() }
                                } else {
                                    val companyVehicles = remember(vehicles, companyId) {
                                        vehicles.filter { it.companyId == companyId }
                                    }
                                    CompanyVehiclesScreen(
                                        company = company,
                                        vehicles = companyVehicles,
                                        oilThresholdKm = oilThresholdKm,
                                        onBack = { navController.popBackStack() },
                                        onEditCompany = {
                                            editingCompany = company
                                            showCompanyForm = true
                                        },
                                        onAddVehicle = {
                                            showVehicleFormForCompanyId = company.id
                                        },
                                        onVehicleClick = { vehicle ->
                                            navController.navigate("vehicle_detail/${vehicle.id}")
                                        }
                                    )
                                }
                            }

                            composable(
                                route = ROUTE_VEHICLE_DETAIL,
                                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
                            ) { entry ->
                                val vehicleId = entry.arguments?.getString("vehicleId").orEmpty()
                                val vehicle = vehicles.firstOrNull { it.id == vehicleId }
                                LaunchedEffect(vehicleId) {
                                    viewModel.observeMaintenanceFor(vehicleId)
                                }
                                if (vehicle == null) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    val compName = companies.firstOrNull { it.id == vehicle.companyId }?.name.orEmpty()
                                    VehicleDetailScreen(
                                        vehicle = vehicle,
                                        companyName = compName,
                                        maintenanceState = maintenanceState,
                                        historyState = historyState,
                                        oilThresholdKm = oilThresholdKm,
                                        onBack = { navController.popBackStack() },
                                        onDeleteVehicle = {
                                            viewModel.deleteVehicle(vehicle.id) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onUpdateParking = { loc, path, b64 ->
                                            viewModel.updateVehicleParking(vehicle, loc, path, b64)
                                        },
                                        onUpdateVehiclePhoto = { path, b64 ->
                                            viewModel.updateVehiclePhoto(vehicle, path, b64)
                                        },
                                        onUpdateOilSettings = { lastKm, intervalKm, alertOn ->
                                            viewModel.updateVehicleOilSettings(vehicle, lastKm, intervalKm, alertOn)
                                        },
                                        onOpenEditParkingLocation = {
                                            parkingLocationTargetVehicle = vehicle
                                        },
                                        onOpenAdminEdit = {
                                            adminEditingVehicle = vehicle
                                        },
                                        onOpenAddMaintenance = {
                                            maintenanceTargetVehicle = vehicle
                                        },
                                        onDeleteMaintenance = { recordId ->
                                            viewModel.deleteMaintenanceRecord(vehicle.id, recordId)
                                        },
                                        onOpenEditInsurance = {
                                            insuranceTargetVehicle = vehicle
                                        },
                                        onShowMessage = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    companiesState = companiesState,
                                    vehiclesState = vehiclesState,
                                    oilThresholdKm = oilThresholdKm,
                                    onVehicleClick = { vehicle ->
                                        navController.navigate("vehicle_detail/${vehicle.id}")
                                    }
                                )
                            }

                            composable(ROUTE_ADMIN) {
                                AdminScreen(
                                    companiesState = companiesState,
                                    vehiclesState = vehiclesState,
                                    onEditMileageAndStatus = { vehicle ->
                                        adminEditingVehicle = vehicle
                                    },
                                    onAddCompany = {
                                        editingCompany = null
                                        showCompanyForm = true
                                    },
                                    onAddVehicle = {
                                        showVehicleFormForCompanyId = companies.firstOrNull()?.id.orEmpty()
                                    }
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    currentUserId = currentUserId,
                                    googleEmail = googleEmail,
                                    profile = profile,
                                    releaseConfig = releaseConfig,
                                    currentTheme = localTheme,
                                    onUpdatePreferences = viewModel::updatePreferences,
                                    onTriggerOilCheckNow = viewModel::triggerOilAlertCheckNow,
                                    onOpenChangeNickname = { showChangeNickname = true },
                                    onOpenUpdateLink = { url ->
                                        if (!openUpdateDownloadLink(context, url)) {
                                            scope.launch { snackbarHostState.showSnackbar("링크를 열 수 없습니다") }
                                        }
                                    },
                                    onOpenUpdateConfig = { showUpdateConfigSheet = true },
                                    onLockNow = viewModel::lockWorkspace,
                                    onSignOut = { performSignOut() },
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Modal Bottom Sheets
            if (showCompanyForm) {
                val target = editingCompany
                CompanyFormSheet(
                    initial = target,
                    onDismiss = { showCompanyForm = false },
                    onDelete = target?.let { comp ->
                        {
                            showCompanyForm = false
                            viewModel.deleteCompany(comp.id)
                        }
                    },
                    onSave = { name, code, hex, bgPath, bgB64 ->
                        showCompanyForm = false
                        if (target == null) {
                            viewModel.addCompany(name, code, hex, bgPath, bgB64)
                        } else {
                            viewModel.updateCompany(target.id, name, code, hex, bgPath, bgB64)
                        }
                    },
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }

            val addVehCompanyId = showVehicleFormForCompanyId
            if (addVehCompanyId != null && companies.isNotEmpty()) {
                VehicleFormSheet(
                    companies = companies,
                    initialCompanyId = addVehCompanyId,
                    onDismiss = { showVehicleFormForCompanyId = null },
                    onSave = { compId, plate, model, year, parking, mileage, status, lastOil, oilInterval, oilAlert, insComp, insPol, insExp, insCov, vehPhotoPath, vehPhotoB64 ->
                        showVehicleFormForCompanyId = null
                        viewModel.addVehicle(
                            companyId = compId,
                            plateNumber = plate,
                            modelName = model,
                            year = year,
                            parkingLocation = parking,
                            mileageKm = mileage,
                            status = status,
                            lastOilChangeKm = lastOil,
                            oilChangeIntervalKm = oilInterval,
                            oilAlertEnabled = oilAlert,
                            insuranceCompany = insComp,
                            insurancePolicyNo = insPol,
                            insuranceExpiryDate = insExp,
                            insuranceCoverage = insCov,
                            vehiclePhotoPath = vehPhotoPath,
                            vehiclePhotoBase64 = vehPhotoB64
                        )
                    },
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }

            adminEditingVehicle?.let { veh ->
                AdminMileageStatusSheet(
                    vehicle = veh,
                    scanState = odometerScanState,
                    onDismiss = {
                        viewModel.clearOdometerScanState()
                        adminEditingVehicle = null
                    },
                    onSave = { km, status ->
                        viewModel.clearOdometerScanState()
                        adminEditingVehicle = null
                        viewModel.updateVehicleAdmin(veh, km, status)
                    },
                    onScanOdometerFile = { file, status ->
                        viewModel.scanAndApplyOdometerFromFile(
                            vehicle = veh,
                            status = status,
                            file = file,
                            onApplied = { adminEditingVehicle = null }
                        )
                    },
                    onScanOdometerUri = { uri, status ->
                        viewModel.scanAndApplyOdometerFromUri(
                            context = context,
                            vehicle = veh,
                            status = status,
                            uri = uri,
                            onApplied = { adminEditingVehicle = null }
                        )
                    },
                    onShowMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }

            maintenanceTargetVehicle?.let { veh ->
                MaintenanceFormSheet(
                    currentMileageKm = veh.mileageKm,
                    onDismiss = { maintenanceTargetVehicle = null },
                    onSave = { date, cat, title, km, cost, shop ->
                        maintenanceTargetVehicle = null
                        viewModel.addMaintenanceRecord(veh, date, cat, title, km, cost, shop)
                    }
                )
            }

            insuranceTargetVehicle?.let { veh ->
                InsuranceFormSheet(
                    vehicle = veh,
                    onDismiss = { insuranceTargetVehicle = null },
                    onSave = { comp, pol, exp, cov ->
                        insuranceTargetVehicle = null
                        viewModel.updateVehicleInsurance(veh, comp, pol, exp, cov)
                    }
                )
            }

            parkingLocationTargetVehicle?.let { veh ->
                ParkingLocationSheet(
                    initialLocation = veh.parkingLocation,
                    onDismiss = { parkingLocationTargetVehicle = null },
                    onSave = { newLoc ->
                        parkingLocationTargetVehicle = null
                        viewModel.updateVehicleParking(
                            vehicle = veh,
                            parkingLocation = newLoc,
                            parkingPhotoPath = veh.parkingPhotoPath,
                            parkingPhotoBase64 = veh.parkingPhotoBase64
                        )
                    }
                )
            }

            if (showChangeNickname) {
                NicknameChangeSheet(
                    currentNickname = profile.displayNickname(),
                    onDismiss = { showChangeNickname = false },
                    onSave = { newNick ->
                        showChangeNickname = false
                        viewModel.updateNickname(newNick)
                    }
                )
            }

            if (showUpdateConfigSheet) {
                AppUpdateConfigSheet(
                    existing = releaseConfig,
                    onDismiss = { showUpdateConfigSheet = false },
                    onSave = { code, name, url, notes, force ->
                        showUpdateConfigSheet = false
                        viewModel.saveAppReleaseConfig(code, name, url, notes, force)
                    }
                )
            }

            if (
                releaseConfig != null &&
                releaseConfig.hasUpdateFor(BuildConfig.VERSION_CODE) &&
                (releaseConfig.forceUpdate || dismissedUpdateVersionCode != releaseConfig.latestVersionCode)
            ) {
                AppUpdatePromptDialog(
                    config = releaseConfig,
                    onOpenDownload = { url ->
                        if (!openUpdateDownloadLink(context, url)) {
                            scope.launch { snackbarHostState.showSnackbar("링크를 열 수 없습니다") }
                        }
                    },
                    onDismiss = { dismissedUpdateVersionCode = releaseConfig.latestVersionCode }
                )
            }
        }
    }
}
