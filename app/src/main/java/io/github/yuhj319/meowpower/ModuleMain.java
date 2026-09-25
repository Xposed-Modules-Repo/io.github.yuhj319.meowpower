package io.github.yuhj319.meowpower;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * MeowPower — 小米安全中心充电策略自由调节模块。
 *
 * <p>基于 libxposed API 102，作用域 {@code com.miui.securitycenter}
 * （含 {@code :remote} 等子进程，{@code PowerSaveService} 跑在 remote 进程里）。</p>
 *
 * <h3>覆盖的充电保护（全部经 jadx + baksmali 双向核验）</h3>
 * <ul>
 *   <li>保护策略总闸 {@code hg.a.l(boolean)} —— 一条拦掉全部 7 个策略：
 *       MODE_NIGHT / MODE_NAVIGATION / MODE_ALWAYS / MODE_HANDLE /
 *       MODE_HIGH_TEMP / MODE_LONG_TIME_CHARGE / MODE_NO_PROTECT</li>
 *   <li>夜间充电保护：入口 {@code NightChargeProtectManager.C()}、
 *       执行点 {@code M()}、底层状态 {@code e.r(int)}</li>
 *   <li>通用保护（高温/导航/长充）：{@code e.n(int)} / {@code e.p(String,int)}</li>
 *   <li>pogo（底座）充电保护：{@code gh.e.n()} / {@code gh.e.m(int,int)}</li>
 *   <li>无线静音充电：{@code jh.e0.b(boolean)}（smart_chg 0x80）</li>
 *   <li>快充：{@code ng.c.i(boolean)} + 能力判定 + {@code jh.p.d(boolean)}</li>
 *   <li>旁路充电：{@code jg.a.D()/E()/F()}</li>
 *   <li>电池健康度等级：{@code jh.c.p()}</li>
 * </ul>
 *
 * <p>所有混淆类名都做了存在性检查，任一目标缺失只会打日志，不会影响其它 Hook。
 * 每个 Hook 命中都会计入统计并节流上报到 RemotePreferences，供模块界面实时展示。</p>
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "MeowPower";
    private static final String TARGET_PACKAGE = "com.miui.securitycenter";

    /** 充电保护工具类（全静态）。 */
    private static final String CLS_PROTECT_UTILS = "com.miui.powercenter.charge.protect.e";
    /** 夜间充电保护管理器。 */
    private static final String CLS_NIGHT_MANAGER = "com.miui.powercenter.charge.protect.NightChargeProtectManager";
    /** 充电保护策略基类。 */
    private static final String CLS_BASE_PROTECT = "hg.a";
    /** IMiCharge 包装类，写 smart_chg 位。 */
    private static final String CLS_MI_CHARGE_WRAPPER = "jh.p";
    /** 快充控制器 FastChargeController。 */
    private static final String CLS_FAST_CHARGE = "ng.c";
    /** 旁路充电管理器 SideRoadChargeManager。 */
    private static final String CLS_SIDE_ROAD = "jg.a";
    /** 电池健康度工具类 BatteryHealthUtils。 */
    private static final String CLS_BATTERY_HEALTH = "jh.c";
    /** pogo（底座）充电保护管理器 SmartChargeManager。 */
    private static final String CLS_POGO_MANAGER = "gh.e";
    /** 无线静音充电工具类 SmartChargeUtils。 */
    private static final String CLS_WIRELESS_SILENCE = "jh.e0";
    /** 电源模式总闸（性能 / 省电 / 超省电的唯一分发点）。 */
    private static final String CLS_POWER_SAVER_PROVIDER = "com.miui.powercenter.powersaver.PowerSaverProvider";
    /** 超省电模式管理器。 */
    private static final String CLS_SUPER_SAVE = "uj.p";
    /** 省电任务执行器（降刷新率 / 降亮度 / 缩短息屏）。 */
    private static final String CLS_POWER_TASK = "jh.x";
    /** 温控配置工具。 */
    private static final String CLS_THERMAL = "jh.g0";
    /** 游戏画质增强（插帧/超分）判定与下发。 */
    private static final String CLS_VISION_ENHANCE = "com.miui.gamebooster.utils.GameBoxVisionEnhanceUtils";
    /** joyose GPU 调谐 Binder 代理（跑在安全中心进程内），按包能力查询的实际返回点。 */
    private static final String CLS_GPU_TUNER_PROXY = "com.xiaomi.joyose.securitycenter.IGPUTunerInterface$Stub$a";
    /** 游戏性能档（GameManager.setGameMode 封装）。 */
    private static final String CLS_GAME_MODE = "com.miui.gamebooster.utils.n1";
    /** AppOps 私有码写入总闸。 */
    private static final String CLS_APPOPS_COMPAT = "com.miui.permcenter.compact.AppOpsUtilsCompat";
    /** 内存清理（杀后台）。 */
    private static final String CLS_MEMORY_CHECK = "com.miui.securitycenter.memory.MemoryCheck";
    /** 内存清理回调 AIDL 接口，只能反射拿。 */
    private static final String CLS_MEMORY_CALLBACK = "com.miui.securitycenter.memory.IMemoryCleanupCallback";
    /** 应用管理（自启动开关）。 */
    private static final String CLS_APP_MANAGE_UTILS = "com.miui.appmanager.AppManageUtils";
    /** 防火墙规则枚举。 */
    private static final String CLS_FIREWALL_RULE = "com.miui.networkassistant.model.FirewallRule";
    /** netd 防火墙实现（联网控制的最后写入点）。 */
    private static final String CLS_NETD_FIREWALL = "com.miui.networkassistant.firewall.impl.MiuiNetdFirewall";
    /** 后台联网策略。 */
    private static final String CLS_BG_POLICY = "com.miui.networkassistant.firewall.BackgroundPolicyService";
    /** 流量管理（超限停网）。 */
    private static final String CLS_TRAFFIC_SIM = "com.miui.networkassistant.service.tm.TrafficSimManager";
    /** 热点流量统计（超限处理）。 */
    private static final String CLS_TETHER_STATS = "com.miui.networkassistant.service.tm.TetherStatsManager";
    /** 安装包校验。 */
    private static final String CLS_PKG_VERIFY = "com.miui.permcenter.install.PackageVerificationReceiver";
    /** 插件：修改UI健康度 —— 充电保护页。 */
    private static final String CLS_CHARGER_PROTECT_ACTIVITY = "com.miui.powercenter.nightcharge.ChargerProtectActivity";
    /** 插件：修改UI健康度 —— 健康度百分比的资源名关键字。 */
    private static final String RES_HEALTH_PERCENT = "percent_formatted_text";
    /** 字符串值 -> 资源名（Resources.getText/getString 顺手记录，只记百分数字符串）。 */
    private static final ConcurrentHashMap<String, String> VALUE2RES = new ConcurrentHashMap<>();
    /** 最近一次进入的充电保护页（Context 链取不到 Activity 时的兜底）。 */
    private static volatile String lastActivity = "?";
    private SharedPreferences prefs;
    private String processName = "";
    private volatile boolean debugLog = false;

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        try {
            prefs = getRemotePreferences(Config.GROUP);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "getRemotePreferences 失败，将使用默认配置", t);
        }
        log(Log.INFO, TAG, "已加载：进程=" + processName
                + "，框架=" + getFrameworkName() + " " + getFrameworkVersion()
                + "，API=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        final ClassLoader cl = param.getClassLoader();
        log(Log.INFO, TAG, "目标包就绪：" + param.getPackageName() + " @ " + processName);

        hookNightChargeEntry(cl);
        hookNightChargeState(cl);
        hookCommonProtect(cl);
        hookBaseProtect(cl);
        hookPogoProtect(cl);
        hookWirelessSilence(cl);
        hookPowerMode(cl);
        hookSuperSave(cl);
        hookFpsThrottle(cl);
        hookThermalLimit(cl);
        hookFrameInsert(cl);
        hookGameMode(cl);
        hookAppOpsRestrict(cl);
        hookKillBackground(cl);
        hookAutoStart(cl);
        hookNetworkRestrict(cl);
        hookInstallVerify(cl);
        hookFastCharge(cl);
        hookSideRoadCharge(cl);
        hookBatteryHealth(cl);
        hookUiHealth(cl);

    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        log(Log.INFO, TAG, "热重载中，旧 Hook 将被清理");
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        for (var handle : param.getOldHookHandles()) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // 已失效的句柄，忽略
            }
        }
        log(Log.INFO, TAG, "热重载完成，清理了 " + param.getOldHookHandles().size() + " 个旧 Hook");
    }

    // ------------------------------------------------------------------
    // Hook：夜间充电保护
    // ------------------------------------------------------------------

    /**
     * 掐掉夜充保护的启动入口。{@code C(Context)} 是全库唯一调用
     * {@code hg.a.l(true)} 的地方，掐死后检查闹钟全变哑弹。
     */
    private void hookNightChargeEntry(ClassLoader cl) {
        Method method = findMethod(cl, CLS_NIGHT_MANAGER, "C", Context.class);
        if (method != null) {
            try {
                hook(method).setId("cf_night_entry").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.killNightEntry) {
                        d("拦截 NightChargeProtectManager.C()，夜充保护不启动");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 夜充保护入口");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 夜充保护入口失败", t);
            }
        }

        // 执行点：真正下发 setNightChargingState(1) 并设恢复闹钟的地方
        Method apply = findMethod(cl, CLS_NIGHT_MANAGER, "M", Context.class);
        if (apply == null) {
            return;
        }
        try {
            hook(apply).setId("cf_night_apply").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.killNightEntry) {
                    d("拦截 NightChargeProtectManager.M()，不下发 setNightChargingState(1)");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 夜充保护执行点");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 夜充保护执行点失败", t);
        }
    }

    /**
     * 兜底拦截底层状态写入。{@code e.r(1)} 即
     * {@code IMiCharge.setNightChargingState(1)}，是"卡在 80%"的真正开关。
     *
     * <p>传 1 时直接返回 {@code Boolean.FALSE} 且不 proceed：调用方
     * {@code NightChargeProtectManager.L()} 拿到 false 后不会置位保护态、
     * 不会设恢复闹钟；传 0（关闭保护）时正常放行。</p>
     */
    private void hookNightChargeState(ClassLoader cl) {
        Method method = findMethod(cl, CLS_PROTECT_UTILS, "r", int.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_night_state").intercept(chain -> {
                ConfigSnapshot c = read();
                int value = asInt(chain.getArg(0), 0);
                if (c.enabled && c.killNightState && value == 1) {
                    d("拦截 setNightChargingState(1)");
                    return Boolean.FALSE;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 夜充状态写入");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 夜充状态写入失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：通用充电保护（限制百分比）
    // ------------------------------------------------------------------

    /**
     * 通用保护入口。{@code e.n(int percent)} 会写入
     * {@code smart_chg = 0x{percent}{0x11}}；{@code e.p(String,int)} 是另一个入口
     * （navigation 用 0x3，always/handle/high_temp 用 0x11）。
     *
     * <p>支持两种干预：整体拦截，或把限制百分比改写成用户设定值。</p>
     */
    private void hookCommonProtect(ClassLoader cl) {
        Method n = findMethod(cl, CLS_PROTECT_UTILS, "n", int.class);
        if (n != null) {
            try {
                hook(n).setId("cf_common_protect_n").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (!c.enabled) {
                        return chain.proceed();
                    }
                    if (c.killCommonProtect) {
                        d("拦截 e.n()，通用充电保护不生效");
                        return null;
                    }
                    if (c.overrideLimit) {
                        if (c.limitPercent >= Config.LIMIT_UNLIMITED) {
                            d("跳过 e.n()：限制百分比设为 " + c.limitPercent + "，等同不限制");
                            return null;
                        }
                        int origin = asInt(chain.getArg(0), 80);
                        if (origin != c.limitPercent) {
                            d("改写限制百分比 " + origin + " -> " + c.limitPercent);
                            return chain.proceed(new Object[]{c.limitPercent});
                        }
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 通用保护 e.n(int)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook e.n(int) 失败", t);
            }
        }

        Method p = findMethod(cl, CLS_PROTECT_UTILS, "p", String.class, int.class);
        if (p != null) {
            try {
                hook(p).setId("cf_common_protect_p").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (!c.enabled) {
                        return chain.proceed();
                    }
                    if (c.killCommonProtect) {
                        d("拦截 e.p()，通用充电保护不生效");
                        return null;
                    }
                    if (c.overrideLimit) {
                        if (c.limitPercent >= Config.LIMIT_UNLIMITED) {
                            d("跳过 e.p()：限制百分比设为 " + c.limitPercent + "，等同不限制");
                            return null;
                        }
                        int origin = asInt(chain.getArg(1), 80);
                        if (origin != c.limitPercent) {
                            Object[] args = chain.getArgs().toArray();
                            args[1] = c.limitPercent;
                            d("改写限制百分比 " + origin + " -> " + c.limitPercent);
                            return chain.proceed(args);
                        }
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 通用保护 e.p(String,int)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook e.p(String,int) 失败", t);
            }
        }
    }

    /**
     * 保护策略总闸。{@code hg.a.l(boolean)} 传 true 表示某个保护策略要启动，
     * 传 false 表示退出保护（必须放行，否则恢复不了充电）。
     *
     * <p>该方法全库只有一份实现，7 个保护策略都没有重写它，所以这一条 Hook
     * 就能拦下所有策略的启动。</p>
     */
    private void hookBaseProtect(ClassLoader cl) {
        Method method = findMethod(cl, CLS_BASE_PROTECT, "l", boolean.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_base_protect").intercept(chain -> {
                ConfigSnapshot c = read();
                boolean start = Boolean.TRUE.equals(chain.getArg(0));
                if (c.enabled && c.killAllProtect && start) {
                    d("拦截 hg.a.l(true)，所有充电保护不启动");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 保护策略总闸");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 保护策略总闸失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：pogo（底座）充电保护
    // ------------------------------------------------------------------

    /**
     * pogo 底座充电保护。{@code gh.e}（SmartChargeManager）在底座连接且电量
     * 达到阈值时调 {@code x.r0(Context)} 开启保护。
     *
     * <p>拦 {@code n()}（开启保护的动作）与 {@code m(int,int)}（电量变化触发），
     * 不动 {@code l()}（支持判定，UI 会读它）。</p>
     */
    private void hookPogoProtect(ClassLoader cl) {
        Method open = findMethod(cl, CLS_POGO_MANAGER, "n");
        if (open != null) {
            try {
                hook(open).setId("cf_pogo_open").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.killPogo) {
                        d("拦截 pogo 充电保护开启");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook pogo 保护开启");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook pogo 保护开启失败", t);
            }
        }

        Method batteryChanged = findMethod(cl, CLS_POGO_MANAGER, "m", int.class, int.class);
        if (batteryChanged == null) {
            return;
        }
        try {
            hook(batteryChanged).setId("cf_pogo_battery").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.killPogo) {
                    d("拦截 pogo 保护电量触发");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook pogo 保护电量触发");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook pogo 保护电量触发失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：无线静音充电
    // ------------------------------------------------------------------

    /**
     * 无线静音充电：{@code jh.e0.b(boolean)} 写 {@code smart_chg} 的 0x81/0x80 位，
     * 开启后无线充电功率被压低。强制传 false 即恢复满功率。
     */
    private void hookWirelessSilence(ClassLoader cl) {
        Method method = findMethod(cl, CLS_WIRELESS_SILENCE, "b", boolean.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_wireless_silence").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.killWirelessSilence
                        && Boolean.TRUE.equals(chain.getArg(0))) {
                    d("强制关闭无线静音充电");
                    return chain.proceed(new Object[]{Boolean.FALSE});
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 无线静音充电");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 无线静音充电失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：电源 / 性能 / 温控
    // ------------------------------------------------------------------

    /**
     * 电源模式总闸。{@code PowerSaverProvider.call} 是性能模式、省电模式、
     * 超省电模式的唯一分发点，拦掉后模式切换一律不生效。
     *
     * <p>只拦"切模式"这类调用，放行查询类（如 checkOverclock），
     * 避免把 UI 读状态也一起打断。</p>
     */
    private void hookPowerMode(ClassLoader cl) {
        Method method = findMethod(cl, CLS_POWER_SAVER_PROVIDER, "call",
                String.class, String.class, Bundle.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_power_mode").intercept(chain -> {
                ConfigSnapshot c = read();
                Object name = chain.getArg(1);
                if (c.enabled && c.killPowerMode && name instanceof String
                        && isModeSwitchCall((String) name)) {
                    d("拦截电源模式切换：" + name);
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 电源模式总闸");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 电源模式总闸失败", t);
        }
    }

    private static boolean isModeSwitchCall(String name) {
        return "changePowerMode".equals(name)
                || "changePerformanceMode".equals(name)
                || "changeSuperPowerMode".equals(name)
                || "changeBalancedMode".equals(name)
                || "changeExtremeEnduranceMode".equals(name);
    }

    /**
     * 超省电模式。{@code uj.p.X()} 一旦进入，会连锁触发禁用应用、杀后台、
     * 冻结自启动、动画归零、通知与状态栏限制。掐掉这一个方法即可全部解除。
     */
    private void hookSuperSave(ClassLoader cl) {
        Method method = findMethod(cl, CLS_SUPER_SAVE, "X",
                boolean.class, boolean.class, boolean.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_super_save").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.killSuperSave) {
                    d("拦截超省电模式进入");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 超省电模式");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 超省电模式失败", t);
        }
    }

    /**
     * 省电降频 / 降亮度 / 缩短息屏。{@code jh.x} 是 PowerTaskManager：
     * {@code G}/{@code H} 改亮度、{@code I} 降刷新率、{@code O} 改息屏超时。
     */
    private void hookFpsThrottle(ClassLoader cl) {
        for (String name : new String[]{"G", "H", "O"}) {
            Method method = findMethod(cl, CLS_POWER_TASK, name, int.class);
            if (method == null) {
                continue;
            }
            final String label = name;
            try {
                hook(method).setId("cf_throttle_" + name).intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noFpsThrottle) {
                        d("拦截省电策略 jh.x." + label + "()");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 省电策略 jh.x." + name + "()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook jh.x." + name + "() 失败", t);
            }
        }

        Method refresh = findMethod(cl, CLS_POWER_TASK, "I");
        if (refresh == null) {
            return;
        }
        try {
            hook(refresh).setId("cf_throttle_I").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.noFpsThrottle) {
                    d("拦截省电降刷新率 jh.x.I()");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 省电策略 jh.x.I()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook jh.x.I() 失败", t);
        }
    }

    /**
     * 温控降频。{@code jh.g0.e(int,boolean)} 写
     * {@code persist.sys.thermal.config} 与 {@code /sys/class/thermal/thermal_message/sconfig}。
     */
    private void hookThermalLimit(ClassLoader cl) {
        Method method = findMethod(cl, CLS_THERMAL, "e", int.class, boolean.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_thermal").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.noThermalLimit) {
                    d("拦截温控配置下发");
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 温控配置");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 温控配置失败", t);
        }
    }

    /**
     * 强制开启游戏插帧 / 超分（类型 1=插帧、2=超分、4=双开）。
     *
     * <p>判定链（安全服务 13.5.3，{@code GameBoxVisionEnhanceUtils}）：
     * {@code K()} 设备总闸、{@code Q()} 按包支持、{@code E()} 开关态为公共闸；
     * 插帧走 {@code W()/R()/O()}，超分走 {@code Z()/S()/N()}，
     * 放行后侧边栏 {@code b1.y()} 才会把对应复选框置为 {@code VISIBLE}。
     * 下发链：{@code w0/q0} 写类型、{@code p0} 写开关，最终调 joyose
     * {@code IGPUTuner.setFrameInsertingOrSuperResolution(pkg,type)} /
     * {@code setPictureEnhancement(pkg,true)}。{@code F(String)} 置 true
     * 让初始化默认开，{@code Stub$a} 代理层兜底按包查询（不支持的包返回
     * 帧率 120 / 合并类型数组 / 目标类型），避免 {@code J()} 因
     * {@code f17937d=false} 走 {@code i0()} 释放服务。</p>
     */
    private void hookFrameInsert(ClassLoader cl) {
        hookVisionGate(cl, new String[]{"K", "Q", "E"}, 0);
        hookVisionGate(cl, new String[]{"W", "R", "O"}, 1);
        hookVisionGate(cl, new String[]{"Z", "S", "N"}, 2);

        Method f = findMethod(cl, CLS_VISION_ENHANCE, "F", String.class);
        if (f != null) {
            try {
                hook(f).setId("cf_frameinsert_F").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (visionOn(c)) {
                        d("强制画质默认开：" + chain.getArg(0));
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 画质默认开关 F()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 插帧默认开关 F() 失败", t);
            }
        }

        Method q0 = findMethod(cl, CLS_VISION_ENHANCE, "q0", int.class);
        if (q0 != null) {
            try {
                hook(q0).setId("cf_frameinsert_q0").intercept(chain -> {
                    ConfigSnapshot c = read();
                    int target = visionTargetType(c);
                    if (target != 0) {
                        d("强制画质类型 -> " + target);
                        return chain.proceed(new Object[]{target});
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 画质类型 q0()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 插帧类型 q0() 失败", t);
            }
        }

        Method p0 = findMethod(cl, CLS_VISION_ENHANCE, "p0", boolean.class);
        if (p0 != null) {
            try {
                hook(p0).setId("cf_frameinsert_p0").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (visionOn(c) && !Boolean.TRUE.equals(chain.getArg(0))) {
                        d("强制画质开关 -> true");
                        return chain.proceed(new Object[]{Boolean.TRUE});
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 画质开关 p0()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 画质开关 p0() 失败", t);
            }
        }

        Method w0 = findMethod(cl, CLS_VISION_ENHANCE, "w0",
                boolean.class, boolean.class, int.class);
        if (w0 != null) {
            try {
                hook(w0).setId("cf_frameinsert_w0").intercept(chain -> {
                    ConfigSnapshot c = read();
                    int target = visionTargetType(c);
                    if (target != 0) {
                        Object[] args = chain.getArgs().toArray();
                        if (c.forceFrameInsert) {
                            args[0] = Boolean.TRUE;
                        }
                        if (c.forceSuperResolution) {
                            args[1] = Boolean.TRUE;
                        }
                        args[2] = target;
                        d("强制画质下发 w0(" + args[0] + "," + args[1] + "," + target + ")");
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 画质下发 w0()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 插帧下发 w0() 失败", t);
            }
        }

        Method pkgSupport = findMethod(cl, CLS_GPU_TUNER_PROXY,
                "isSupportGameEnhancePkg", String.class);
        if (pkgSupport != null) {
            try {
                hook(pkgSupport).setId("cf_frameinsert_pkg").intercept(chain -> {
                    ConfigSnapshot c = read();
                    Object result = chain.proceed();
                    if (visionOn(c) && result instanceof Integer && (Integer) result == 0) {
                        d("joyose 按包帧率 0 -> 120：" + chain.getArg(0));
                        return 120;
                    }
                    return result;
                });
                log(Log.INFO, TAG, "已 Hook joyose 按包查询");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook joyose 按包查询失败", t);
            }
        }

        Method supportType = findMethod(cl, CLS_GPU_TUNER_PROXY,
                "getPictureEnhanceSupportType", String.class);
        if (supportType != null) {
            try {
                hook(supportType).setId("cf_frameinsert_types").intercept(chain -> {
                    ConfigSnapshot c = read();
                    Object result = chain.proceed();
                    int target = visionTargetType(c);
                    if (target != 0) {
                        int[] merged = mergeVisionTypes(
                                result instanceof int[] ? (int[]) result : null, c);
                        d("joyose 支持类型 -> " + java.util.Arrays.toString(merged)
                                + "：" + chain.getArg(0));
                        return merged;
                    }
                    return result;
                });
                log(Log.INFO, TAG, "已 Hook joyose 支持类型查询");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook joyose 支持类型查询失败", t);
            }
        }

        Method currentType = findMethod(cl, CLS_GPU_TUNER_PROXY,
                "getFrameInsertingOrSuperResolution", String.class);
        if (currentType != null) {
            try {
                hook(currentType).setId("cf_frameinsert_curtype").intercept(chain -> {
                    ConfigSnapshot c = read();
                    int target = visionTargetType(c);
                    if (target != 0) {
                        return target;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook joyose 当前类型查询");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook joyose 当前类型查询失败", t);
            }
        }

        Method dualSupport = findMethod(cl, CLS_GPU_TUNER_PROXY,
                "isSupportSuperResolutionWithFrameInsert", String.class);
        if (dualSupport != null) {
            try {
                hook(dualSupport).setId("cf_frameinsert_dual").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (visionOn(c)) {
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook joyose 双开支持查询");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook joyose 双开支持查询失败", t);
            }
        }

        Method topGame = findMethod(cl, CLS_GPU_TUNER_PROXY,
                "enableSuperResolutionWithFrameInsert", String.class);
        if (topGame != null) {
            try {
                hook(topGame).setId("cf_frameinsert_topgame").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (visionOn(c)) {
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook joyose 双开使能查询");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook joyose 双开使能查询失败", t);
            }
        }
    }

    /** 画质判定门：scope 0=任一开关，1=仅插帧，2=仅超分。 */
    private void hookVisionGate(ClassLoader cl, String[] names, int scope) {
        for (String name : names) {
            Method gate = findMethod(cl, CLS_VISION_ENHANCE, name);
            if (gate == null) {
                continue;
            }
            final String label = name;
            try {
                hook(gate).setId("cf_vision_" + name).intercept(chain -> {
                    ConfigSnapshot c = read();
                    boolean hit = scope == 0 ? visionOn(c)
                            : scope == 1 ? (c.enabled && c.forceFrameInsert)
                            : (c.enabled && c.forceSuperResolution);
                    if (hit) {
                        d("强制放行画质判定 " + label + "()");
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 画质判定 " + name + "()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 画质判定 " + name + "() 失败", t);
            }
        }
    }

    /** 任一画质开关开启。 */
    private static boolean visionOn(ConfigSnapshot c) {
        return c.enabled && (c.forceFrameInsert || c.forceSuperResolution);
    }

    /** 目标类型：1=插帧、2=超分、4=双开，0=不干预。 */
    private static int visionTargetType(ConfigSnapshot c) {
        if (!c.enabled) {
            return 0;
        }
        if (c.forceFrameInsert && c.forceSuperResolution) {
            return 4;
        }
        if (c.forceFrameInsert) {
            return 1;
        }
        if (c.forceSuperResolution) {
            return 2;
        }
        return 0;
    }

    /** 合并 joyose 原返回与强制类型位，保证强制位一定在。 */
    private static int[] mergeVisionTypes(int[] origin, ConfigSnapshot c) {
        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
        if (origin != null) {
            for (int type : origin) {
                set.add(type);
            }
        }
        if (c.forceFrameInsert) {
            set.add(1);
        }
        if (c.forceSuperResolution) {
            set.add(2);
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int type : set) {
            out[i++] = type;
        }
        return out;
    }

    /**
     * 强制游戏性能档。{@code n1.d(String,int)} 是
     * {@code GameManager.setGameMode} 的唯一封装（1=标准、2=性能、3=省电），
     * 强制写 2（GAME_MODE_PERFORMANCE），不再看名单脸色。
     */
    private void hookGameMode(ClassLoader cl) {
        Method method = findMethod(cl, CLS_GAME_MODE, "d", String.class, int.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_game_mode").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.forceGameMode
                        && !Integer.valueOf(2).equals(chain.getArg(1))) {
                    d("强制游戏性能档 -> 2：" + chain.getArg(0));
                    return chain.proceed(new Object[]{chain.getArg(0), 2});
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 游戏性能档 n1.d()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 游戏性能档失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：权限与后台
    // ------------------------------------------------------------------

    /**
     * AppOps 私有限制总闸。三个方法覆盖 10048 禁安装、10049 禁链式启动、
     * 10050 设备标识、10041 录屏保护、119 无障碍受限、10054/10055 AI 读屏控屏。
     *
     * <p><b>参数顺序有陷阱</b>：安全中心自己的封装是
     * {@code setMode(appOps, pkg, uid, op, mode)}，op 与 pkg 的位置与标准
     * {@code AppOpsManager.setMode(op, uid, pkg, mode)} 相反，照抄标准顺序会全错。</p>
     */
    private void hookAppOpsRestrict(ClassLoader cl) {
        Method setMode = findMethod(cl, CLS_APPOPS_COMPAT, "setMode",
                AppOpsManager.class, String.class, int.class, String.class, int.class);
        if (setMode != null) {
            try {
                hook(setMode).setId("cf_appops_setmode").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noAppOpsRestrict && isRestrictMode(chain.getArg(4))) {
                        d("放行 AppOps 限制 op=" + chain.getArg(3));
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook AppOpsUtilsCompat.setMode");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook AppOpsUtilsCompat.setMode 失败", t);
            }
        }

        Method withXSpace = findMethod(cl, CLS_APPOPS_COMPAT, "setModeWithXSpace",
                Context.class, AppOpsManager.class, String.class, int.class, int.class, int.class);
        if (withXSpace != null) {
            try {
                hook(withXSpace).setId("cf_appops_xspace").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noAppOpsRestrict && isRestrictMode(chain.getArg(5))) {
                        d("放行 AppOps 限制（XSpace）");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook AppOpsUtilsCompat.setModeWithXSpace");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook setModeWithXSpace 失败", t);
            }
        }

        Method setUidMode = findMethod(cl, CLS_APPOPS_COMPAT, "setUidMode",
                AppOpsManager.class, String.class, int.class, int.class);
        if (setUidMode != null) {
            try {
                hook(setUidMode).setId("cf_appops_uidmode").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noAppOpsRestrict && isRestrictMode(chain.getArg(3))) {
                        d("放行 AppOps 限制（uidMode）");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook AppOpsUtilsCompat.setUidMode");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook setUidMode 失败", t);
            }
        }
    }

    /** MODE_ALLOWED = 0，其余 mode 一律视为限制。 */
    private static boolean isRestrictMode(Object mode) {
        return !(mode instanceof Integer) || (Integer) mode != 0;
    }

    /**
     * 内存清理杀后台。{@code MemoryCheck.d4()} 是一键清理的 AIDL 入口，
     * {@code O9(String)} 内部调 {@code ActivityManager.killBackgroundProcesses}。
     * 两条路径都拦，避免漏掉。
     */
    private void hookKillBackground(ClassLoader cl) {
        try {
            Class<?> callback = Class.forName(CLS_MEMORY_CALLBACK, false, cl);
            Method cleanup = findMethod(cl, CLS_MEMORY_CHECK, "d4", List.class, callback);
            if (cleanup != null) {
                hook(cleanup).setId("cf_kill_background").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noKillBackground) {
                        d("拦截内存清理杀后台");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 内存清理入口");
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 内存清理入口失败", t);
        }

        Method kill = findMethod(cl, CLS_MEMORY_CHECK, "O9", String.class);
        if (kill == null) {
            return;
        }
        try {
            hook(kill).setId("cf_kill_process").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.noKillBackground) {
                    d("拦截 killBackgroundProcesses：" + chain.getArg(0));
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 杀进程 O9()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook O9() 失败", t);
        }
    }

    /**
     * 自启动。{@code AppManageUtils.w0(Context,int,String,boolean)}，
     * allow=false 表示禁止自启动，强制改 true。
     *
     * <p>自启动不走 Settings 也不走 AppOps，走平台权限库，
     * 所以常规方案对它无效，必须打在这个写入点上。</p>
     */
    private void hookAutoStart(ClassLoader cl) {
        Method method = findMethod(cl, CLS_APP_MANAGE_UTILS, "w0",
                Context.class, int.class, String.class, boolean.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_autostart").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.allowAutostart && !Boolean.TRUE.equals(chain.getArg(3))) {
                    Object[] args = chain.getArgs().toArray();
                    args[3] = Boolean.TRUE;
                    d("强制允许自启动：" + chain.getArg(2));
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 自启动");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 自启动失败", t);
        }
    }

    // ------------------------------------------------------------------
    // Hook：网络与安装
    // ------------------------------------------------------------------

    /**
     * 网络类限制，四个写入点：
     * <ul>
     *   <li>{@code MiuiNetdFirewall.setWifiRule / setMobileRule} —— 单应用联网控制，
     *       最终调 {@code SecurityManager.setMiuiFirewallRule}。把规则改成
     *       {@code FirewallRule.Allow} 而不是跳过，这样防火墙内部的 mRuleMap
     *       与系统侧保持一致。</li>
     *   <li>{@code BackgroundPolicyService.setAppRestrictBackground / setRestrictBackground}
     *       —— 后台联网限制。</li>
     *   <li>{@code TrafficSimManager.onNormalTrafficOverLimit} —— 流量超限自动停网
     *       （表现为「突然断网」）。</li>
     *   <li>{@code TetherStatsManager.onTetherStatsOverLimit} —— 热点共享流量限制。</li>
     * </ul>
     */
    private void hookNetworkRestrict(ClassLoader cl) {
        Class<?> ruleClass = null;
        try {
            ruleClass = Class.forName(CLS_FIREWALL_RULE, false, cl);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "FirewallRule 不存在，跳过联网限制 Hook");
        }

        if (ruleClass != null) {
            final Class<?> ruleCls = ruleClass;
            Method wifi = findMethod(cl, CLS_NETD_FIREWALL, "setWifiRule", String.class, ruleCls);
            if (wifi != null) {
                try {
                    hook(wifi).setId("cf_net_wifi").intercept(chain -> {
                        ConfigSnapshot c = read();
                        if (c.enabled && c.noNetworkRestrict) {
                            Object allow = enumValue(ruleCls, "Allow");
                            Object current = chain.getArg(1);
                            if (allow != null && current != allow) {
                                Object[] args = chain.getArgs().toArray();
                                args[1] = allow;
                                d("放行 WiFi 联网限制：" + chain.getArg(0));
                                return chain.proceed(args);
                            }
                        }
                        return chain.proceed();
                    });
                    log(Log.INFO, TAG, "已 Hook WiFi 联网限制");
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "Hook setWifiRule 失败", t);
                }
            }

            Method mobile = findMethod(cl, CLS_NETD_FIREWALL, "setMobileRule",
                    String.class, ruleCls, int.class, boolean.class);
            if (mobile != null) {
                try {
                    hook(mobile).setId("cf_net_mobile").intercept(chain -> {
                        ConfigSnapshot c = read();
                        if (c.enabled && c.noNetworkRestrict) {
                            Object allow = enumValue(ruleCls, "Allow");
                            Object current = chain.getArg(1);
                            if (allow != null && current != allow) {
                                Object[] args = chain.getArgs().toArray();
                                args[1] = allow;
                                d("放行移动数据联网限制：" + chain.getArg(0));
                                return chain.proceed(args);
                            }
                        }
                        return chain.proceed();
                    });
                    log(Log.INFO, TAG, "已 Hook 移动数据联网限制");
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "Hook setMobileRule 失败", t);
                }
            }
        }

        Method perApp = findMethod(cl, CLS_BG_POLICY, "setAppRestrictBackground", int.class, boolean.class);
        if (perApp != null) {
            try {
                hook(perApp).setId("cf_net_bg_app").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noBgNetworkRestrict && Boolean.TRUE.equals(chain.getArg(1))) {
                        d("放行后台联网限制（uid " + chain.getArg(0) + "）");
                        return chain.proceed(new Object[]{chain.getArg(0), Boolean.FALSE});
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 后台联网限制（单应用）");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook setAppRestrictBackground 失败", t);
            }
        }

        Method global = findMethod(cl, CLS_BG_POLICY, "setRestrictBackground", boolean.class);
        if (global != null) {
            try {
                hook(global).setId("cf_net_bg_global").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noBgNetworkRestrict && Boolean.TRUE.equals(chain.getArg(0))) {
                        d("放行全局后台联网限制");
                        return chain.proceed(new Object[]{Boolean.FALSE});
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 后台联网限制（全局）");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook setRestrictBackground 失败", t);
            }
        }

        Method traffic = findMethod(cl, CLS_TRAFFIC_SIM, "onNormalTrafficOverLimit");
        if (traffic != null) {
            try {
                hook(traffic).setId("cf_traffic_cutoff").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noTrafficCutoff) {
                        d("拦截流量超限停网");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 流量超限停网");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook onNormalTrafficOverLimit 失败", t);
            }
        }

        Method tether = findMethod(cl, CLS_TETHER_STATS, "onTetherStatsOverLimit");
        if (tether != null) {
            try {
                hook(tether).setId("cf_tether_limit").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.noTetherLimit) {
                        d("拦截热点流量超限处理");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 热点流量限制");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook onTetherStatsOverLimit 失败", t);
            }
        }
    }

    /**
     * 安装签名校验。{@code PackageVerificationReceiver.c()} 返回 false 会中断安装流程，
     * 返回 true 表示放行（该方法在「包已存在」等分支上自己也返回 true）。
     */
    private void hookInstallVerify(ClassLoader cl) {
        Method method = findMethod(cl, CLS_PKG_VERIFY, "c",
                Context.class, PackageInfo.class, String.class, String.class);
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_install_verify").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.noInstallVerify) {
                    d("放行安装校验：" + chain.getArg(2));
                    return Boolean.TRUE;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 安装签名校验");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 安装签名校验失败", t);
        }
    }

    /** 取枚举里指定名字的常量，取不到返回 null。 */
    private static Object enumValue(Class<?> clazz, String name) {
        if (clazz == null || !clazz.isEnum()) {
            return null;
        }
        try {
            for (Object constant : clazz.getEnumConstants()) {
                if (constant != null && name.equals(constant.toString())) {
                    return constant;
                }
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Hook：快充
    // ------------------------------------------------------------------

    /**
     * 快充强制开关。
     *
     * <p>写入链路：{@code ng.c.i(boolean)} → 支持位判定 {@code ng.c.g()} →
     * {@code jh.p.d(boolean)} → {@code setMiChargePath("smart_chg", ...)} bit0。
     * 两个写入点都改，能力判定在"强制开启"时一并放行，避免因为机型白名单
     * 或 {@code persist.vendor.accelerate.charge} 缺失而无效。</p>
     */
    private void hookFastCharge(ClassLoader cl) {
        Method entry = findMethod(cl, CLS_FAST_CHARGE, "i", boolean.class);
        if (entry != null) {
            try {
                hook(entry).setId("cf_fast_charge_entry").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.fastChargeMode != 0) {
                        boolean want = c.fastChargeMode == 1;
                        if (!Boolean.valueOf(want).equals(chain.getArg(0))) {
                            d("强制快充（入口）-> " + want);
                            return chain.proceed(new Object[]{want});
                        }
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 快充入口 ng.c.i(boolean)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 快充入口失败", t);
            }
        }

        Method writer = findMethod(cl, CLS_MI_CHARGE_WRAPPER, "d", boolean.class);
        if (writer != null) {
            try {
                hook(writer).setId("cf_fast_charge_writer").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.fastChargeMode != 0) {
                        boolean want = c.fastChargeMode == 1;
                        if (!Boolean.valueOf(want).equals(chain.getArg(0))) {
                            d("强制快充（底层 smart_chg bit0）-> " + want);
                            return chain.proceed(new Object[]{want});
                        }
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 快充底层写入 jh.p.d(boolean)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 快充底层写入失败", t);
            }
        }

        for (String name : new String[]{"e", "f", "g"}) {
            Method support = findMethod(cl, CLS_FAST_CHARGE, name);
            if (support == null) {
                continue;
            }
            try {
                hook(support).setId("cf_fast_charge_support_" + name).intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.fastChargeMode == 1) {
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook 快充能力判定 " + name + "() 失败", t);
            }
        }
    }

    // ------------------------------------------------------------------
    // Hook：旁路充电
    // ------------------------------------------------------------------

    /**
     * 旁路充电（边玩边充直供）干预。
     *
     * <p>{@code jg.a.D()} 开启、{@code E()} 停止、{@code F()} 带 UI 停止。
     * 模式 1 阻止自动开启，模式 2 阻止自动停止。</p>
     */
    private void hookSideRoadCharge(ClassLoader cl) {
        Method start = findMethod(cl, CLS_SIDE_ROAD, "D");
        if (start != null) {
            try {
                hook(start).setId("cf_bypass_start").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.bypassMode == 1) {
                        d("阻止旁路充电自动开启");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 旁路充电开启 jg.a.D()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook jg.a.D() 失败", t);
            }
        }

        for (String name : new String[]{"E", "F"}) {
            Method stop = findMethod(cl, CLS_SIDE_ROAD, name);
            if (stop == null) {
                continue;
            }
            try {
                hook(stop).setId("cf_bypass_stop_" + name).intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (c.enabled && c.bypassMode == 2) {
                        d("阻止旁路充电自动停止");
                        return null;
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "已 Hook 旁路充电停止 jg.a." + name + "()");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Hook jg.a." + name + "() 失败", t);
            }
        }
    }

    // ------------------------------------------------------------------
    // Hook：电池健康度
    // ------------------------------------------------------------------

    /**
     * 伪装电池健康度等级（1~4，4 为最优）。
     * {@code jh.c.p()} 是等级对外的唯一出口。
     */
    private void hookBatteryHealth(ClassLoader cl) {
        Method method = findMethod(cl, CLS_BATTERY_HEALTH, "p");
        if (method == null) {
            return;
        }
        try {
            hook(method).setId("cf_health_level").intercept(chain -> {
                ConfigSnapshot c = read();
                if (c.enabled && c.fakeHealth) {
                    return c.healthLevel;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "已 Hook 电池健康度等级 jh.c.p()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 电池健康度等级失败", t);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private ConfigSnapshot read() {
        ConfigSnapshot snapshot = ConfigSnapshot.read(prefs);
        debugLog = snapshot.debugLog;
        return snapshot;
    }

    private Method findMethod(ClassLoader cl, String className, String name, Class<?>... params) {
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            Method method = clazz.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "目标方法不存在，跳过：" + className + "#" + name);
            return null;
        }
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private void d(String message) {
        if (debugLog) {
            log(Log.INFO, TAG, message);
        }
    }

    // ------------------------------------------------------------------
    // Hook：插件「修改UI健康度」（移植自 lspilot TextPatch 插件 main.java）
    // ------------------------------------------------------------------

    /**
     * 把充电保护页（ChargerProtectActivity）上电池健康度百分比的显示改写成
     * 配置里的自定义文本，其它文本一律不动。配置为空时不做任何修改。
     *
     * <p>识别链路与原插件一致：先顺手记下 {@code Resources.getText/getString}
     * 返回的百分数字符串对应的资源名，再在 {@code TextView.setText} 里按
     * 「资源名 + 页面」双条件命中。原插件的 #112 内部编号是易漂移的计数器，
     * 这里不用，只用资源名判定，更稳。</p>
     *
     * <p>无 UI 开关，跟随模块总开关。</p>
     */
    private void hookUiHealth(ClassLoader cl) {
        hookUiHealthResCap(cl);

        try {
            Class<?> bufferType = Class.forName("android.widget.TextView$BufferType", false, cl);
            Method setText = findMethod(cl, "android.widget.TextView", "setText",
                    CharSequence.class, bufferType);
            if (setText != null) {
                hook(setText).setId("cf_ui_health_settext").intercept(chain -> {
                    ConfigSnapshot c = read();
                    if (!c.enabled) {
                        return chain.proceed();
                    }
                    String target = c.uiHealthText;
                    if (target == null || target.isEmpty()) {
                        return chain.proceed();
                    }
                    Object a0 = chain.getArg(0);
                    if (!(a0 instanceof CharSequence)) {
                        return chain.proceed();
                    }
                    String txt = a0.toString();
                    if (txt == null || txt.isEmpty()
                            || target.equals(txt)
                            || !isPercentText(txt)) {
                        return chain.proceed();
                    }
                    Object thisObj = chain.getThisObject();
                    if (thisObj instanceof EditText) {
                        return chain.proceed();
                    }
                    String rn = VALUE2RES.get(txt);
                    if (rn == null || rn.indexOf(RES_HEALTH_PERCENT) < 0) {
                        return chain.proceed();
                    }
                    String act = activityOf(thisObj);
                    if (act != null && act.length() > 0 && !"?".equals(act)
                            && act.indexOf("ChargerProtectActivity") < 0) {
                        return chain.proceed();
                    }
                    d("[修改UI健康度] 命中 [" + rn + "] @ " + act + " : " + txt
                            + " ==> " + target);
                    Object[] args = chain.getArgs().toArray();
                    args[0] = target;
                    return chain.proceed(args);
                });
                log(Log.INFO, TAG, "已 Hook 修改UI健康度");
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook TextView.setText 失败", t);
        }

        try {
            Class<?> actCls = Class.forName(CLS_CHARGER_PROTECT_ACTIVITY, false, cl);
            Method onCreate = actCls.getDeclaredMethod("onCreate", Bundle.class);
            onCreate.setAccessible(true);
            hook(onCreate).setId("cf_ui_health_act").intercept(chain -> {
                lastActivity = CLS_CHARGER_PROTECT_ACTIVITY;
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "充电保护页不存在，跳过页面追踪");
        }
    }

    /** 记录百分数字符串对应的资源名。健康度是格式化串（getString(id, …) 带参），无参重载会漏，必须全拦。 */
    private void hookUiHealthResCap(ClassLoader cl) {
        capResMethod(cl, "getText", new Class<?>[]{int.class}, "cf_ui_health_gettext");
        capResMethod(cl, "getText", new Class<?>[]{int.class, CharSequence.class}, "cf_ui_health_gettext_def");
        capResMethod(cl, "getString", new Class<?>[]{int.class}, "cf_ui_health_getstring");
        capResMethod(cl, "getString", new Class<?>[]{int.class, Object[].class}, "cf_ui_health_getstring_fmt");
    }

    private void capResMethod(ClassLoader cl, String name, Class<?>[] params, String id) {
        Method m = findMethod(cl, "android.content.res.Resources", name, params);
        if (m == null) {
            return;
        }
        try {
            hook(m).setId(id).intercept(chain -> {
                Object result = chain.proceed();
                capUiRes(chain.getThisObject(), chain.getArg(0), result);
                return result;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook Resources." + name + " 失败", t);
        }
    }

    /** 顺手记录百分数字符串对应的资源名（getResourceName 未被 Hook，无重入）。 */
    private void capUiRes(Object thisObj, Object idArg, Object result) {
        if (!(thisObj instanceof Resources)) {
            return;
        }
        if (!(idArg instanceof Integer) || !(result instanceof CharSequence)) {
            return;
        }
        String txt = result.toString();
        if (!isPercentText(txt)) {
            return;
        }
        try {
            String rn = ((Resources) thisObj).getResourceName((Integer) idArg);
            if (rn != null) {
                VALUE2RES.put(txt, rn);
            }
        } catch (Throwable ignored) {
            // 资源 id 非法，忽略
        }
    }

    /** 粗判百分数字符串，如 "100%"（只记这种，降开销）。 */
    private static boolean isPercentText(String s) {
        if (s == null) {
            return false;
        }
        int n = s.length();
        if (n < 2 || n > 5 || s.charAt(n - 1) != '%') {
            return false;
        }
        for (int i = 0; i < n - 1; i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    /** 文本所属 Activity 全类名；Context 链走不通时用最近一次的充电保护页兜底。 */
    private static String activityOf(Object view) {
        if (view instanceof View) {
            Context ctx = ((View) view).getContext();
            for (int i = 0; ctx != null && i < 20; i++) {
                if (ctx instanceof Activity) {
                    return ctx.getClass().getName();
                }
                if (ctx instanceof ContextWrapper) {
                    ctx = ((ContextWrapper) ctx).getBaseContext();
                } else {
                    break;
                }
            }
        }
        return lastActivity != null ? lastActivity : "?";
    }
}
