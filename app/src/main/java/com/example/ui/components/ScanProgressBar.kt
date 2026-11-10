package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScanProgressState
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentDark

import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp

@Composable
fun ScanProgressBar(
    progressState: ScanProgressState,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onViewLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!progressState.isScanning && progressState.currentStep.isEmpty()) {
        return
    }

    val animatedOverallProgress by animateFloatAsState(
        targetValue = progressState.overallProgress,
        label = "overall_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("scan_progress_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DeepNavy
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header row with status & counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (progressState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TealAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(EmeraldSuccess)
                        )
                    }

                    Text(
                        text = if (progressState.isScanning) {
                            if (progressState.isPaused) "SCAN PAUSED" else "SCANNING & HARVESTING"
                        } else "SCAN FINISHED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (progressState.isScanning) TealAccent else EmeraldSuccess
                    )
                }

                Text(
                    text = "${progressState.completedCount} / ${progressState.totalCount} DOIs (${(animatedOverallProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Linear Progress Bar
            LinearProgressIndicator(
                progress = { animatedOverallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("scan_progress_bar"),
                color = TealAccent,
                trackColor = Color(0xFF2C3E50),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Current Step / Status Message
            Text(
                text = progressState.currentStep,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Current DOI / Title if available
            if (progressState.currentDoi.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DOI: ${progressState.currentDoi}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TealAccent.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // Action controls (Pause, Resume, Cancel, Logs)
            // Action controls (Pause, Resume, Cancel, Logs, Sound)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left group: Live Log + Elevator Music pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onViewLogs,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("view_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Scan Logs",
                            tint = TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Log",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    // Elevator Music status pill / toggle
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMuted) Color(0xFF2C3E50) else Color(0xFF003830))
                            .clickable(onClick = onToggleMute)
                            .padding(horizontal = 8.dp)
                            .testTag("elevator_music_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (progressState.isScanning) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                                contentDescription = if (isMuted) "Jeopardy music muted" else "Jeopardy music playing",
                                tint = if (isMuted) Color.LightGray else TealAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isMuted) "Theme Muted" else "Jeopardy Theme",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (isMuted) Color.LightGray else TealAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Chime Ding",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Ding! Done 🔔",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = EmeraldSuccess
                            )
                        }
                    }
                }

                // Scan operation controls (Pause, Resume, Cancel)
                if (progressState.isScanning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = if (progressState.isPaused) onResume else onPause,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A3B50))
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (progressState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (progressState.isPaused) "Resume" else "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B2A2A))
                                .testTag("cancel_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Scan",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
