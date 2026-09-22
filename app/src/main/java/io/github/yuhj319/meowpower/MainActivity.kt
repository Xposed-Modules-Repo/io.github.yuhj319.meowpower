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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
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

/**
 * 配置界面。Miuix（Compose）+ 底部导航 + 左右滑动切换。
 *
 * 主页只放状态、监控与总开关；功能开关按分类收进二级页。
 * 每个开关常驻一行摘要，点「详情」展开完整说明。
 */
class MainActivity : ComponentActivity() {

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
// 配置模型
// ---------------------------------------------------------------------------

private data class UiConfig(
    val enabled: Boolean = true,
    val killNightEntry: Boolean = true,
    val killNightState: Boolean = true,
    val killAllProtect: Boolean = false,
    val killCommonProtect: Boolean = false,
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

/** Hook 目标包，重启作用域即重启它。 */
private const val TARGET_PACKAGE = "com.miui.securitycenter"

private const val REFRESH_INTERVAL_MS = 2000L

/** 界面自身的偏好，与 Hook 侧读取的 RemotePreferences 无关。 */
private const val UI_PREFS = "meow_ui"
private const val KEY_HONOR_DONE = "honor_done"
/** 付款时的安装戳（PackageInfo.lastUpdateTime），覆盖安装 / 升级会变，变了就重置激活。 */
private const val KEY_HONOR_STAMP = "honor_install_stamp"

private fun currentInstallStamp(context: Context): Long = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
} catch (_: Exception) {
    -1L
}

/** 激活有效 = 标记为真且安装戳未变；覆盖安装或升级后戳变，自动回到付款页。 */
private fun isHonorValid(context: Context, uiPrefs: SharedPreferences): Boolean {
    if (!uiPrefs.getBoolean(KEY_HONOR_DONE, false)) return false
    val stamp = uiPrefs.getLong(KEY_HONOR_STAMP, -1L)
    val current = currentInstallStamp(context)
    return current != -1L && stamp == current
}

private val FAST_CHARGE_OPTIONS = listOf("不干预", "强制开启", "强制关闭")
private val BYPASS_OPTIONS = listOf("不干预", "阻止自动开启", "阻止自动停止")

private val OK_GREEN = Color(0xFF34A853)
private val WARN_RED = Color(0xFFE53935)
private val HERO_OK_BG = Color(0xFFE3F3E6)
private val HERO_WARN_BG = Color(0xFFFBE4E2)

// ---------------------------------------------------------------------------
// 根界面
// ---------------------------------------------------------------------------

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun rememberTabs(): List<Tab> = remember {
    listOf(
        Tab("主页", MiuixIcons.Home),
        Tab("充电", MiuixIcons.Refresh),
        Tab("系统", MiuixIcons.GridView),
        Tab("设置", MiuixIcons.Tune),
    )
}

@Composable
private fun MeowPowerApp() {
    val context = LocalContext.current

    // 首次进入先过一遍诚信付款页。覆盖安装 / 升级会改安装戳，激活自动重置。
    val uiPrefs = remember(context) {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    }
    var honorDone by remember {
        mutableStateOf(isHonorValid(context, uiPrefs))
    }
    val scope = rememberCoroutineScope()
    val tabs = rememberTabs()

    var service by remember { mutableStateOf(App.getService()) }
    var prefs by remember { mutableStateOf(loadRemotePrefs(service)) }
    var config by remember { mutableStateOf(readConfig(prefs)) }

    // 框架 binder 一送达就刷新状态，不用等 2 秒轮询；binder 线程回调，用 scope 切回主线程
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

    if (!honorDone) {
        HonorPayWall(
            onConfirm = {
                uiPrefs.edit()
                    ?.putBoolean(KEY_HONOR_DONE, true)
                    ?.putLong(KEY_HONOR_STAMP, currentInstallStamp(context))
                    ?.apply()
                honorDone = true
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

    /** root 重启安全服务（作用域），使新配置与 Hook 生效。 */
    fun restartScope() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                RootShell.restartPackage(TARGET_PACKAGE)
            }
            Toast.makeText(
                context,
                if (ok) "已重启安全服务" else "重启失败：root 不可用",
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
                        label = tab.label,
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
// 主页
// ---------------------------------------------------------------------------

@Composable
private fun HomePage(
    service: XposedService?,
    hasPrefs: Boolean,
    rootAvailable: Boolean?,
    chargeState: ChargeState?,
    updatedAt: Long,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("喵力全开", onRestartScope) }
        item { StatusHeroCard(service, hasPrefs) }
        item { StatusInfoCard(service, rootAvailable) }

        item {
            SmallTitle(text = "实时监控（root）")
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                MonitorBlock(state = chargeState, updatedAt = updatedAt)
            }
        }

        item {
            SmallTitle(text = "总开关")
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "启用模块",
                    brief = "关闭后所有 Hook 干预立即停止",
                    detail = "关闭后本模块不再拦截任何行为，目标应用恢复原生逻辑。" +
                            "已经通过「持久化落地」写进系统属性与 Settings 的改动不受影响，需要另行还原。",
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 二级页：充电
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
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("充电保护", onRestartScope) }
        item { SmallTitle(text = "夜间充电保护") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "拦截保护入口",
                    brief = "夜充保护不启动，不再卡 80%",
                    detail = "夜间充电保护的总入口。它一旦启动就会让底层固件把充电卡在 80%，" +
                            "拦掉后整条链路都不会启动，连恢复充电的闹钟也不会设置。",
                    checked = config.killNightEntry,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_NIGHT_ENTRY, it) { c -> c.copy(killNightEntry = it) }
                    },
                )
                SettingItem(
                    title = "拦截状态写入（兜底）",
                    brief = "防止其它路径重新开启保护",
                    detail = "夜充保护最终靠 setNightChargingState(1) 生效。" +
                            "即使保护被别的路径启动，这条拦截也会让它无法真正下达限制。",
                    checked = config.killNightState,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_NIGHT_STATE, it) { c -> c.copy(killNightState = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = "通用充电保护") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "拦截全部充电保护",
                    brief = "7 个保护策略一律不启动",
                    detail = "7 个充电保护的共同启动闸门：夜间、导航、高温、长时间、手柄、始终保护。" +
                            "该闸门在全库只有一份实现、没有任何子类重写，拦一处即可全部失效。",
                    checked = config.killAllProtect,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_ALL_PROTECT, it) { c -> c.copy(killAllProtect = it) }
                    },
                )
                SettingItem(
                    title = "拦截通用保护写入",
                    brief = "不下发 smart_chg 保护位",
                    detail = "通用保护（高温 / 导航 / 长时）通过 smart_chg 下发保护位：" +
                            "高 16 位是限制百分比（默认 80%），低 16 位是保护类型。" +
                            "拦掉后不再向底层下发任何保护指令。",
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
                    title = "限制百分比",
                    valueText = if (config.limitPercent >= 100) "不限制" else "${config.limitPercent}%",
                    valueRange = 50f..100f,
                    steps = 49,
                    enabled = enabled && !config.killCommonProtect,
                    insideMargin = PaddingValues(16.dp, 12.dp, 16.dp, 8.dp),
                )
                DetailRow(
                    "改写通用保护下发的限制阈值。设为 100 表示不下发限制；" +
                            "设为 50–99 则把保护阈值改写成该百分比。",
                )
            }
        }

        item { SmallTitle(text = "其它保护") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "pogo 底座充电保护",
                    brief = "底座充电超过 79% 时触发的保护",
                    detail = "使用 pogo 底座 / 触点充电时，电量超过 79% 会触发的充电保护，" +
                            "用于避免长时间满电放置。",
                    checked = config.killPogo,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_POGO, it) { c -> c.copy(killPogo = it) }
                    },
                )
                SettingItem(
                    title = "关闭无线静音充电",
                    brief = "恢复无线充电满功率",
                    detail = "无线静音充电会压低无线充电功率（对应 smart_chg 的 0x80 位），" +
                            "开启本项后不再压低，按满功率充电。",
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

        item { SmallTitle(text = "快充") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "快充开关",
                    items = FAST_CHARGE_OPTIONS,
                    selectedIndex = config.fastChargeMode.coerceIn(0, 2),
                    enabled = enabled,
                    onSelectedIndexChange = {
                        onInt(Config.KEY_FAST_CHARGE_MODE, it) { c -> c.copy(fastChargeMode = it) }
                    },
                )
                DetailRow(
                    "强制改写快充开关与能力判定。选「强制开启」会同时放行机型白名单与属性判定，" +
                            "用于设备本身支持快充、但因白名单或属性缺失而不生效的情况。",
                )
            }
        }

        item { SmallTitle(text = "旁路充电") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    title = "旁路充电",
                    items = BYPASS_OPTIONS,
                    selectedIndex = config.bypassMode.coerceIn(0, 2),
                    enabled = enabled,
                    onSelectedIndexChange = {
                        onInt(Config.KEY_BYPASS_MODE, it) { c -> c.copy(bypassMode = it) }
                    },
                )
                DetailRow(
                    "旁路充电时会暂停给电池充电、直接给主板供电。" +
                            "「阻止自动开启」避免边玩边充时停止充电；「阻止自动停止」避免该模式被中途关掉。",
                )
            }
        }

        item { SmallTitle(text = "电池健康度") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "伪装健康度等级",
                    brief = "改写健康等级返回值",
                    detail = "拦截电池健康等级的返回值（1–4 级，4 为最优）。" +
                            "只影响显示与等级判定，不改写底层 SOH 原始数据。",
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
                    title = "健康等级",
                    valueText = "等级 ${config.healthLevel}",
                    valueRange = 1f..4f,
                    steps = 2,
                    enabled = enabled && config.fakeHealth,
                    insideMargin = PaddingValues(16.dp, 12.dp, 16.dp, 8.dp),
                )
                TextInputRow(
                    title = "自定义健康度显示",
                    brief = "只改充电保护页那一处百分比。留空不修改，点确定保存并重启安全服务。",
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
// 二级页：系统与性能
// ---------------------------------------------------------------------------

@Composable
private fun SystemPage(
    config: UiConfig,
    onBool: (String, Boolean, (UiConfig) -> UiConfig) -> Unit,
    onRestartScope: () -> Unit,
) {
    val enabled = config.enabled
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("系统与性能", onRestartScope) }
        item { SmallTitle(text = "性能与电源") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "拦截电源模式切换",
                    brief = "性能 / 省电 / 超省电都切不了",
                    detail = "性能模式、省电模式、超省电模式的切换总闸。" +
                            "拦掉后三种模式都无法切换；读取当前状态的调用仍然放行，不影响界面显示。",
                    checked = config.killPowerMode,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_POWER_MODE, it) { c -> c.copy(killPowerMode = it) }
                    },
                )
                SettingItem(
                    title = "拦截超省电模式",
                    brief = "解除超省电的一连串限制",
                    detail = "超省电模式一进入会连锁触发：禁用应用、锁屏杀进程、冻结自启动与唤醒路径、" +
                            "动画归零、限制通知与状态栏、触控降级。掐掉入口即可一次性全部解除。",
                    checked = config.killSuperSave,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_KILL_SUPER_SAVE, it) { c -> c.copy(killSuperSave = it) }
                    },
                )
                SettingItem(
                    title = "禁止降频 / 降亮度",
                    brief = "刷新率不再被锁 60Hz",
                    detail = "进入省电模式后系统会：把屏幕刷新率锁到 60Hz（省电后手感变卡的主因）、" +
                            "降低屏幕亮度、缩短息屏超时。开启后这三项都不再被改动。",
                    checked = config.noFpsThrottle,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_FPS_THROTTLE, it) { c -> c.copy(noFpsThrottle = it) }
                    },
                )
                SettingItem(
                    title = "禁止温控降频",
                    brief = "高温不强制降档",
                    detail = "温度升高时系统会下发温控档位，写系统属性 persist.sys.thermal.config " +
                            "以及内核节点 /sys/class/thermal/thermal_message/sconfig，" +
                            "以此限制 CPU / GPU 频率。开启后不再下发。",
                    checked = config.noThermalLimit,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_THERMAL_LIMIT, it) { c -> c.copy(noThermalLimit = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = "系统与后台") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "解除 AppOps 限制",
                    brief = "7 种 MIUI 私有限制一并放行",
                    detail = "MIUI 的 7 种私有限制：10048 禁止安装应用、10049 禁止链式启动、" +
                            "10050 限制设备标识读取、10041 录屏保护、119 无障碍服务受限、" +
                            "10054 / 10055 限制 AI 读屏与控屏。",
                    checked = config.noAppOpsRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_APPOPS_RESTRICT, it) { c -> c.copy(noAppOpsRestrict = it) }
                    },
                )
                SettingItem(
                    title = "禁止清理杀后台",
                    brief = "一键清理不再杀进程",
                    detail = "一键清理会调用 killBackgroundProcesses 杀掉后台进程、批量杀进程 id，" +
                            "并把应用从最近任务列表移除。开启后这些动作都不再执行。",
                    checked = config.noKillBackground,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_KILL_BACKGROUND, it) { c -> c.copy(noKillBackground = it) }
                    },
                )
                SettingItem(
                    title = "强制允许自启动",
                    brief = "允许开机自启与自动拉起",
                    detail = "自启动限制写在平台权限库的 PERM_ID_AUTOSTART 上（禁止 = 1，允许 = 3），" +
                            "不走 Settings 也不走 AppOps，所以常规方案对它无效。" +
                            "系统禁止自启动时还会顺带杀掉该应用的进程。",
                    checked = config.allowAutostart,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_ALLOW_AUTOSTART, it) { c -> c.copy(allowAutostart = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = "网络限制") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "解除联网限制",
                    brief = "WiFi / 移动数据联网全部放行",
                    detail = "按应用逐个控制联网权限：WiFi 与移动数据分开，移动数据还分 SIM 卡槽。" +
                            "规则来源除了手动设置，还有系统预置的联网配置。" +
                            "开启后一律放行，不再限制任何应用联网。",
                    checked = config.noNetworkRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_NETWORK_RESTRICT, it) { c -> c.copy(noNetworkRestrict = it) }
                    },
                )
                SettingItem(
                    title = "解除后台联网限制",
                    brief = "后台也能正常联网",
                    detail = "系统为省电会限制应用在后台使用网络，按应用 uid 逐条设置，" +
                            "另有一个全局开关。开启后应用在后台可正常联网。",
                    checked = config.noBgNetworkRestrict,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_BG_NETWORK_RESTRICT, it) { c ->
                            c.copy(noBgNetworkRestrict = it)
                        }
                    },
                )
                SettingItem(
                    title = "解除流量超限停网",
                    brief = "套餐用尽不再自动断网",
                    detail = "套餐流量用尽时系统会直接关闭移动数据开关，表现为突然断网。" +
                            "开启后不再自动断网，流量用尽需自行留意。",
                    checked = config.noTrafficCutoff,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_TRAFFIC_CUTOFF, it) { c -> c.copy(noTrafficCutoff = it) }
                    },
                )
                SettingItem(
                    title = "解除热点流量限制",
                    brief = "热点超限不再中断",
                    detail = "热点共享流量达到上限时系统会关闭热点并弹出提示。" +
                            "开启后不再中断热点共享。",
                    checked = config.noTetherLimit,
                    enabled = enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_NO_TETHER_LIMIT, it) { c -> c.copy(noTetherLimit = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = "安装") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "解除安装签名校验",
                    brief = "第三方安装器不再被拦",
                    detail = "安装时会比对 APK 的签名摘要与白名单（小米官方签名，或系统已记录的签名），" +
                            "不一致就中止安装。第三方安装器、部分自签名应用常被这条拦住。",
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
// 二级页：设置
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
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader("设置", onRestartScope) }
        item { SmallTitle(text = "持久化落地（模块关闭后仍生效）") }
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

        item { SmallTitle(text = "调试") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SettingItem(
                    title = "输出调试日志",
                    brief = "记录每次拦截到 logcat",
                    detail = "开启后每次拦截都会写入日志，用 logcat -s MeowPower 查看。" +
                            "排查某个功能是否生效时打开，平时建议关闭。",
                    checked = config.debugLog,
                    enabled = config.enabled,
                    onCheckedChange = {
                        onBool(Config.KEY_DEBUG_LOG, it) { c -> c.copy(debugLog = it) }
                    },
                )
            }
        }

        item { SmallTitle(text = "关于") }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                AboutBlock()
            }
        }

        item {
            Text(
                text = "配置修改即时生效，无需重启目标应用。\n" +
                        "首次启用模块或更新模块代码后，需要重启安全服务进程。\n" +
                        "持久化落地不依赖模块运行，可在停用模块后继续生效。\n\n" +
                        "本模块仅修改本机行为，不涉及数据上传。",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 关于
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
        LinkRow(label = "作者", value = ABOUT_AUTHOR, url = ABOUT_AUTHOR_URL)
        LinkRow(label = "仓库", value = ABOUT_REPO_NAME, url = ABOUT_REPO_URL)
        AboutVersionRow()
    }
}

