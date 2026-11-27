package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Company
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.data.VehicleStatus
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.IosSearchBar
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.parseHexColor
import com.example.ui.components.statusColor
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.ParkingPhotoHelper

@Composable
fun CompaniesScreen(
    companiesState: UiState<List<Company>>,
    vehiclesState: UiState<List<Vehicle>>,
    searchQuery: String,
    oilThresholdKm: Long,
    onSearchQueryChange: (String) -> Unit,
    onCompanyClick: (Company) -> Unit,
    onEditCompanyClick: (Company) -> Unit,
    onVehicleClick: (Vehicle) -> Unit,
    onAddCompanyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    val companies = (companiesState as? UiState.Success)?.data.orEmpty()
    val vehicles = (vehiclesState as? UiState.Success)?.data.orEmpty()
    val companyMap = remember(companies) { companies.associateBy { it.id } }

    val trimmedQuery = searchQuery.trim()
    val matchingVehicles = remember(trimmedQuery, vehicles, companyMap) {
        if (trimmedQuery.isEmpty()) emptyList()
        else vehicles.filter { v ->
            val compName = companyMap[v.companyId]?.name.orEmpty()
            v.plateNumber.contains(trimmedQuery, ignoreCase = true) ||
                v.modelName.contains(trimmedQuery, ignoreCase = true) ||
                v.parkingLocation.contains(trimmedQuery, ignoreCase = true) ||
                compName.contains(trimmedQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // iOS Large Title Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_doojicar_logo),
                    contentDescription = "두지카 로고",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Text(
                    text = "두지카",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Surface(
                onClick = onAddCompanyClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("add_company_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "회사 추가",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        IosSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "차량 번호 · 차종 · 주차 위치 · 회사",
            testTagName = "global_search_bar"
        )

        Spacer(Modifier.height(14.dp))

        when {
            companiesState is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            companiesState is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = companiesState.message,
                        color = ios.red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            trimmedQuery.isNotEmpty() -> {
                if (matchingVehicles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "검색 결과 없음",
                            style = MaterialTheme.typography.titleMedium,
                            color = ios.secondaryLabel
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(matchingVehicles, key = { it.id }) { vehicle ->
                            val companyName = companyMap[vehicle.companyId]?.name.orEmpty()
                            GlobalSearchVehicleCard(
                                vehicle = vehicle,
                                companyName = companyName,
                                onClick = { onVehicleClick(vehicle) }
                            )
                        }
                    }
                }
            }
            companies.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "등록된 회사 없음",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(18.dp))
                        IosPrimaryButton(
                            text = "회사 추가",
                            onClick = onAddCompanyClick,
                            modifier = Modifier.width(180.dp),
                            testTagName = "empty_add_company_button"
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(companies, key = { it.id }) { company ->
                        val companyVehicles = vehicles.filter { it.companyId == company.id }
                        val issueCount = companyVehicles.count {
                            it.statusEnum() == VehicleStatus.MAINTENANCE ||
                                it.statusEnum() == VehicleStatus.ATTENTION ||
                                it.needsOilAttention(oilThresholdKm)
                        }
                        CompanySquareCard(
                            company = company,
                            vehicleCount = companyVehicles.size,
                            issueCount = issueCount,
                            onClick = { onCompanyClick(company) },
                            onLongClick = { onEditCompanyClick(company) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompanySquareCard(
    company: Company,
    vehicleCount: Int,
    issueCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ios = LocalIosColors.current
    val accent = remember(company.accentColorHex) { parseHexColor(company.accentColorHex) }
    val bgBitmap = remember(company.backgroundPhotoPath, company.backgroundPhotoBase64) {
        ParkingPhotoHelper.loadBitmap(company.backgroundPhotoPath, company.backgroundPhotoBase64)
    }
    val hasPhoto = bgBitmap != null
    val shape = RoundedCornerShape(22.dp)
    val border = if (ios.isDark && !hasPhoto) BorderStroke(0.5.dp, ios.separator.copy(alpha = 0.55f)) else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.06f)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("company_card_${company.id}"),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (ios.isDark) 0.dp else 1.5.dp,
        border = border
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = "${company.name} 배경 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.34f),
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (hasPhoto) Color.Black.copy(alpha = 0.44f)
                                else accent.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = company.code.take(3).ifBlank { company.name.take(2) },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (hasPhoto) Color.White else accent,
                            maxLines = 1
                        )
                    }

                    if (issueCount > 0) {
                        if (hasPhoto) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ios.orange.copy(alpha = 0.92f)
                            ) {
                                Text(
                                    text = "점검 $issueCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            IosStatusBadge(
                                label = "점검 $issueCount",
                                color = ios.orange
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = company.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (hasPhoto) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${vehicleCount}대",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (hasPhoto) Color.White.copy(alpha = 0.9f) else accent
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchVehicleCard(
    vehicle: Vehicle,
    companyName: String,
    onClick: () -> Unit
) {
    val ios = LocalIosColors.current
    val status = vehicle.statusEnum()
    val vehicleBitmap = remember(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64)
    }

    IosGroupedCard(onClick = onClick, modifier = Modifier.testTag("search_vehicle_${vehicle.id}")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (vehicleBitmap != null) {
                Image(
                    bitmap = vehicleBitmap.asImageBitmap(),
                    contentDescription = "차량 대표 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vehicle.plateNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (companyName.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = companyName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = vehicle.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = ios.secondaryLabel,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = vehicle.parkingLocation.ifBlank { "미지정" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ios.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IosStatusBadge(label = status.labelKo, color = statusColor(status))
        }
    }
}
