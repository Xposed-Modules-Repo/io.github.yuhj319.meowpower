package io.github.yuhj319.meowpower

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme(
                colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                MeowPowerApp()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Config model
// ---------------------------------------------------------------------------

private data class UiConfig(
    val enabled: Boolean = true,
    val killNightEntry: Boolean = true,
    val killNightState: Boolean = true,
    val killAllProtect: Boolean = false,
    val killCommonProtect: Boolean = false,
    val overrideLimit: Boolean = true,
    val limitPercent: Int = 100,
    val fastChargeMode: Int = 0,
    val bypassMode: Int = 0,
    val killPogo: Boolean = false,
    val killWirelessSilence: Boolean = false,
    val killPowerMode: Boolean = false,
    val killSuperSave: Boolean = false,
    val noFpsThrottle: Boolean = false,
    val noThermalLimit: Boolean = false,
    val noAppOpsRestrict: Boolean = false,
    val noKillBackground: Boolean = false,
    val allowAutostart: Boolean = false,
    val noNetworkRestrict: Boolean = false,
    val noBgNetworkRestrict: Boolean = false,
    val noTrafficCutoff: Boolean = false,
    val noTetherLimit: Boolean = false,
    val noInstallVerify: Boolean = false,
    val fakeHealth: Boolean = false,
    val healthLevel: Int = 4,
    val uiHealthText: String = "",
    val debugLog: Boolean = false,
)

private fun loadRemotePrefs(service: XposedService?): SharedPreferences? =
    runCatching { service?.getRemotePreferences(Config.GROUP) }.getOrNull()

private fun readConfig(prefs: SharedPreferences?): UiConfig {
    if (prefs == null) return UiConfig()
    return UiConfig(
        enabled = prefs.getBoolean(Config.KEY_ENABLED, true),
        killNightEntry = prefs.getBoolean(Config.KEY_KILL_NIGHT_ENTRY, true),
        killNightState = prefs.getBoolean(Config.KEY_KILL_NIGHT_STATE, true),
        killAllProtect = prefs.getBoolean(Config.KEY_KILL_ALL_PROTECT, false),
        killCommonProtect = prefs.getBoolean(Config.KEY_KILL_COMMON_PROTECT, false),
        overrideLimit = prefs.getBoolean(Config.KEY_OVERRIDE_LIMIT, true),
        limitPercent = prefs.getInt(Config.KEY_LIMIT_PERCENT, 100),
        fastChargeMode = prefs.getInt(Config.KEY_FAST_CHARGE_MODE, 0),
        bypassMode = prefs.getInt(Config.KEY_BYPASS_MODE, 0),
        killPogo = prefs.getBoolean(Config.KEY_KILL_POGO, false),
        killWirelessSilence = prefs.getBoolean(Config.KEY_KILL_WIRELESS_SILENCE, false),
        killPowerMode = prefs.getBoolean(Config.KEY_KILL_POWER_MODE, false),
        killSuperSave = prefs.getBoolean(Config.KEY_KILL_SUPER_SAVE, false),
        noFpsThrottle = prefs.getBoolean(Config.KEY_NO_FPS_THROTTLE, false),
        noThermalLimit = prefs.getBoolean(Config.KEY_NO_THERMAL_LIMIT, false),
        noAppOpsRestrict = prefs.getBoolean(Config.KEY_NO_APPOPS_RESTRICT, false),
        noKillBackground = prefs.getBoolean(Config.KEY_NO_KILL_BACKGROUND, false),
        allowAutostart = prefs.getBoolean(Config.KEY_ALLOW_AUTOSTART, false),
        noNetworkRestrict = prefs.getBoolean(Config.KEY_NO_NETWORK_RESTRICT, false),
        noBgNetworkRestrict = prefs.getBoolean(Config.KEY_NO_BG_NETWORK_RESTRICT, false),
        noTrafficCutoff = prefs.getBoolean(Config.KEY_NO_TRAFFIC_CUTOFF, false),
        noTetherLimit = prefs.getBoolean(Config.KEY_NO_TETHER_LIMIT, false),
        noInstallVerify = prefs.getBoolean(Config.KEY_NO_INSTALL_VERIFY, false),
        fakeHealth = prefs.getBoolean(Config.KEY_FAKE_HEALTH, false),
        healthLevel = prefs.getInt(Config.KEY_HEALTH_LEVEL, 4),
        uiHealthText = prefs.getString(Config.KEY_UI_HEALTH_TEXT, "") ?: "",
        debugLog = prefs.getBoolean(Config.KEY_DEBUG_LOG, false),
    )
}

private const val TARGET_PACKAGE = "com.miui.securitycenter"

private const val REFRESH_INTERVAL_MS = 2000L

private const val UI_PREFS = "meow_ui"
private const val KEY_HONOR_DONE = "honor_done"
private const val KEY_HONOR_STAMP = "honor_install_stamp"
private const val KEY_TRIAL_DONE = "trial_done"
private const val KEY_TRIAL_STAMP = "trial_install_stamp"
private const val KEY_TRIAL_DATE = "trial_date"

private fun currentInstallStamp(context: Context): Long = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
} catch (_: Exception) {
    -1L
}

