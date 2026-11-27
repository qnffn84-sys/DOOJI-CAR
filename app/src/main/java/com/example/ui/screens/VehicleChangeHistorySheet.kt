package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.ChangeLogType
import com.example.data.UiState
import com.example.data.Vehicle
import com.example.data.VehicleChangeLog
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosStatusBadge
import com.example.ui.theme.LocalIosColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleChangeHistorySheet(
    vehicle: Vehicle,
    historyState: UiState<List<VehicleChangeLog>>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ios = LocalIosColors.current
    var selectedFilter by remember { mutableStateOf<ChangeLogType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag("vehicle_change_history_sheet")
        ) {
            val allLogs = (historyState as? UiState.Success)?.data.orEmpty()
            val filteredLogs = remember(allLogs, selectedFilter) {
                if (selectedFilter == null) allLogs
                else allLogs.filter { it.changeTypeEnum() == selectedFilter }
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "수정 내역",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${vehicle.plateNumber} · ${vehicle.modelName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = ios.secondaryLabel
                    )
                }
                if (historyState is UiState.Success) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${filteredLogs.size}건",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (allLogs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilterChip(
                        label = "전체",
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        testTagName = "history_filter_all"
                    )
                    listOf(
                        ChangeLogType.STATUS to "상태",
                        ChangeLogType.PARKING to "주차",
                        ChangeLogType.OIL to "오일",
                        ChangeLogType.INSURANCE to "보험"
                    ).forEach { (type, shortLabel) ->
                        HistoryFilterChip(
                            label = shortLabel,
                            selected = selectedFilter == type,
                            onClick = { selectedFilter = if (selectedFilter == type) null else type },
                            testTagName = "history_filter_${type.name}"
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when (historyState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = historyState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ios.red
                        )
                    }
                }
                is UiState.Success -> {
                    if (filteredLogs.isEmpty()) {
                        val legacyAudit = remember(vehicle.statusUpdatedBy, vehicle.statusUpdatedAt) {
                            vehicle.formattedStatusAudit()
                        }
                        if (selectedFilter == null && !legacyAudit.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.55f else 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IosStatusBadge(
                                            label = "최근 상태",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = legacyAudit,
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                            color = ios.secondaryLabel
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "${vehicle.statusEnum().labelKo}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = ios.secondaryLabel,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "수정 내역 없음",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ios.secondaryLabel
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { log ->
                                ChangeLogCard(log = log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    val ios = LocalIosColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else ios.groupedSecondary,
        modifier = Modifier.testTag(testTagName)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ChangeLogCard(log: VehicleChangeLog) {
    val ios = LocalIosColors.current
    val type = log.changeTypeEnum()
    val accent = when (type) {
        ChangeLogType.STATUS -> MaterialTheme.colorScheme.primary
        ChangeLogType.PARKING -> ios.indigo
        ChangeLogType.OIL -> ios.orange
        ChangeLogType.INSURANCE -> ios.green
        ChangeLogType.CREATED -> ios.secondaryLabel
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ios.groupedSecondary.copy(alpha = if (ios.isDark) 0.55f else 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("change_log_card_${log.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IosStatusBadge(label = type.labelKo, color = accent)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = ios.secondaryLabel,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = log.editorNickname.ifBlank { "사용자" },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = log.formattedDateTime(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = ios.secondaryLabel
                )
            }

            Spacer(Modifier.height(10.dp))
            IosHairlineDivider(startIndent = 0.dp)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                log.changes.forEach { entry ->
                    ChangeDiffLine(entry = entry, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun ChangeDiffLine(entry: String, accent: Color) {
    val ios = LocalIosColors.current
    val colonIdx = entry.indexOf(": ")
    val field = if (colonIdx > 0) entry.substring(0, colonIdx) else ""
    val detail = if (colonIdx > 0) entry.substring(colonIdx + 2) else entry
    val arrowParts = detail.split(" → ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (field.isNotEmpty()) {
            Text(
                text = field,
                style = MaterialTheme.typography.labelMedium,
                color = ios.secondaryLabel,
                modifier = Modifier.padding(end = 10.dp)
            )
        }

        if (arrowParts.size == 2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = arrowParts[0],
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = ios.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = arrowParts[1],
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
