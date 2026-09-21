package io.github.yuhj319.meowpower;

import android.content.SharedPreferences;

/**
 * 配置快照。每次 Hook 被调用时从 RemotePreferences 读取一次，
 * RemotePreferences 内部带内存缓存与失效检测，读取成本很低。
 */
public final class ConfigSnapshot {

    public static final ConfigSnapshot DEFAULTS = new ConfigSnapshot(
            true,   // enabled
            true,   // killNightEntry
            true,   // killNightState
            false,  // killAllProtect
            false,  // killCommonProtect
            true,   // overrideLimit
            100,    // limitPercent
            0,      // fastChargeMode
            0,      // bypassMode
            false,  // killPogo
            false,  // killWirelessSilence
            false,  // killPowerMode
            false,  // killSuperSave
            false,  // noFpsThrottle
            false,  // noThermalLimit
            false,  // noAppOpsRestrict
            false,  // noKillBackground
            false,  // allowAutostart
            false,  // noNetworkRestrict
            false,  // noBgNetworkRestrict
            false,  // noTrafficCutoff
            false,  // noTetherLimit
            false,  // noInstallVerify
            false,  // fakeHealth
            4,      // healthLevel
            false   // debugLog
    );

    public final boolean enabled;
    public final boolean killNightEntry;
    public final boolean killNightState;
    public final boolean killAllProtect;
    public final boolean killCommonProtect;
    public final boolean overrideLimit;
    public final int limitPercent;
    public final int fastChargeMode;
    public final int bypassMode;
    public final boolean killPogo;
    public final boolean killWirelessSilence;
    public final boolean killPowerMode;
    public final boolean killSuperSave;
    public final boolean noFpsThrottle;
    public final boolean noThermalLimit;
    public final boolean noAppOpsRestrict;
    public final boolean noKillBackground;
    public final boolean allowAutostart;
    public final boolean noNetworkRestrict;
    public final boolean noBgNetworkRestrict;
    public final boolean noTrafficCutoff;
    public final boolean noTetherLimit;
    public final boolean noInstallVerify;
    public final boolean fakeHealth;
    public final int healthLevel;
    public final boolean debugLog;

    private ConfigSnapshot(boolean enabled, boolean killNightEntry, boolean killNightState,
                           boolean killAllProtect, boolean killCommonProtect, boolean overrideLimit,
                           int limitPercent, int fastChargeMode, int bypassMode,
                           boolean killPogo, boolean killWirelessSilence,
                           boolean killPowerMode, boolean killSuperSave, boolean noFpsThrottle,
                           boolean noThermalLimit, boolean noAppOpsRestrict,
                           boolean noKillBackground, boolean allowAutostart,
                           boolean noNetworkRestrict, boolean noBgNetworkRestrict,
                           boolean noTrafficCutoff, boolean noTetherLimit, boolean noInstallVerify,

                           boolean fakeHealth, int healthLevel, boolean debugLog) {
        this.enabled = enabled;
        this.killNightEntry = killNightEntry;
        this.killNightState = killNightState;
        this.killAllProtect = killAllProtect;
        this.killCommonProtect = killCommonProtect;
        this.overrideLimit = overrideLimit;
        this.limitPercent = clamp(limitPercent, 50, 100);
        this.fastChargeMode = clamp(fastChargeMode, 0, 2);
        this.bypassMode = clamp(bypassMode, 0, 2);
        this.killPogo = killPogo;
        this.killWirelessSilence = killWirelessSilence;
        this.killPowerMode = killPowerMode;
        this.killSuperSave = killSuperSave;
        this.noFpsThrottle = noFpsThrottle;
        this.noThermalLimit = noThermalLimit;
        this.noAppOpsRestrict = noAppOpsRestrict;
        this.noKillBackground = noKillBackground;
        this.allowAutostart = allowAutostart;
        this.noNetworkRestrict = noNetworkRestrict;
        this.noBgNetworkRestrict = noBgNetworkRestrict;
        this.noTrafficCutoff = noTrafficCutoff;
        this.noTetherLimit = noTetherLimit;
        this.noInstallVerify = noInstallVerify;
        this.fakeHealth = fakeHealth;
        this.healthLevel = clamp(healthLevel, 1, 4);
        this.debugLog = debugLog;
    }

    public static ConfigSnapshot read(SharedPreferences prefs) {
        if (prefs == null) {
            return DEFAULTS;
        }
        try {
            return new ConfigSnapshot(
                    prefs.getBoolean(Config.KEY_ENABLED, true),
                    prefs.getBoolean(Config.KEY_KILL_NIGHT_ENTRY, true),
                    prefs.getBoolean(Config.KEY_KILL_NIGHT_STATE, true),
                    prefs.getBoolean(Config.KEY_KILL_ALL_PROTECT, false),
                    prefs.getBoolean(Config.KEY_KILL_COMMON_PROTECT, false),
                    prefs.getBoolean(Config.KEY_OVERRIDE_LIMIT, true),
                    prefs.getInt(Config.KEY_LIMIT_PERCENT, 100),
                    prefs.getInt(Config.KEY_FAST_CHARGE_MODE, 0),
                    prefs.getInt(Config.KEY_BYPASS_MODE, 0),
                    prefs.getBoolean(Config.KEY_KILL_POGO, false),
                    prefs.getBoolean(Config.KEY_KILL_WIRELESS_SILENCE, false),
                    prefs.getBoolean(Config.KEY_KILL_POWER_MODE, false),
                    prefs.getBoolean(Config.KEY_KILL_SUPER_SAVE, false),
                    prefs.getBoolean(Config.KEY_NO_FPS_THROTTLE, false),
                    prefs.getBoolean(Config.KEY_NO_THERMAL_LIMIT, false),
                    prefs.getBoolean(Config.KEY_NO_APPOPS_RESTRICT, false),
                    prefs.getBoolean(Config.KEY_NO_KILL_BACKGROUND, false),
                    prefs.getBoolean(Config.KEY_ALLOW_AUTOSTART, false),
                    prefs.getBoolean(Config.KEY_NO_NETWORK_RESTRICT, false),
                    prefs.getBoolean(Config.KEY_NO_BG_NETWORK_RESTRICT, false),
                    prefs.getBoolean(Config.KEY_NO_TRAFFIC_CUTOFF, false),
                    prefs.getBoolean(Config.KEY_NO_TETHER_LIMIT, false),
                    prefs.getBoolean(Config.KEY_NO_INSTALL_VERIFY, false),
                    prefs.getBoolean(Config.KEY_FAKE_HEALTH, false),
                    prefs.getInt(Config.KEY_HEALTH_LEVEL, 4),
                    prefs.getBoolean(Config.KEY_DEBUG_LOG, false)
            );
        } catch (Throwable t) {
            return DEFAULTS;
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