private fun isHonorValid(context: Context, uiPrefs: SharedPreferences): Boolean {
    if (!uiPrefs.getBoolean(KEY_HONOR_DONE, false)) return false
    val stamp = uiPrefs.getLong(KEY_HONOR_STAMP, -1L)
    val current = currentInstallStamp(context)
    return current != -1L && stamp == current
}

private fun isTrialValid(context: Context, uiPrefs: SharedPreferences): Boolean {
    if (!uiPrefs.getBoolean(KEY_TRIAL_DONE, false)) return false
    val stamp = uiPrefs.getLong(KEY_TRIAL_STAMP, -1L)
    val current = currentInstallStamp(context)
    if (current == -1L || stamp != current) return false
    return uiPrefs.getLong(KEY_TRIAL_DATE, -1L) == LocalDate.now().toEpochDay()
}

private val OK_GREEN = Color(0xFF34A853)
private val WARN_RED = Color(0xFFE53935)
private val HERO_OK_BG = Color(0xFFE3F3E6)
private val HERO_WARN_BG = Color(0xFFFBE4E2)

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

private data class Tab(val labelResId: Int, val icon: ImageVector)

@Composable
private fun rememberTabs(): List<Tab> = remember {
    listOf(
        Tab(R.string.tab_home, MiuixIcons.Home),
        Tab(R.string.tab_charge, MiuixIcons.Refresh),
        Tab(R.string.tab_system, MiuixIcons.GridView),
        Tab(R.string.tab_settings, MiuixIcons.Tune),
    )
}