/** 关于页顶部的花哨横幅：流动渐变底 + 眨眼猫头 + 彩虹标题 + 蹦跶爪印。 */
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
                text = "喵力全开",
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

/** 一对图标对撞：从两侧靠近→撞击挤压→弹回，撞击瞬间炸出闪光。 */
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

/** 对撞的一方：位置由 [xDp] 驱动，撞击时压扁并微微回正倾斜。 */
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

/** 可点击跳转浏览器的链接行。 */
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

/** 版本行：读包管理器拿真实 versionName / versionCode。 */
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
            Text(text = "版本", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "v$name ($code)", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------------------
// 通用组件
// ---------------------------------------------------------------------------

/** 页面大标题。首页用应用名，其余页用功能分类名。 */
@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        fontSize = 32.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
    )
}

/** 大页页头：大标题 + 右上角重启作用域按钮。 */
@Composable
private fun PageHeader(title: String, onRestartScope: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "重启作用域",
            onClick = onRestartScope,
        )
    }
}

/**
 * 首次进入的诚信付款页。只做提示，不校验是否真的付过款，
 * 点「我已付款」即视为通过，结果记在本地偏好里，之后不再出现。
 */
@Composable
private fun HonorPayWall(onConfirm: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        // 内容不足一屏时整体居中，避免全挤在上半屏；超出一屏时仍可正常滚动
        verticalArrangement = Arrangement.Center,
    ) {
        item { PageTitle("付款支持") }

        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "¥8", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "本模块售价 8 元", fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "扫码支付 8 元即可进入使用，不设功能限制，也没有试用版。",
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "诚信付款，不做校验",
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            }
        }

        item {
            SmallTitle(text = "收款码")
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    QrCode(R.drawable.qr_code_1, "收款码一")
                    Spacer(modifier = Modifier.height(14.dp))
                    QrCode(R.drawable.qr_code_2, "收款码二")
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
                    text = "已支付 8 元，进入",
                    style = TextStyle(
                        color = Color(0xFF2E7CF6),
                        fontSize = 13.sp,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable { showConfirm = true },
                )
            }
            if (showConfirm) {
                PayConfirmDialog(
                    onPaid = onConfirm,
                    onDismiss = { showConfirm = false },
                )
            }
        }
    }
}

