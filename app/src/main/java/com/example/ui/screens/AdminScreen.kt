package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Company
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.ui.components.IosGroupedCard
import com.example.ui.components.IosSearchBar
import com.example.ui.components.IosStatusBadge
import com.example.ui.components.statusColor
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AdminScreen(
    companiesState: UiState<List<Company>>,
    vehiclesState: UiState<List<Vehicle>>,
    onEditMileageAndStatus: (Vehicle) -> Unit,
    onAddCompany: () -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ios = LocalIosColors.current
    val nf = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val companies = (companiesState as? UiState.Success)?.data.orEmpty()
    val vehicles = (vehiclesState as? UiState.Success)?.data.orEmpty()
    val companyMap = remember(companies) { companies.associateBy { it.id } }

    var query by remember { mutableStateOf("") }
    val filtered = remember(vehicles, query, companyMap) {
        val q = query.trim()
        if (q.isEmpty()) vehicles
        else vehicles.filter { v ->
            val compName = companyMap[v.companyId]?.name.orEmpty()
            v.plateNumber.contains(q, ignoreCase = true) ||
                v.modelName.contains(q, ignoreCase = true) ||
                compName.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "관리자",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onAddCompany,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("admin_add_company_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "회사",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (companies.isNotEmpty()) {
                    Surface(
                        onClick = onAddVehicle,
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("admin_add_vehicle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "차량",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        IosSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "주행거리 · 상태 입력할 차량 검색",
            testTagName = "admin_search_bar"
        )

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "차량 없음",
                    style = MaterialTheme.typography.titleMedium,
                    color = ios.secondaryLabel
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { vehicle ->
                    val companyName = companyMap[vehicle.companyId]?.name.orEmpty()
                    val status = vehicle.statusEnum()
                    IosGroupedCard(
                        onClick = { onEditMileageAndStatus(vehicle) },
                        modifier = Modifier.testTag("admin_vehicle_${vehicle.id}")
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
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IosStatusBadge(label = status.labelKo, color = statusColor(status))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "$companyName · ${vehicle.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ios.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "${nf.format(vehicle.mileageKm)} km",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                val statusAudit = remember(vehicle.statusUpdatedBy, vehicle.statusUpdatedAt, vehicle.updatedAt) {
                                    vehicle.formattedStatusAudit()
                                }
                                if (!statusAudit.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = statusAudit,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = ios.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "입력",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "입력",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