@Composable
private fun MeowPowerApp() {
    val context = LocalContext.current

    val uiPrefs = remember(context) {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    }
    val honorValid = isHonorValid(context, uiPrefs)
    var honorDone by remember {
        mutableStateOf(honorValid)
    }
    var isTrial by remember {
        mutableStateOf(!honorValid && isTrialValid(context, uiPrefs))
    }
    val scope = rememberCoroutineScope()
    val tabs = rememberTabs()

    var service by remember { mutableStateOf(App.getService()) }
    var prefs by remember { mutableStateOf(loadRemotePrefs(service)) }
    var config by remember { mutableStateOf(readConfig(prefs)) }

    DisposableEffect(Unit) {
        val listener = App.Listener { latest ->
            scope.launch {
                service = latest
                prefs = loadRemotePrefs(latest)
                config = readConfig(prefs)
            }
        }
        App.setListener(listener)
        scope.launch {
            val current = App.getService()
            if (current !== service) {
                service = current
                prefs = loadRemotePrefs(current)
                config = readConfig(prefs)
            }
        }
        onDispose { App.clearListener(listener) }
    }

    if (!honorDone && !isTrial) {
        HonorPayWall(
            onConfirm = {
                uiPrefs.edit()
                    ?.putBoolean(KEY_HONOR_DONE, true)
                    ?.putLong(KEY_HONOR_STAMP, currentInstallStamp(context))
                    ?.apply()
                honorDone = true
            },
            onTrial = {
                uiPrefs.edit()
                    ?.putBoolean(KEY_TRIAL_DONE, true)
                    ?.putLong(KEY_TRIAL_STAMP, currentInstallStamp(context))
                    ?.putLong(KEY_TRIAL_DATE, LocalDate.now().toEpochDay())
                    ?.apply()
                isTrial = true
            },
        )
        return
    }

    var chargeState by remember { mutableStateOf<ChargeState?>(null) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var updatedAt by remember { mutableStateOf(0L) }

    var persistBusy by remember { mutableStateOf(false) }
    var persistApplied by remember { mutableStateOf(PersistManager.isApplied(context)) }
    var persistLog by remember { mutableStateOf("") }

    fun setBool(key: String, value: Boolean, apply: (UiConfig) -> UiConfig) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
        config = apply(config)
    }

    fun setInt(key: String, value: Int, apply: (UiConfig) -> UiConfig) {
        prefs?.edit()?.putInt(key, value)?.apply()
        config = apply(config)
    }

    fun setString(key: String, value: String, apply: (UiConfig) -> UiConfig) {
        prefs?.edit()?.putString(key, value)?.apply()
        config = apply(config)
    }

    val restartOkMsg = stringResource(R.string.restart_scope_ok)
    val restartFailMsg = stringResource(R.string.restart_scope_fail)

    fun restartScope() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                RootShell.restartPackage(TARGET_PACKAGE)
            }
            Toast.makeText(
                context,
                if (ok) restartOkMsg else restartFailMsg,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val latest = App.getService()
            if (latest !== service) {
                service = latest
                prefs = loadRemotePrefs(latest)
                config = readConfig(prefs)
            }
            rootAvailable = withContext(Dispatchers.IO) { RootShell.isRootAvailable() }
            chargeState = withContext(Dispatchers.IO) { ChargeMonitor.snapshot() }
            persistApplied = PersistManager.isApplied(context)
            updatedAt = System.currentTimeMillis()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = tab.icon,
                        label = stringResource(tab.labelResId),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) { page ->
            when (page) {
                0 -> HomePage(
                    service = service,
                    hasPrefs = prefs != null,
                    rootAvailable = rootAvailable,
                    chargeState = chargeState,
                    updatedAt = updatedAt,
                    enabled = config.enabled,
                    isTrial = isTrial,
                    onEnabledChange = {
                        setBool(Config.KEY_ENABLED, it) { c -> c.copy(enabled = it) }
                    },
                    onRestartScope = { restartScope() },
                )

                1 -> ChargePage(
                    config = config,
                    onBool = { key, value, apply -> setBool(key, value, apply) },
                    onInt = { key, value, apply -> setInt(key, value, apply) },
                    onString = { key, value, apply -> setString(key, value, apply) },
                    onRestartScope = { restartScope() },
                )

                2 -> SystemPage(
                    config = config,
                    onBool = { key, value, apply -> setBool(key, value, apply) },
                    onRestartScope = { restartScope() },
                )

                else -> SettingsPage(
                    config = config,
                    persistApplied = persistApplied,
                    persistBusy = persistBusy,
                    persistLog = persistLog,
                    onBool = { key, value, apply -> setBool(key, value, apply) },
                    onPersist = { apply ->
                        scope.launch {
                            persistBusy = true
                            persistLog = withContext(Dispatchers.IO) {
                                val log = if (apply) {
                                    PersistManager.apply(context)
                                } else {
                                    PersistManager.restore(context)
                                }
                                log.joinToString("\n")
                            }
                            persistApplied = PersistManager.isApplied(context)
                            persistBusy = false
                        }
                    },
                    onRestartScope = { restartScope() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home page
// ---------------------------------------------------------------------------

@Composable
private fun HomePage(
    service: XposedService?,
    hasPrefs: Boolean,
    rootAvailable: Boolean?,
    chargeState: ChargeState?,
    updatedAt: Long,
    enabled: Boolean,
    isTrial: Boolean = false,
    onEnabledChange: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeader(
                title = if (isTrial) stringResource(R.string.app_name_unlicensed) else stringResource(R.string.app_name),
                titleColor = if (isTrial) WARN_RED else null,
                onRestartScope = onRestartScope,
            )
        }
        item { StatusHeroCard(service, hasPrefs) }
        item { StatusInfoCard(service, rootAvailable) }

        item {
            SmallTitle(text = stringResource(R.string.section_monitor))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                MonitorBlock(state = chargeState, updatedAt = updatedAt)
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.section_master))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.master_title),
                    brief = stringResource(R.string.master_brief),
                    detail = stringResource(R.string.master_detail),
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Charge page
// ---------------------------------------------------------------------------

@Composable
private fun ChargePage(
    config: UiConfig,
    onBool: (String, Boolean, (UiConfig) -> UiConfig) -> Unit,
    onInt: (String, Int, (UiConfig) -> UiConfig) -> Unit,
    onString: (String, String, (UiConfig) -> UiConfig) -> Unit,
    onRestartScope: () -> Unit,
) {
    val enabled = config.enabled
    val fastChargeOptions = listOf(
        stringResource(R.string.fast_off),
        stringResource(R.string.fast_on),
        stringResource(R.string.fast_disable),
    )
    val bypassOptions = listOf(
        stringResource(R.string.bypass_off),
        stringResource(R.string.bypass_block_on),
        stringResource(R.string.bypass_block_off),
    )

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(stringResource(R.string.page_charge), onRestartScope) }
        item { SmallTitle(text = stringResource(R.string.section_night)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.night_entry_title),
                    brief = stringResource(R.string.night_entry_brief),
                    detail = stringResource(R.string.night_entry_detail),
                    checked = config.killNightEntry,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_NIGHT_ENTRY, it) { c -> c.copy(killNightEntry = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.night_state_title),
                    brief = stringResource(R.string.night_state_brief),
                    detail = stringResource(R.string.night_state_detail),
                    checked = config.killNightState,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_NIGHT_STATE, it) { c -> c.copy(killNightState = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_general_protect)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.protect_all_title),
                    brief = stringResource(R.string.protect_all_brief),
                    detail = stringResource(R.string.protect_all_detail),
                    checked = config.killAllProtect,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_ALL_PROTECT, it) { c -> c.copy(killAllProtect = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.protect_common_title),
                    brief = stringResource(R.string.protect_common_brief),
                    detail = stringResource(R.string.protect_common_detail),
                    checked = config.killCommonProtect,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_COMMON_PROTECT, it) { c -> c.copy(killCommonProtect = it) }
                    },
                )
                SliderPreference(
                    value = config.limitPercent.toFloat(),
                    onValueChange = {
                        val v = it.toInt().coerceIn(50, 100)
                        onInt(Config.KEY_LIMIT_PERCENT, v) { c -> c.copy(limitPercent = v) }
                    },
                    title = stringResource(R.string.limit_title),
                    valueText = if (config.limitPercent >= 100) stringResource(R.string.limit_no_limit) else "${config.limitPercent}%",
                    valueRange = 50f..100f,
                    steps = 49,
                    enabled = enabled && !config.killCommonProtect && config.overrideLimit,
                    insideMargin = PaddingValues(16.dp, 12.dp, 16.dp, 8.dp),
                )
                DetailRow(stringResource(R.string.limit_detail))
                SettingItem(
                    title = stringResource(R.string.override_limit_title),
                    brief = stringResource(R.string.override_limit_brief),
                    detail = stringResource(R.string.override_limit_detail),
                    checked = config.overrideLimit,
                    enabled = enabled && !config.killCommonProtect,
                    onCheckedChange = {
                        onBool(Config.KEY_OVERRIDE_LIMIT, it) { c -> c.copy(overrideLimit = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_other)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.pogo_title),
                    brief = stringResource(R.string.pogo_brief),
                    detail = stringResource(R.string.pogo_detail),
                    checked = config.killPogo,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_POGO, it) { c -> c.copy(killPogo = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.wireless_title),
                    brief = stringResource(R.string.wireless_brief),
                    detail = stringResource(R.string.wireless_detail),
                    checked = config.killWirelessSilence,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_WIRELESS_SILENCE, it) { c ->
                            c.copy(killWirelessSilence = it)
                        }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_fast)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.fast_charge_title),
                    items = fastChargeOptions,
                    selectedIndex = config.fastChargeMode.coerceIn(0, 2),
                    enabled = enabled,
                    onSelectedIndexChange = {
                        onInt(Config.KEY_FAST_CHARGE_MODE, it) { c -> c.copy(fastChargeMode = it) }
                    },
                )
                DetailRow(stringResource(R.string.fast_charge_detail))
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_bypass)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.bypass_title),
                    items = bypassOptions,
                    selectedIndex = config.bypassMode.coerceIn(0, 2),
                    enabled = enabled,
                    onSelectedIndexChange = {
                        onInt(Config.KEY_BYPASS_MODE, it) { c -> c.copy(bypassMode = it) }
                    },
                )
                DetailRow(stringResource(R.string.bypass_detail))
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_health)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.health_fake_title),
                    brief = stringResource(R.string.health_fake_brief),
                    detail = stringResource(R.string.health_fake_detail),
                    checked = config.fakeHealth,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_FAKE_HEALTH, it) { c -> c.copy(fakeHealth = it) }
                    },
                )
                SliderPreference(
                    value = config.healthLevel.toFloat(),
                    onValueChange = {
                        val v = it.toInt().coerceIn(1, 4)
                        onInt(Config.KEY_HEALTH_LEVEL, v) { c -> c.copy(healthLevel = v) }
                    },
                    title = stringResource(R.string.health_level_title),
                    valueText = stringResource(R.string.health_level_value, config.healthLevel),
                    valueRange = 1f..4f,
                    steps = 2,
                    enabled = enabled && config.fakeHealth,
                    insideMargin = PaddingValues(16.dp, 12.dp, 16.dp, 8.dp),
                )
                TextInputRow(
                    title = stringResource(R.string.health_custom_title),
                    brief = stringResource(R.string.health_custom_brief),
                    placeholder = stringResource(R.string.health_custom_placeholder),
                    value = config.uiHealthText,
                    enabled = enabled,
                    onValueChange = {
                        onString(Config.KEY_UI_HEALTH_TEXT, it) { c -> c.copy(uiHealthText = it) }
                    },
                    onConfirmed = { onRestartScope() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// System & Performance page
// ---------------------------------------------------------------------------

@Composable
private fun SystemPage(
    config: UiConfig,
    onBool: (String, Boolean, (UiConfig) -> UiConfig) -> Unit,
    onRestartScope: () -> Unit,
) {
    val enabled = config.enabled
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(stringResource(R.string.page_system), onRestartScope) }
        item { SmallTitle(text = stringResource(R.string.section_perf)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.perf_power_mode_title),
                    brief = stringResource(R.string.perf_power_mode_brief),
                    detail = stringResource(R.string.perf_power_mode_detail),
                    checked = config.killPowerMode,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_POWER_MODE, it) { c -> c.copy(killPowerMode = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.perf_super_save_title),
                    brief = stringResource(R.string.perf_super_save_brief),
                    detail = stringResource(R.string.perf_super_save_detail),
                    checked = config.killSuperSave,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_SUPER_SAVE, it) { c -> c.copy(killSuperSave = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.perf_fps_title),
                    brief = stringResource(R.string.perf_fps_brief),
                    detail = stringResource(R.string.perf_fps_detail),
                    checked = config.noFpsThrottle,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_FPS_THROTTLE, it) { c -> c.copy(noFpsThrottle = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.perf_thermal_title),
                    brief = stringResource(R.string.perf_thermal_brief),
                    detail = stringResource(R.string.perf_thermal_detail),
                    checked = config.noThermalLimit,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_THERMAL_LIMIT, it) { c -> c.copy(noThermalLimit = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_system)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.sys_appops_title),
                    brief = stringResource(R.string.sys_appops_brief),
                    detail = stringResource(R.string.sys_appops_detail),
                    checked = config.noAppOpsRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_APPOPS_RESTRICT, it) { c -> c.copy(noAppOpsRestrict = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.sys_kill_bg_title),
                    brief = stringResource(R.string.sys_kill_bg_brief),
                    detail = stringResource(R.string.sys_kill_bg_detail),
                    checked = config.noKillBackground,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_KILL_BACKGROUND, it) { c -> c.copy(noKillBackground = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.sys_autostart_title),
                    brief = stringResource(R.string.sys_autostart_brief),
                    detail = stringResource(R.string.sys_autostart_detail),
                    checked = config.allowAutostart,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_ALLOW_AUTOSTART, it) { c -> c.copy(allowAutostart = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_network)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.net_restrict_title),
                    brief = stringResource(R.string.net_restrict_brief),
                    detail = stringResource(R.string.net_restrict_detail),
                    checked = config.noNetworkRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_NETWORK_RESTRICT, it) { c -> c.copy(noNetworkRestrict = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.net_bg_title),
                    brief = stringResource(R.string.net_bg_brief),
                    detail = stringResource(R.string.net_bg_detail),
                    checked = config.noBgNetworkRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_BG_NETWORK_RESTRICT, it) { c ->
                            c.copy(noBgNetworkRestrict = it)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.net_traffic_title),
                    brief = stringResource(R.string.net_traffic_brief),
                    detail = stringResource(R.string.net_traffic_detail),
                    checked = config.noTrafficCutoff,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_TRAFFIC_CUTOFF, it) { c -> c.copy(noTrafficCutoff = it) }
                    },
                )
                SettingItem(
                    title = stringResource(R.string.net_tether_title),
                    brief = stringResource(R.string.net_tether_brief),
                    detail = stringResource(R.string.net_tether_detail),
                    checked = config.noTetherLimit,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_TETHER_LIMIT, it) { c -> c.copy(noTetherLimit = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_install)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.install_verify_title),
                    brief = stringResource(R.string.install_verify_brief),
                    detail = stringResource(R.string.install_verify_detail),
                    checked = config.noInstallVerify,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_INSTALL_VERIFY, it) { c -> c.copy(noInstallVerify = it) }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings page
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPage(
    config: UiConfig,
    persistApplied: Boolean,
    persistBusy: Boolean,
    persistLog: String,
    onBool: (String, Boolean, (UiConfig) -> UiConfig) -> Unit,
    onPersist: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
) {
    val context = LocalContext.current
    val langOptions = listOf(
        stringResource(R.string.language_follow_system),
        stringResource(R.string.language_zh),
        stringResource(R.string.language_en),
    )
    val langValues = listOf(LocaleHelper.LANG_SYSTEM, LocaleHelper.LANG_ZH, LocaleHelper.LANG_EN)
    val currentLang = remember { LocaleHelper.getSavedLanguage(context) }
    val selectedLangIndex = langValues.indexOf(currentLang).coerceAtLeast(0)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(stringResource(R.string.page_settings), onRestartScope) }

        item { SmallTitle(text = stringResource(R.string.section_language)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.language_title),
                    items = langOptions,
                    selectedIndex = selectedLangIndex,
                    onSelectedIndexChange = { idx ->
                        val lang = langValues[idx]
                        LocaleHelper.saveLanguage(context, lang)
                        (context as? ComponentActivity)?.recreate()
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_persist)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                PersistBlock(
                    applied = persistApplied,
                    busy = persistBusy,
                    log = persistLog,
                    onApply = { onPersist(true) },
                    onRestore = { onPersist(false) },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_debug)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = stringResource(R.string.debug_title),
                    brief = stringResource(R.string.debug_brief),
                    detail = stringResource(R.string.debug_detail),
                    checked = config.debugLog,
                    enabled = config.enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_DEBUG_LOG, it) { c -> c.copy(debugLog = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = stringResource(R.string.section_about)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                AboutBlock()
            }
        }

        item {
            Text(
                text = stringResource(R.string.footer_text),
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

private const val ABOUT_AUTHOR = "yuhj319"
private const val ABOUT_AUTHOR_URL = "https://github.com/yuhj319"
private const val ABOUT_REPO_NAME = "io.github.yuhj319.meowpower"
private const val ABOUT_REPO_URL = "https://github.com/Xposed-Modules-Repo/io.github.yuhj319.meowpower"

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun appVersion(context: Context): Pair<String, String> {
    val info = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
    return (info?.versionName ?: "—") to (info?.longVersionCode?.toString() ?: "—")
}

@Composable
private fun AboutBlock() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        AboutHero()
        Spacer(modifier = Modifier.height(6.dp))
        LinkRow(label = stringResource(R.string.about_author), value = ABOUT_AUTHOR, url = ABOUT_AUTHOR_URL)
        LinkRow(label = stringResource(R.string.about_repo), value = ABOUT_REPO_NAME, url = ABOUT_REPO_URL)
        AboutVersionRow()
    }
}

