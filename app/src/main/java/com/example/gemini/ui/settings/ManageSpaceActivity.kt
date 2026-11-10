package com.example.gemini.ui.settings

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiTheme
import com.example.gemini.theme.isSystemInDarkThemeRobust

class ManageSpaceActivity : ComponentActivity() {

    private val authPreferences: AuthPreferences by lazy { AuthPreferences(applicationContext) }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ManageSpaceActivity::class.java)
        }

        fun isShieldEnabled(context: Context): Boolean {
            return try {
                val state = context.packageManager.getComponentEnabledSetting(getComponentName(context))
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } catch (_: Exception) {
                true
            }
        }

        fun setShieldEnabled(context: Context, enabled: Boolean) {
            try {
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    getComponentName(context),
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                android.util.Log.e("ManageSpaceActivity", "Failed to update ManageSpaceActivity enabled state: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val initialThemeMode = remember { authPreferences.getThemeModeSync() }
            val themeMode by authPreferences.themeMode.collectAsState(initial = initialThemeMode)
            val isSystemDark = isSystemInDarkThemeRobust()
            val useDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            GeminiTheme(darkTheme = useDarkTheme) {
                ManageSpaceScreen(
                    onBack = { finish() },
                    onFactoryReset = {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.clearApplicationUserData()
                    }
                )
            }
        }
    }
}

@Composable
private fun ManageSpaceScreen(
    onBack: () -> Unit,
    onFactoryReset: () -> Unit
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var resetPasswordInput by remember { mutableStateOf("") }

    val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDarkBg) Color(0xFF09090B) else MaterialTheme.colorScheme.background
    val cardBg = if (isDarkBg) Color(0xFF141417) else Color.White
    val cardBorder = if (isDarkBg) BorderStroke(1.dp, Color(0xFF222226)) else BorderStroke(1.dp, Color(0xFFE4E4E7))

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = containerColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isDarkBg) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Gravent Storage & Space Manager",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkBg) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Exact storage breakdown, safe cleanup & data protection",
                        fontSize = 12.sp,
                        color = if (isDarkBg) Color(0xFFA1A1AA) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }

            HorizontalDivider(color = if (isDarkBg) Color(0xFF1E1E22) else Color(0xFFE4E4E7))

            StorageManagementPanel(
                cardBg = cardBg,
                cardBorder = cardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                extraBottomContent = {
                    // Danger Zone: Full Reset (Protected with Phrase Confirmation)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Danger Zone: Factory Reset",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Performs a complete wipe of all app data, removing Linux packages, accounts, workspaces, and chats.",
                                fontSize = 12.sp,
                                color = if (isDarkBg) Color(0xFFA1A1AA) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showResetConfirmation = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wipe All App Data")
                            }
                        }
                    }
                }
            )
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm Full Reset", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to wipe all Gravent data? This will permanently delete all Linux packages, workspaces, accounts, and chats.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        resetPasswordInput = ""
                        showResetPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed to Confirmation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetPasswordDialog) {
        val isMatch = resetPasswordInput.trim() == "iknowwhatiamdoing"
        AlertDialog(
            onDismissRequest = {
                showResetPasswordDialog = false
                resetPasswordInput = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Destruction",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To confirm and permanently wipe all app data and local Linux storage, type the confirmation phrase exactly as shown below:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "iknowwhatiamdoing",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ClaudeTerracotta,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    OutlinedTextField(
                        value = resetPasswordInput,
                        onValueChange = { resetPasswordInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = resetPasswordInput.isNotEmpty() && !isMatch
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isMatch) {
                            showResetPasswordDialog = false
                            resetPasswordInput = ""
                            onFactoryReset()
                        }
                    },
                    enabled = isMatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showResetPasswordDialog = false
                        resetPasswordInput = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
