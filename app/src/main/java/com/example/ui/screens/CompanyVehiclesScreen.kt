package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Company
import com.example.data.Vehicle
import com.example.data.VehicleStatus
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.IosSearchBar
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.statusColor
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.ParkingPhotoHelper
import java.text.NumberFormat
import java.util.Locale

private enum class VehicleFilter(val label: String) {
    ALL("전체"),
    ACTIVE("운행 중"),
    IDLE("대기 중"),
    MAINTENANCE("정비 중"),
    OIL("오일 점검")
}

@Composable
fun CompanyVehiclesScreen(
    company: Company,
    vehicles: List<Vehicle>,
    oilThresholdKm: Long,
    onBack: () -> Unit,
    onEditCompany: () -> Unit,
    onAddVehicle: () -> Unit,
    onVehicleClick: (Vehicle) -> Unit,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(VehicleFilter.ALL) }

    val filtered = remember(vehicles, query, selectedFilter, oilThresholdKm) {
        val q = query.trim()
        vehicles.filter { v ->
            val matchesQuery = q.isEmpty() ||
                v.plateNumber.contains(q, ignoreCase = true) ||
                v.modelName.contains(q, ignoreCase = true) ||
                v.parkingLocation.contains(q, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                VehicleFilter.ALL -> true
                VehicleFilter.ACTIVE -> v.statusEnum() == VehicleStatus.ACTIVE
                VehicleFilter.IDLE -> v.statusEnum() == VehicleStatus.IDLE
                VehicleFilter.MAINTENANCE -> v.statusEnum() == VehicleStatus.MAINTENANCE ||
                    v.statusEnum() == VehicleStatus.ATTENTION
                VehicleFilter.OIL -> v.needsOilAttention(oilThresholdKm)
            }
            matchesQuery && matchesFilter
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
                    .testTag("vehicles_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "뒤로",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "회사",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEditCompany,
                    modifier = Modifier.testTag("edit_company_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "회사 수정",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    onClick = onAddVehicle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .testTag("add_vehicle_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "차량 추가",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "${vehicles.size}대",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = ios.secondaryLabel,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            IosSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "차량 번호 · 차종 · 주차 위치",
                testTagName = "company_vehicles_search"
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VehicleFilter.entries) { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("filter_${filter.name}")
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = ios.secondaryLabel,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (vehicles.isEmpty()) "등록된 차량 없음" else "조건에 맞는 차량 없음",
                        style = MaterialTheme.typography.titleMedium,
                        color = ios.secondaryLabel
                    )
                    if (vehicles.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        IosPrimaryButton(
                            text = "차량 등록",
                            onClick = onAddVehicle,
                            modifier = Modifier.width(160.dp),
                            testTagName = "empty_add_vehicle_button"
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { vehicle ->
                    VehicleListCard(
                        vehicle = vehicle,
                        oilThresholdKm = oilThresholdKm,
                        onClick = { onVehicleClick(vehicle) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleListCard(
    vehicle: Vehicle,
    oilThresholdKm: Long,
    onClick: () -> Unit
) {
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val status = vehicle.statusEnum()
    val oilAlert = vehicle.needsOilAttention(oilThresholdKm)
    val remOil = vehicle.remainingOilKm()

    val vehicleBitmap = remember(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.vehiclePhotoPath, vehicle.vehiclePhotoBase64)
    }
    val parkingBitmap = remember(vehicle.parkingPhotoPath, vehicle.parkingPhotoBase64) {
        ParkingPhotoHelper.loadBitmap(vehicle.parkingPhotoPath, vehicle.parkingPhotoBase64)
    }

    IosGroupedCard(
        onClick = onClick,
        modifier = Modifier.testTag("vehicle_card_${vehicle.id}")
    ) {
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
                    Text(
                        text = vehicle.plateNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IosStatusBadge(
                        label = status.labelKo,
                        color = statusColor(status)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = vehicle.modelName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "주차 위치",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = vehicle.parkingLocation.ifBlank { "주차 미지정" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = ios.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${nf.format(vehicle.mileageKm)} km",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel
                    )
                }

                val statusAudit = remember(vehicle.statusUpdatedBy, vehicle.statusUpdatedAt, vehicle.updatedAt) {
                    vehicle.formattedStatusAudit()
                }
                if (!statusAudit.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = statusAudit,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (oilAlert) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.OilBarrel,
                            contentDescription = "오일 점검",
                            tint = if (remOil <= 0L) ios.red else ios.orange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (remOil <= 0L) "오일 ${nf.format(-remOil)} km 초과" else "오일 ${nf.format(remOil)} km 남음",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (remOil <= 0L) ios.red else ios.orange
                        )
                    }
                }
            }

            if (vehicleBitmap != null || parkingBitmap != null) {
                Spacer(Modifier.width(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vehicleBitmap != null) {
                        VehicleThumbTile(
                            bitmap = vehicleBitmap,
                            badge = "대표",
                            contentDescription = "차량 대표 사진",
                            modifier = Modifier.testTag("vehicle_profile_thumb_${vehicle.id}")
                        )
                    }
                    if (parkingBitmap != null) {
                        VehicleThumbTile(
                            bitmap = parkingBitmap,
                            badge = "주차",
                            contentDescription = "주차 사진",
                            modifier = Modifier.testTag("vehicle_parking_thumb_${vehicle.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleThumbTile(
    bitmap: Bitmap,
    badge: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