@Composable
private fun AboutHero() {
    val transition = rememberInfiniteTransition(label = "about")
    val slide by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slide",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF7B2FF7),
                        Color(0xFFF107A3),
                        Color(0xFFFF8A00),
                        Color(0xFF7B2FF7),
                    ),
                    start = Offset(slide * 900f, 0f),
                    end = Offset(slide * 900f + 700f, 900f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 22.dp),
        ) {
            CollidingIcons(transition = transition)
            Spacer(modifier = Modifier.height(10.dp))
            BasicText(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF3B30),
                            Color(0xFFFF9500),
                            Color(0xFFFFCC00),
                            Color(0xFF34C759),
                            Color(0xFF32ADE6),
                            Color(0xFFBF5AF2),
                            Color(0xFFFF3B30),
                        ),
                        start = Offset(slide * 700f, slide * 200f),
                        end = Offset(slide * 700f + 420f, slide * 200f + 160f),
                        tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                    ),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
        }
    }
}

@Composable
private fun CollidingIcons(transition: InfiniteTransition) {
    val approach by transition.animateFloat(
        initialValue = -92f,
        targetValue = -92f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                -92f at 0
                -36f at 900
                -36f at 1100
                -92f at 2400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "approach",
    )
    val squash by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                1f at 0
                1f at 800
                0.8f at 950
                1f at 1150
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "squash",
    )
    val flash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                0f at 0
                0f at 850
                1f at 950
                0f at 1200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "flash",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (flash > 0.01f) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val col = Color.White.copy(alpha = 0.9f * flash)
                drawCircle(color = col.copy(alpha = 0.3f * flash), radius = 24f + 56f * flash, center = c)
                val r = 26f + 64f * flash
                val w = 7f
                drawLine(col, c + Offset(-r, 0f), c + Offset(r, 0f), w, StrokeCap.Round)
                drawLine(col, c + Offset(0f, -r), c + Offset(0f, r), w, StrokeCap.Round)
                drawLine(col, c + Offset(-r * 0.7f, -r * 0.7f), c + Offset(r * 0.7f, r * 0.7f), w, StrokeCap.Round)
                drawLine(col, c + Offset(-r * 0.7f, r * 0.7f), c + Offset(r * 0.7f, -r * 0.7f), w, StrokeCap.Round)
            }
        }
        IconFighter(xDp = approach, squash = squash, tilt = -1f)
        IconFighter(xDp = -approach, squash = squash, tilt = 1f)
    }
}

