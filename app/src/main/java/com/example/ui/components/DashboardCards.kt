package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DashboardStats
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.OceanNavy
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardCards(
    stats: DashboardStats,
    activeFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedMb = String.format("%.1f", stats.totalDownloadedBytes / (1024.0 * 1024.0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HARVEST DASHBOARD",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stats.totalScanned > 0) {
                Text(
                    text = "${stats.successRatePercent}% Legal OA Rate",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (stats.successRatePercent >= 50) EmeraldSuccess else AmberWarning
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Total Scanned Card
            StatMetricCard(
                title = "Total Scanned",
                value = "${stats.totalScanned}",
                subtitle = "DOI references",
                icon = Icons.Default.Description,
                iconColor = OceanNavy,
                iconBg = Color(0xFFE2E8F0),
                isSelected = activeFilter == "ALL",
                onClick = { onFilterSelected("ALL") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_all_card")
            )

            // Downloaded OA PDFs Card
            StatMetricCard(
                title = "Downloaded",
                value = "${stats.downloadedCount}",
                subtitle = if (stats.downloadedCount > 0) "$downloadedMb MB saved" else "Free OA PDFs",
                icon = Icons.Default.CheckCircle,
                iconColor = EmeraldSuccess,
                iconBg = EmeraldSuccessBg,
                isSelected = activeFilter == "DOWNLOADED",
                onClick = { onFilterSelected("DOWNLOADED") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_downloaded_card")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Paywalled / Missing Card
            StatMetricCard(
                title = "Paywalled / Missing",
                value = "${stats.paywalledCount}",
                subtitle = "No free OA found",
                icon = Icons.Default.Lock,
                iconColor = AmberWarning,
                iconBg = AmberWarningBg,
                isSelected = activeFilter == "PAYWALLED",
                onClick = { onFilterSelected("PAYWALLED") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_paywalled_card")
            )

            // Active / Failed Card
            val activeOrPending = stats.pendingCount
            StatMetricCard(
                title = if (activeOrPending > 0) "In Progress" else "Failed / Errors",
                value = if (activeOrPending > 0) "$activeOrPending" else "${stats.failedCount}",
                subtitle = if (activeOrPending > 0) "Queued for scan" else "Network / Timeout",
                icon = if (activeOrPending > 0) Icons.Default.CloudDownload else Icons.Default.PieChart,
                iconColor = if (activeOrPending > 0) TealAccentDark else Color(0xFFEF4444),
                iconBg = if (activeOrPending > 0) Color(0xFFD6F5EE) else Color(0xFFFEE2E2),
                isSelected = activeFilter == (if (activeOrPending > 0) "ACTIVE" else "FAILED"),
                onClick = { onFilterSelected(if (activeOrPending > 0) "ACTIVE" else "FAILED") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_active_card")
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}
