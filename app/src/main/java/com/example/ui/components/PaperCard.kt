package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PaperRecord
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.PurpleHybrid
import com.example.ui.theme.PurpleHybridBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.RoseErrorBg
import com.example.ui.theme.TealAccentDark

@Composable
fun PaperCard(
    paper: PaperRecord,
    onOpenPdf: (PaperRecord) -> Unit,
    onOpenWeb: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDetails: (PaperRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onDetails(paper) }
            .testTag("paper_card_${paper.doi.replace('/', '_')}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Badges (OA type & Download status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // OA Status Tag
                OaStatusBadge(oaStatus = paper.oaStatus, isOa = paper.isOa)

                // Download Status Pill
                DownloadStatusBadge(status = paper.downloadStatus, sizeBytes = paper.fileSizeBytes)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = paper.title.ifBlank { "DOI: ${paper.doi}" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Authors & Journal info
            if (paper.authors.isNotBlank() || paper.journal.isNotBlank() || paper.year != null) {
                val metaParts = mutableListOf<String>()
                if (paper.authors.isNotBlank()) metaParts.add(paper.authors)
                if (paper.journal.isNotBlank()) metaParts.add(paper.journal)
                if (paper.year != null) metaParts.add(paper.year.toString())

                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // DOI Pill with Copy Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        copyToClipboard(context, "DOI", paper.doi)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = paper.doi,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy DOI",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left status description
                Text(
                    text = paper.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (paper.downloadStatus) {
                        "DOWNLOADED" -> EmeraldSuccess
                        "PAYWALLED" -> AmberWarning
                        "FAILED" -> RoseError
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                // Action buttons based on status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (paper.downloadStatus) {
                        "DOWNLOADED" -> {
                            Button(
                                onClick = { onOpenPdf(paper) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccentDark
                                ),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("open_pdf_${paper.doi.replace('/', '_')}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        "PAYWALLED" -> {
                            OutlinedButton(
                                onClick = { onOpenWeb("https://doi.org/${paper.doi}") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("view_doi_${paper.doi.replace('/', '_')}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Publisher", fontSize = 12.sp)
                            }
                        }

                        "FAILED" -> {
                            OutlinedButton(
                                onClick = { onRetry(paper.doi) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("retry_${paper.doi.replace('/', '_')}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 12.sp)
                            }
                        }

                        "RESOLVING", "DOWNLOADING" -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = TealAccentDark
                            )
                        }

                        else -> {
                            OutlinedButton(
                                onClick = { onRetry(paper.doi) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Scan", fontSize = 12.sp)
                            }
                        }
                    }

                    // Details button
                    IconButton(
                        onClick = { onDetails(paper) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Paper details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OaStatusBadge(oaStatus: String, isOa: Boolean) {
    val (label, bg, fg, icon) = when (oaStatus.lowercase()) {
        "gold" -> Quadruple("Gold OA", EmeraldSuccessBg, EmeraldSuccess, Icons.Default.LockOpen)
        "green" -> Quadruple("Green OA", Color(0xFFE0F2F1), TealAccentDark, Icons.Default.LockOpen)
        "hybrid" -> Quadruple("Hybrid OA", PurpleHybridBg, PurpleHybrid, Icons.Default.LockOpen)
        "bronze" -> Quadruple("Bronze Free", AmberWarningBg, AmberWarning, Icons.Default.LockOpen)
        "closed" -> Quadruple("Paywalled / Closed", Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.Lock)
        else -> if (isOa) {
            Quadruple("Open Access", EmeraldSuccessBg, EmeraldSuccess, Icons.Default.LockOpen)
        } else {
            Quadruple("Subscription", Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.Lock)
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun DownloadStatusBadge(status: String, sizeBytes: Long) {
    val mbText = if (sizeBytes > 0) {
        val mb = sizeBytes / (1024.0 * 1024.0)
        String.format("%.1f MB", mb)
    } else ""

    val (label, bg, fg, icon) = when (status) {
        "DOWNLOADED" -> Quadruple(
            if (mbText.isNotEmpty()) "Downloaded • $mbText" else "Downloaded",
            EmeraldSuccessBg,
            EmeraldSuccess,
            Icons.Default.CheckCircle
        )
        "PAYWALLED" -> Quadruple("Missing OA", AmberWarningBg, AmberWarning, Icons.Default.Lock)
        "DOWNLOADING" -> Quadruple("Downloading...", Color(0xFFE0F7FA), TealAccentDark, null)
        "RESOLVING" -> Quadruple("Resolving...", Color(0xFFE2E8F0), Color(0xFF475569), null)
        "FAILED" -> Quadruple("Failed", RoseErrorBg, RoseError, Icons.Default.Warning)
        else -> Quadruple("Pending", Color(0xFFF1F5F9), Color(0xFF64748B), null)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
        } else if (status in listOf("DOWNLOADING", "RESOLVING")) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = fg
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = fg
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied DOI: $text", Toast.LENGTH_SHORT).show()
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