@Composable
private fun BoxScope.IconFighter(xDp: Float, squash: Float, tilt: Float) {
    val progress = ((xDp + 36f) / -56f).coerceIn(0f, 1f)
    Image(
        painter = painterResource(R.drawable.sign),
        contentDescription = "meow",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = xDp.dp)
            .size(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .graphicsLayer {
                scaleX = squash
                scaleY = 1f + (1f - squash) * 0.6f
                rotationZ = tilt * progress * 10f
            },
    )
}

@Composable
private fun LinkRow(label: String, value: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 13.sp, color = Color.Gray)
        }
        Text(text = "↗", fontSize = 15.sp, color = Color.Gray)
    }
}

@Composable
private fun AboutVersionRow() {
    val context = LocalContext.current
    val (name, code) = remember(context) { appVersion(context) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.about_version), fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "v$name ($code)", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        fontSize = 32.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
    )
}

@Composable
private fun PageHeader(title: String, onRestartScope: () -> Unit, titleColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            color = titleColor ?: Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = stringResource(R.string.restart_scope),
            onClick = onRestartScope,
        )
    }
}

@Composable
private fun HonorPayWall(onConfirm: () -> Unit, onTrial: () -> Unit) {
    var dialogStep by remember { mutableStateOf(0) }
    var trialUnlocked by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        item { PageTitle(stringResource(R.string.pay_title)) }

        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.pay_price), fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = stringResource(R.string.pay_price_desc), fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.pay_desc),
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pay_trial_note),
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            }
        }

        item {
            SmallTitle(text = stringResource(R.string.pay_qr_title))
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    QrCode(R.drawable.qr_code_1, stringResource(R.string.pay_qr_1))
                    Spacer(modifier = Modifier.height(14.dp))
                    QrCode(R.drawable.qr_code_2, stringResource(R.string.pay_qr_2))
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(R.string.pay_enter),
                    style = TextStyle(
                        color = Color(0xFF2E7CF6),
                        fontSize = 13.sp,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable { dialogStep = 1 },
                )
            }
            if (trialUnlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = stringResource(R.string.pay_trial_enter),
                        style = TextStyle(
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline,
                        ),
                        modifier = Modifier.clickable { onTrial() },
                    )
                }
            }
            if (dialogStep == 1) {
                PayConfirmDialog(
                    title = stringResource(R.string.pay_confirm_title),
                    unpaidFirst = true,
                    onPaid = { dialogStep = 2 },
                    onUnpaid = { dialogStep = 2 },
                    onDismiss = { dialogStep = 0 },
                )
            } else if (dialogStep == 2) {
                PayConfirmDialog(
                    title = stringResource(R.string.pay_confirm_again),
                    unpaidFirst = false,
                    onPaid = {
                        dialogStep = 0
                        onConfirm()
                    },
                    onUnpaid = {
                        dialogStep = 0
                        trialUnlocked = true
                    },
                    onDismiss = { dialogStep = 0 },
                )
            }
        }
    }
}