/**
 * 付款二次确认框：「我已付款」蓝色高亮，点后进入；
 * 「未付款」灰色弱化，点后关框回到付款页。点框外同样回到付款页。
 */
@Composable
private fun PayConfirmDialog(onPaid: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "付款确认", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                BasicText(
                    text = "我已付款",
                    style = TextStyle(
                        color = Color(0xFF2E7CF6),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.clickable { onPaid() },
                )
                Spacer(modifier = Modifier.height(14.dp))
                BasicText(
                    text = "未付款",
                    style = TextStyle(
                        color = Color.Gray.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    ),
                    modifier = Modifier.clickable { onDismiss() },
                )
            }
        }
    }
}

/**
 * 收款码。宽度自适应：跟随容器但不超过 [maxWidth]，在容器内水平居中。
 * 垫一层白底，保证深色模式下二维码仍然清晰可扫。
 */
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

/**
 * 带可展开详情的开关项：常驻一行摘要，点「详情」展开完整说明。
 */
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

/** 单行文本输入行：标题 + 说明，输入框与确定按钮横向排列，空值显示占位提示。输入先存草稿，点确定才写入。 */
@Composable
private fun TextInputRow(
    title: String,
    brief: String,
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
                            Text(text = "如：88%", fontSize = 15.sp, color = Color.Gray)
                        }
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                text = "确定",
                onClick = {
                    onValueChange(draft)
                    onConfirmed()
                },
                enabled = enabled && draft != value,
            )
        }
    }
}

