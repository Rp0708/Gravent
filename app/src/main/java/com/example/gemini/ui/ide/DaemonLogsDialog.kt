package com.example.gemini.ui.ide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.daemon.DaemonStatus
import com.example.gemini.data.daemon.TermuxDaemonManager
import com.example.gemini.theme.graventPrimaryButtonColors
import com.example.gemini.theme.graventTokens
import com.example.gemini.ui.components.GraventBadge
import com.example.gemini.ui.components.GraventLogConsole
import com.example.gemini.ui.components.GraventTone
import com.example.gemini.ui.components.graventToneColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaemonLogsDialog(
    onDismiss: () -> Unit
) {
    val t = graventTokens()
    val coroutineScope = rememberCoroutineScope()
    val status by TermuxDaemonManager.status.collectAsState()
    val statusMessage by TermuxDaemonManager.statusMessage.collectAsState()
    val logs by TermuxDaemonManager.logs.collectAsState()
    val tone = when (status) {
        DaemonStatus.RUNNING -> GraventTone.SUCCESS
        DaemonStatus.STARTING -> GraventTone.WARNING
        else -> GraventTone.ERROR
    }
    val statusColors = graventToneColors(tone)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.cardBg,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = t.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Go Daemon Status & Logs",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.textPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = t.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                // Status Header Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.background,
                    border = BorderStroke(1.dp, statusColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColors.foreground, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                GraventBadge(text = status.name, tone = tone)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.textPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    TermuxDaemonManager.ensureDaemonStarted()
                                }
                            },
                            colors = graventPrimaryButtonColors(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                GraventLogConsole(
                    lines = logs,
                    title = "Live Daemon Log",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = t.accent)
            }
        }
    )
}