@Composable
private fun PayConfirmDialog(
    title: String,
    unpaidFirst: Boolean,
    onPaid: () -> Unit,
    onUnpaid: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = title, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.pay_confirm_question), fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (unpaidFirst) {
                        UnpaidOption(onClick = onUnpaid)
                        PaidOption(onClick = onPaid)
                    } else {
                        PaidOption(onClick = onPaid)
                        UnpaidOption(onClick = onUnpaid)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnpaidOption(onClick: () -> Unit) {
    BasicText(
        text = stringResource(R.string.pay_unpaid),
        style = TextStyle(
            color = Color(0xFF2E7CF6),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PaidOption(onClick: () -> Unit) {
    BasicText(
        text = stringResource(R.string.pay_paid),
        style = TextStyle(
            color = Color.Gray.copy(alpha = 0.6f),
            fontSize = 12.sp,
        ),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun QrCode(resId: Int, desc: String, maxWidth: Dp = 300.dp) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = desc,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun SettingItem(
    title: String,
    brief: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SwitchPreference(
        title = title,
        summary = brief,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )

    ExpandableDetail(detail = detail, expanded = expanded, onToggle = { expanded = !expanded })
}

@Composable
private fun TextInputRow(
    title: String,
    brief: String,
    placeholder: String = "",
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onConfirmed: () -> Unit = {},
) {
    val inputColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    var draft by remember(value) { mutableStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = brief, fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = inputColor),
                cursorBrush = SolidColor(inputColor),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(text = placeholder, fontSize = 15.sp, color = Color.Gray)
                        }
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                text = stringResource(R.string.btn_confirm),
                onClick = {
                    onValueChange(draft)
                    onConfirmed()
                },
                enabled = enabled && draft != value,
            )
        }
    }
}

@Composable
private fun DetailRow(detail: String) {
    var expanded by remember { mutableStateOf(false) }
    ExpandableDetail(detail = detail, expanded = expanded, onToggle = { expanded = !expanded })
}

@Composable
private fun ExpandableDetail(detail: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp)
            .padding(bottom = if (expanded) 2.dp else 10.dp),
    ) {
        Text(
            text = if (expanded) "收起 ▴" else "详情 ▾",
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    if (expanded) {
        Text(
            text = detail,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun StatusHeroCard(service: XposedService?, hasPrefs: Boolean) {
    val connected = service != null && hasPrefs
    val bg = if (connected) HERO_OK_BG else HERO_WARN_BG
    val fg = if (connected) OK_GREEN else WARN_RED
    val headline = when {
        service == null -> stringResource(R.string.status_not_connected)
        !hasPrefs -> stringResource(R.string.status_config_unavailable)
        else -> stringResource(R.string.status_working)
    }
    val versionLine = if (service != null) {
        stringResource(R.string.status_version, service.frameworkVersion, service.apiVersion.toString())
    } else {
        stringResource(R.string.status_version_none)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg),
    ) {
        HeroBadge(
            ok = connected,
            color = fg,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(62.dp),
        )
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = headline, fontSize = 22.sp, color = fg)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = versionLine, fontSize = 13.sp, color = fg)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (service != null) service.frameworkName else "LSPosed",
                fontSize = 13.sp,
                color = fg,
            )
        }
    }
}