/** 给滑块 / 下拉项用的详情行，视觉与 [SettingItem] 一致。 */
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

/**
 * 状态卡，模仿 KernelSU 主页顶部那块：纯色圆角底 + 大号状态字 + 版本行 +
 * 右侧一枚半透明徽标（正常对勾、异常感叹号）。
 */
@Composable
private fun StatusHeroCard(service: XposedService?, hasPrefs: Boolean) {
    val connected = service != null && hasPrefs
    val bg = if (connected) HERO_OK_BG else HERO_WARN_BG
    val fg = if (connected) OK_GREEN else WARN_RED
    val headline = when {
        service == null -> "未连接"
        !hasPrefs -> "配置不可用"
        else -> "工作中"
    }
    val versionLine = if (service != null) {
        "版本：${service.frameworkVersion} (${service.apiVersion})"
    } else {
        "版本：—"
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

/** 半透明圆形徽标：正常画对勾，异常画感叹号。 */
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

/**
 * 信息卡，模仿 KernelSU 主页下方那块：每项 label 在上、value 在下，
 * 项与项之间只靠间距分隔，没有分割线。
 */
@Composable
private fun StatusInfoCard(service: XposedService?, rootAvailable: Boolean?) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp),
        insideMargin = PaddingValues(vertical = 10.dp),
    ) {
        InfoItem("框架", service?.frameworkName ?: "—")
        InfoItem("版本", service?.frameworkVersion ?: "—")
        InfoItem("API", service?.apiVersion?.toString() ?: "—")
        InfoItem("作用域", scopeText(service))
        InfoItem(
            label = "Root",
            value = when (rootAvailable) {
                null -> "检测中…"
                true -> "已授权"
                false -> "未授权"
            },
            valueColor = when (rootAvailable) {
                true -> OK_GREEN
                false -> WARN_RED
                else -> null
            },
        )
        if (service == null) {
            InfoItem(
                label = "提示",
                value = "请在 LSPosed 中启用本模块，作用域勾选「安全服务」，然后重启该进程。",
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
        state == null -> "读取中…"
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
        text = if (updatedAt > 0) "更新于 ${timeText(updatedAt)} · 每 2 秒自动刷新" else "正在读取…",
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
        text = "用 root 把「禁用保护」写进系统属性与 Settings。落地后即使停用或卸载本模块，限制也不会回来。应用前会自动备份原值。",
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Text(
        text = when {
            busy -> "执行中…"
            applied -> "当前：已落地"
            else -> "当前：未落地"
        },
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        TextButton(
            text = "应用落地",
            onClick = onApply,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "还原原值",
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
