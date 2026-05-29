package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import com.example.security.SecurePreferenceHelper
import com.example.ui.locale.Localizer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VaultViewModel
import com.example.ui.viewmodel.VaultViewModelFactory

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: VaultViewModel
    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var inactivityJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. SECURITY POLICY: Prevent Screenshots & Recordings globally
        // Note: Commented out for development/streaming emulator compatibility so the app is visible.
        // window.setFlags(
        //     WindowManager.LayoutParams.FLAG_SECURE,
        //     WindowManager.LayoutParams.FLAG_SECURE
        // )

        // 2. Initialize EncryptedPreferences store
        SecurePreferenceHelper.init(applicationContext)

        // 3. Setup core VaultViewModel and Inject database
        val factory = VaultViewModelFactory(applicationContext)
        viewModel = ViewModelProvider(this, factory)[VaultViewModel::class.java]

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            DisposableEffect(isDarkMode) {
                val style = if (isDarkMode) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = style,
                    navigationBarStyle = style
                )
                onDispose {}
            }

            MyApplicationTheme(darkTheme = isDarkMode, dynamicColor = false) {
                val isConfigured by viewModel.isConfigured.collectAsState()
                val isLocked by viewModel.isLocked.collectAsState()
                val isSelfDestructed by viewModel.isSelfDestructed.collectAsState()
                val currentLanguage by viewModel.currentLanguage.collectAsState()
                val activeTab by viewModel.activeTab.collectAsState()

                // Calculate failed attempts for warning counts
                val failedAttempts = SecurePreferenceHelper.getFailedAttempts()

                // Dynamic RTL Alignment selector
                val direction = if (Localizer.AppLanguage.fromCode(currentLanguage).isRtl) {
                    LayoutDirection.Rtl
                } else {
                    LayoutDirection.Ltr
                }

                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            isSelfDestructed -> {
                                SelfDestructStateView(currentLanguage)
                            }
                            !isConfigured -> {
                                SetupScreen(
                                    currentLanguage = currentLanguage,
                                    onSetupCompleted = { passcode ->
                                        viewModel.setupMasterPasscode(passcode)
                                    }
                                )
                            }
                            isLocked -> {
                                UnlockScreen(
                                    currentLanguage = currentLanguage,
                                    onUnlockAttempt = { passcode ->
                                        viewModel.unlockWithPasscode(passcode)
                                    },
                                    onTriggerBiometrics = {
                                        triggerBiometricAuthentication()
                                    },
                                    failedAttempts = failedAttempts
                                )
                            }
                            else -> {
                                // Dynamic Vault Dashboard
                                Scaffold(
                                    bottomBar = {
                                        MainBottomNavigationBar(
                                            activeTab = activeTab,
                                            currentLanguage = currentLanguage,
                                            onTabSelected = { tab ->
                                                viewModel.setActiveTab(tab)
                                            }
                                        )
                                    },
                                    containerColor = MaterialTheme.colorScheme.background
                                ) { innerPadding ->
                                    Box(modifier = Modifier.padding(innerPadding)) {
                                        when (activeTab) {
                                            "vault" -> VaultScreen(viewModel = viewModel, currentLanguage = currentLanguage)
                                            "notes" -> NotesScreen(viewModel = viewModel, currentLanguage = currentLanguage)
                                            "gen" -> GeneratorScreen(viewModel = viewModel, currentLanguage = currentLanguage)
                                            "settings" -> SettingsScreen(viewModel = viewModel, currentLanguage = currentLanguage)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic biometric prompt load if configured
        if (SecurePreferenceHelper.isConfigured() && SecurePreferenceHelper.isBiometricsEnabled()) {
            triggerBiometricAuthentication()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        checkInactivityAndLock()
        startInactivityTimer()
    }

    // 4. SECURITY POLICY: Lock and wipe active state on backgrounding/lockscreen (Disabled to prevent lock/state loss when backgrounded)
    override fun onStop() {
        super.onStop()
        stopInactivityTimer()
    }

    private fun checkInactivityAndLock() {
        val timeoutMinutes = SecurePreferenceHelper.getAutoLockTimeout()
        if (timeoutMinutes > 0) {
            val timeoutMillis = timeoutMinutes * 60 * 1000L
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            if (elapsed >= timeoutMillis) {
                viewModel.lockVault()
            }
        }
    }

    private fun startInactivityTimer() {
        stopInactivityTimer()
        inactivityJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000) // check every 5 seconds
                checkInactivityAndLock()
            }
        }
    }

    private fun stopInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun triggerBiometricAuthentication() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockBiometrically()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(Localizer.t("auth_required", SecurePreferenceHelper.getLanguage()))
            .setSubtitle(Localizer.t("try_biometrics", SecurePreferenceHelper.getLanguage()))
            .setNegativeButtonText(Localizer.t("cancel", SecurePreferenceHelper.getLanguage()))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun MainBottomNavigationBar(
    activeTab: String,
    currentLanguage: String,
    onTabSelected: (String) -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("vault", Icons.Default.Lock, t("passwords_tab")),
            Triple("notes", Icons.Default.Note, t("notes_tab")),
            Triple("gen", Icons.Default.Bolt, t("gen_tab")),
            Triple("settings", Icons.Default.Settings, t("settings_tab"))
        )

        items.forEach { (tab, icon, label) ->
            val isSelected = activeTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun SelfDestructStateView(
    currentLanguage: String
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFB7185),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = t("self_destruction_warning"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "SafeVault auto-wiped the SQLite disk contents cleanly.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