@Composable
private fun HeroBadge(ok: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = color.copy(alpha = 0.16f), radius = size.minDimension / 2f)
        val stroke = size.minDimension * 0.08f
        val mark = color.copy(alpha = 0.8f)
        if (ok) {
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.73f, size.height * 0.34f)
            }
            drawPath(path = path, color = mark, style = Stroke(width = stroke, cap = StrokeCap.Round))
        } else {
            drawLine(
                color = mark,
                start = Offset(size.width * 0.5f, size.height * 0.3f),
                end = Offset(size.width * 0.5f, size.height * 0.58f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = mark,
                radius = stroke * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.72f),
            )
        }
    }
}

@Composable
private fun StatusInfoCard(service: XposedService?, rootAvailable: Boolean?) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
        insideMargin = PaddingValues(vertical = 10.dp),
    ) {
        InfoItem(stringResource(R.string.info_framework), service?.frameworkName ?: stringResource(R.string.info_na))
        InfoItem(stringResource(R.string.info_version), service?.frameworkVersion ?: stringResource(R.string.info_na))
        InfoItem(stringResource(R.string.info_api), service?.apiVersion?.toString() ?: stringResource(R.string.info_na))
        InfoItem(stringResource(R.string.info_scope), scopeText(service))
        InfoItem(
            label = stringResource(R.string.info_root),
            value = when (rootAvailable) {
                null -> stringResource(R.string.info_root_checking)
                true -> stringResource(R.string.info_root_granted)
                false -> stringResource(R.string.info_root_denied)
            },
            valueColor = when (rootAvailable) {
                true -> OK_GREEN
                false -> WARN_RED
                else -> null
            },
        )
        if (service == null) {
            InfoItem(
                label = stringResource(R.string.info_hint),
                value = stringResource(R.string.info_hint_detail),
                valueColor = WARN_RED,
            )
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, valueColor: Color? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text = label, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = valueColor ?: Color.Gray,
        )
    }
}

