package com.example.gemini.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.gemini.theme.graventNeutralButtonColors
import com.example.gemini.theme.graventTokens
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RawPayloadDialog(
    payloadJson: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val t = graventTokens()
    var wrapLines by remember { mutableStateOf(true) }

    val prettyJson = remember(payloadJson) {
        try {
            val trimmed = payloadJson.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                payloadJson
            }
        } catch (e: Exception) {
            payloadJson
        }
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = t.cardBg,
            border = BorderStroke(1.dp, t.border),
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.86f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DataObject,
                            contentDescription = null,
                            tint = t.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Raw Request Payload",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.textPrimary
                        )
                    }

                    // Wrap Lines Toggle Button
                    FilterChip(
                        selected = wrapLines,
                        onClick = { wrapLines = !wrapLines },
                        label = { Text(if (wrapLines) "Wrap: ON" else "Wrap: OFF", fontSize = 11.5.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.WrapText,
                                contentDescription = "Toggle Wrap",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = t.accentSoftBg,
                            selectedLabelColor = t.textPrimary,
                            selectedLeadingIconColor = t.accent,
                            containerColor = t.elevatedBg,
                            labelColor = t.textSecondary
                        ),
                        border = BorderStroke(1.dp, if (wrapLines) t.accent.copy(alpha = 0.55f) else t.border)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Formatted JSON payload transmitted to Google CloudCode API:",
                    fontSize = 11.5.sp,
                    color = t.textSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // JSON Content Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (t.isDark) Color(0xFF0A0B10) else t.elevatedBg,
                    border = BorderStroke(1.dp, t.subtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val boxModifier = if (wrapLines) {
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(verticalScroll)
                    } else {
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                    }

                    Box(modifier = boxModifier) {
                        Text(
                            text = prettyJson,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (t.isDark) Color(0xFF8BE9FD) else Color(0xFF0F4C81),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Raw Payload JSON", prettyJson))
                            Toast.makeText(context, "Formatted JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = t.elevatedBg,
                            contentColor = t.textPrimary
                        ),
                        border = BorderStroke(1.dp, t.border),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy JSON", fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = graventNeutralButtonColors(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
