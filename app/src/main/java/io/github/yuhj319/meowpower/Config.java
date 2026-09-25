package io.github.yuhj319.meowpower;

/**
 * 配置键定义。
 *
 * <p>所有配置保存在 LSPosed RemotePreferences（group = {@link #GROUP}）中，
 * 模块 App 进程通过 {@code XposedService} 写入，被 Hook 的目标进程通过
 * {@code XposedModule#getRemotePreferences} 读取，两侧共享同一份数据。</p>
 */
public final class Config {

    /** RemotePreferences 分组名，两侧必须一致。 */
    public static final String GROUP = "config";

    /** 模块总开关。 */
    public static final String KEY_ENABLED = "master_enabled";

    /** 拦截夜间充电保护入口 {@code NightChargeProtectManager.C(Context)}。 */
    public static final String KEY_KILL_NIGHT_ENTRY = "kill_night_entry";

    /** 拦截 {@code e.r(1)}（即 {@code IMiCharge.setNightChargingState(1)}）。 */
    public static final String KEY_KILL_NIGHT_STATE = "kill_night_state";

    /** 拦截所有充电保护策略的启动（{@code hg.a.l(true)}），一网打尽。 */
    public static final String KEY_KILL_ALL_PROTECT = "kill_all_protect";

    /** 拦截通用充电保护 {@code e.n(int)} / {@code e.p(String,int)}。 */
    public static final String KEY_KILL_COMMON_PROTECT = "kill_common_protect";

    /** 是否用自定义阈值覆盖通用充电保护的限制百分比。 */
    public static final String KEY_OVERRIDE_LIMIT = "override_limit";

    /** 自定义限制百分比，100 表示不做限制。 */
    public static final String KEY_LIMIT_PERCENT = "limit_percent";

    /** 快充模式：0 不干预 / 1 强制开启 / 2 强制关闭。 */
    public static final String KEY_FAST_CHARGE_MODE = "fast_charge_mode";

    /** 旁路充电模式：0 不干预 / 1 阻止自动开启 / 2 阻止自动停止。 */
    public static final String KEY_BYPASS_MODE = "bypass_mode";

    /** 拦截 pogo（底座）充电保护。 */
    public static final String KEY_KILL_POGO = "kill_pogo_protect";

    /** 强制关闭无线静音充电，恢复无线充电正常功率。 */
    public static final String KEY_KILL_WIRELESS_SILENCE = "kill_wireless_silence";

    /** 拦截电源模式切换（性能 / 省电 / 超省电）。 */
    public static final String KEY_KILL_POWER_MODE = "kill_power_mode";

    /** 拦截超省电模式的全部限制。 */
    public static final String KEY_KILL_SUPER_SAVE = "kill_super_save";

    /** 禁止省电时降刷新率 / 降亮度 / 缩短息屏。 */
    public static final String KEY_NO_FPS_THROTTLE = "no_fps_throttle";

    /** 禁止温控降频。 */
    public static final String KEY_NO_THERMAL_LIMIT = "no_thermal_limit";

    /** 强制开启游戏插帧（智能插帧，f17936c=1）。 */
    public static final String KEY_FORCE_FRAME_INSERT = "force_frame_insert";

    /** 强制开启游戏超分（超级分辨率，f17936c=2；与插帧同开时走双开 4）。 */
    public static final String KEY_FORCE_SUPER_RESOLUTION = "force_super_resolution";

    /** 强制游戏性能档（GameManager.setGameMode → GAME_MODE_PERFORMANCE=2）。 */
    public static final String KEY_FORCE_GAME_MODE = "force_game_mode";

    /** 游戏加速全局常开（gb_boosting 写 1 不写 0）。 */
    public static final String KEY_GAME_BOOST_ALWAYS = "game_boost_always";

    /** 解除 AppOps 私有码限制（10048/10049/10050/10041/119/10054/10055）。 */
    public static final String KEY_NO_APPOPS_RESTRICT = "no_appops_restrict";

    /** 禁止内存清理杀后台。 */
    public static final String KEY_NO_KILL_BACKGROUND = "no_kill_background";

    /** 强制允许自启动。 */
    public static final String KEY_ALLOW_AUTOSTART = "allow_autostart";

    /** 解除单应用联网限制（WiFi / 移动数据防火墙）。 */
    public static final String KEY_NO_NETWORK_RESTRICT = "no_network_restrict";

    /** 解除后台联网限制。 */
    public static final String KEY_NO_BG_NETWORK_RESTRICT = "no_bg_network_restrict";

    /** 解除流量超限自动停网。 */
    public static final String KEY_NO_TRAFFIC_CUTOFF = "no_traffic_cutoff";

    /** 解除热点共享流量限制。 */
    public static final String KEY_NO_TETHER_LIMIT = "no_tether_limit";

    /** 解除安装签名校验拦截。 */
    public static final String KEY_NO_INSTALL_VERIFY = "no_install_verify";

    /** 是否伪装电池健康度等级。 */
    public static final String KEY_FAKE_HEALTH = "fake_health";

    /** 伪装的健康度等级，取值 1~4（1 最差，4 最好）。 */
    public static final String KEY_HEALTH_LEVEL = "health_level";

    /** 插件：修改UI健康度 —— 自定义显示文本，为空表示不修改。 */
    public static final String KEY_UI_HEALTH_TEXT = "ui_health_text";

    /** 是否输出调试日志（TAG = MeowPower）。 */
    public static final String KEY_DEBUG_LOG = "debug_log";

    /** 限制百分比的"不限制"取值。 */
    public static final int LIMIT_UNLIMITED = 100;

    private Config() {
    }
}