@Composable
private fun MonitorBlock(state: ChargeState?, updatedAt: Long) {
    val summary = when {
        state == null -> stringResource(R.string.monitor_reading)
        !state.ok -> state.message
        else -> "${state.capacity}%  ${state.status}  ${fmt("A", state.currentMa / 1000f)}  ${fmt("W", state.powerMw() / 1000f)}"
    }
    Text(
        text = summary,
        fontSize = 20.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
    )

    if (state != null && state.ok) {
        val detail = buildString {
            append("电压 ${fmt("V", state.voltageMv / 1000f)} · 温度 ${fmt("℃", state.tempC)} · 健康 ${state.health}")
            if (state.chargeCounter >= 0) append(" · 电量计 ${state.chargeCounter} mAh")
            append("\n快充开关 ${state.fastCharge} · 旁路充电 ${state.bypassState}")
            append("\n夜充支持位 ${state.prop("persist.vendor.night.charge")} · smartchg ${state.prop("persist.vendor.smartchg")}")
            append("\n高温保护 ${state.prop("persist.vendor.battery.high.temp.protect")} · AI 预测 ${state.prop("persist.vendor.battery.ai.predict")}")
        }
        Text(
            text = detail,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }

    Text(
        text = if (updatedAt > 0) stringResource(R.string.monitor_auto_refresh, timeText(updatedAt)) else stringResource(R.string.monitor_loading),
        fontSize = 11.sp,
        color = Color.Gray,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
    )
}

@Composable
private fun PersistBlock(
    applied: Boolean,
    busy: Boolean,
    log: String,
    onApply: () -> Unit,
    onRestore: () -> Unit,
) {
    Text(
        text = stringResource(R.string.persist_desc),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Text(
        text = when {
            busy -> stringResource(R.string.persist_working)
            applied -> stringResource(R.string.persist_state_applied)
            else -> stringResource(R.string.persist_state_clean)
        },
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        TextButton(
            text = stringResource(R.string.persist_apply),
            onClick = onApply,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = stringResource(R.string.persist_restore),
            onClick = onRestore,
            enabled = !busy,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
    if (log.isNotEmpty()) {
        Text(
            text = log,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun scopeText(service: XposedService?): String {
    if (service == null) {
        return "—"
    }
    return runCatching {
        service.scope?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "—"
    }.getOrDefault("—")
}

private fun timeText(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

private fun fmt(unit: String, value: Float): String =
    String.format(java.util.Locale.getDefault(), "%.2f %s", value, unit)
