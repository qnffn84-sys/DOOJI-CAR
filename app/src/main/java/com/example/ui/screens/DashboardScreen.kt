package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Company
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.data.VehicleStatus
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosSectionTitle
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.parseHexColor
import com.example.ui.components.statusColor
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    companiesState: UiState<List<Company>>,
    vehiclesState: UiState<List<Vehicle>>,
    oilThresholdKm: Long,
    onVehicleClick: (Vehicle) -> Unit,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val companies = (companiesState as? UiState.Success)?.data.orEmpty()
    val vehicles = (vehiclesState as? UiState.Success)?.data.orEmpty()
    val companyMap = remember(companies) { companies.associateBy { it.id } }

    val totalCount = vehicles.size
    val activeCount = vehicles.count { it.statusEnum() == VehicleStatus.ACTIVE }
    val idleCount = vehicles.count { it.statusEnum() == VehicleStatus.IDLE }
    val maintCount = vehicles.count {
        it.statusEnum() == VehicleStatus.MAINTENANCE || it.statusEnum() == VehicleStatus.ATTENTION
    }
    val oilDueList = remember(vehicles, oilThresholdKm) {
        vehicles.filter { it.needsOilAttention(oilThresholdKm) }.sortedBy { it.remainingOilKm() }
    }
    val expiringInsuranceList = remember(vehicles) {
        vehicles.mapNotNull { v ->
            val dDay = computeDaysUntil(v.insuranceExpiryDate)
            if (dDay != null && dDay <= 30) v to dDay else null
        }.sortedBy { it.second }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "대시보드",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2x2 KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardKpiCard(
                        icon = Icons.Rounded.DirectionsCar,
                        tint = MaterialTheme.colorScheme.primary,
                        label = "전체 차량",
                        value = "${totalCount}대",
                        modifier = Modifier.weight(1f).testTag("kpi_total")
                    )
                    DashboardKpiCard(
                        icon = Icons.Rounded.CheckCircle,
                        tint = ios.green,
                        label = "운행 중",
                        value = "${activeCount}대",
                        modifier = Modifier.weight(1f).testTag("kpi_active")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardKpiCard(
                        icon = Icons.Rounded.Build,
                        tint = ios.orange,
                        label = "정비 · 점검",
                        value = "${maintCount}대",
                        modifier = Modifier.weight(1f).testTag("kpi_maintenance")
                    )
                    DashboardKpiCard(
                        icon = Icons.Rounded.OilBarrel,
                        tint = ios.red,
                        label = "오일 임박",
                        value = "${oilDueList.size}대",
                        modifier = Modifier.weight(1f).testTag("kpi_oil_due")
                    )
                }
            }
        }

        // Status Distribution Bar
        item {
            IosSectionTitle("상태 비율")
            IosGroupedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    val greenColor = ios.green
                    val indigoColor = ios.indigo
                    val orangeColor = ios.orange
                    val trackColor = ios.groupedSecondary

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        if (totalCount == 0) {
                            drawRoundRect(
                                color = trackColor,
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                        } else {
                            val segments = listOf(
                                activeCount to greenColor,
                                idleCount to indigoColor,
                                maintCount to orangeColor
                            ).filter { it.first > 0 }
                            var x = 0f
                            val gap = 2.dp.toPx()
                            segments.forEachIndexed { idx, (count, color) ->
                                val w = (count.toFloat() / totalCount) * size.width -
                                    if (idx < segments.lastIndex) gap else 0f
                                drawRect(
                                    color = color,
                                    topLeft = Offset(x, 0f),
                                    size = Size(w.coerceAtLeast(0f), size.height)
                                )
                                x += w + gap
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusLegendDot("운행 중", activeCount, ios.green)
                        StatusLegendDot("대기 중", idleCount, ios.indigo)
                        StatusLegendDot("정비 · 점검", maintCount, ios.orange)
                    }
                }
            }
        }

        // Company Fleet Breakdown
        if (companies.isNotEmpty()) {
            item {
                IosSectionTitle("회사별 보유")
                IosGroupedCard {
                    companies.forEachIndexed { index, company ->
                        val count = vehicles.count { it.companyId == company.id }
                        val ratio = if (totalCount > 0) count.toFloat() / totalCount else 0f
                        val accent = parseHexColor(company.accentColorHex)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = company.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(100.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ios.groupedSecondary)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accent)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${count}대",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (index < companies.lastIndex) {
                            IosHairlineDivider(startIndent = 36.dp)
                        }
                    }
                }
            }
        }

        // Urgent Oil Alerts
        if (oilDueList.isNotEmpty()) {
            item {
                IosSectionTitle("엔진 오일 점검")
                IosGroupedCard {
                    oilDueList.forEachIndexed { idx, vehicle ->
                        val rem = vehicle.remainingOilKm()
                        val compName = companyMap[vehicle.companyId]?.name.orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVehicleClick(vehicle) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vehicle.plateNumber,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$compName · ${vehicle.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ios.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IosStatusBadge(
                                label = if (rem <= 0L) "${nf.format(-rem)} km 초과" else "${nf.format(rem)} km",
                                color = if (rem <= 0L) ios.red else ios.orange
                            )
                        }
                        if (idx < oilDueList.lastIndex) IosHairlineDivider()
                    }
                }
            }
        }

        // Expiring Insurance
        if (expiringInsuranceList.isNotEmpty()) {
            item {
                IosSectionTitle("보험 만기 임박")
                IosGroupedCard {
                    expiringInsuranceList.forEachIndexed { idx, (vehicle, dDay) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVehicleClick(vehicle) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vehicle.plateNumber,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${vehicle.insuranceCompany} · ${vehicle.insuranceExpiryDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ios.secondaryLabel
                                )
                            }
                            IosStatusBadge(
                                label = if (dDay < 0) "만료" else "D-$dDay",
                                color = if (dDay < 0) ios.red else ios.orange
                            )
                        }
                        if (idx < expiringInsuranceList.lastIndex) IosHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    IosGroupedCard(modifier = modifier, cornerRadius = 20.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ios.secondaryLabel
            )
        }
    }
}

@Composable
private fun StatusLegendDot(
    label: String,
    count: Int,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            color = LocalIosColors.current.secondaryLabel
        )
    }
}
