package io.github.yuhj319.meowpower;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久化落地：用 root 把「禁用充电保护」直接写进系统层。
 *
 * <p>与 Hook 的区别：Hook 只在模块启用时生效，模块停用就恢复原状；
 * 这里的改动落在系统属性与 Settings 里，**停用甚至卸载模块后依然有效**，
 * 因为安全中心的保护逻辑在能力判定阶段就会失败。</p>
 *
 * <p>应用前会记录每项原值，支持一键还原。</p>
 */
public final class PersistManager {

    private static final String BACKUP_PREFS = "persist_backup";
    private static final String KEY_APPLIED = "__applied";

    public enum Kind {
        /** 系统属性，优先用 Magisk 的 resetprop 写。 */
        PROP,
        /** Settings.Secure。 */
        SECURE,
        /** Settings.Global。 */
        GLOBAL
    }

    public static final class Item {
        public final String key;
        public final String label;
        public final Kind kind;
        public final String applyValue;

        Item(String key, String label, Kind kind, String applyValue) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.applyValue = applyValue;
        }
    }

    /**
     * 落地项。目标值统一取「让保护判定失败」的方向：
     * 支持位关掉 → 对应保护根本不注册；开关类直接置为不限制。
     */
    private static final List<Item> ITEMS = List.of(
            new Item("persist.vendor.night.charge", "夜间充电保护支持位", Kind.PROP, "false"),
            new Item("persist.vendor.battery.ai.predict", "AI 充电预测", Kind.PROP, "false"),
            new Item("persist.vendor.battery.high.temp.protect", "高温充电保护", Kind.PROP, "false"),
            new Item("persist.vendor.high_temp_stop_charge", "高温停止充电", Kind.PROP, "false"),
            new Item("key_fast_charge_enabled", "快充开关", Kind.SECURE, "1"),
            new Item("key_security_side_road_charge_state", "旁路充电状态", Kind.SECURE, "0"),
            new Item("pref_key_miui_securitycenter_last_battery_health_level", "电池健康等级", Kind.GLOBAL, "4")
    );

    private PersistManager() {
    }

    public static List<Item> items() {
        return ITEMS;
    }

    public static boolean isApplied(Context context) {
        return backup(context).getBoolean(KEY_APPLIED, false);
    }

    /** 执行落地。返回逐项结果，供界面展示。 */
    public static List<String> apply(Context context) {
        List<String> log = new ArrayList<>();
        if (!RootShell.isRootAvailable()) {
            log.add("root 不可用，无法落地");
            return log;
        }

        // 1. 记录原值
        Map<String, String> origin = readAll();
        SharedPreferences.Editor editor = backup(context).edit();
        for (Map.Entry<String, String> entry : origin.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.putBoolean(KEY_APPLIED, true);
        editor.apply();
        log.add("已记录 " + origin.size() + " 项原值，可随时还原");

        // 2. 批量写入
        StringBuilder script = new StringBuilder();
        for (Item item : ITEMS) {
            script.append(writeCommand(item.key, item.kind, item.applyValue)).append('\n');
        }
        RootShell.Result result = RootShell.exec(script.toString());
        String error = result.error.trim();
        if (!error.isEmpty()) {
            log.add("stderr：" + error);
        }

        // 3. 回读校验
        Map<String, String> after = readAll();
        int success = 0;
        for (Item item : ITEMS) {
            String value = after.get(item.key);
            boolean ok = item.applyValue.equals(value);
            if (ok) {
                success++;
            }
            log.add((ok ? "成功  " : "失败  ") + item.label + " = " + value);
        }
        log.add("完成：" + success + "/" + ITEMS.size() + " 项生效");
        if (success < ITEMS.size()) {
            log.add("部分属性写入失败通常是 SELinux 拦截。装 Magisk 并启用 resetprop 后重试。");
        }
        return log;
    }

    /** 还原为落地前的原值。 */
    public static List<String> restore(Context context) {
        List<String> log = new ArrayList<>();
        if (!RootShell.isRootAvailable()) {
            log.add("root 不可用，无法还原");
            return log;
        }

        SharedPreferences backup = backup(context);
        if (!backup.getBoolean(KEY_APPLIED, false)) {
            log.add("没有可还原的记录");
            return log;
        }

        StringBuilder script = new StringBuilder();
        for (Item item : ITEMS) {
            String origin = backup.getString(item.key, null);
            if (origin == null || origin.isEmpty() || "-".equals(origin) || "null".equals(origin)) {
                if (item.kind != Kind.PROP) {
                    script.append("settings delete ").append(namespace(item.kind))
                            .append(' ').append(item.key).append('\n');
                }
                continue;
            }
            script.append(writeCommand(item.key, item.kind, origin)).append('\n');
        }
        RootShell.Result result = RootShell.exec(script.toString());
        String error = result.error.trim();
        if (!error.isEmpty()) {
            log.add("stderr：" + error);
        }

        Map<String, String> after = readAll();
        for (Item item : ITEMS) {
            log.add(item.label + " = " + after.get(item.key));
        }

        backup.edit().putBoolean(KEY_APPLIED, false).apply();
        log.add("已还原");
        return log;
    }

    // ------------------------------------------------------------------

    private static SharedPreferences backup(Context context) {
        return context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE);
    }

    private static String namespace(Kind kind) {
        return kind == Kind.SECURE ? "secure" : "global";
    }

    private static String writeCommand(String key, Kind kind, String value) {
        if (kind == Kind.PROP) {
            // persist.vendor.* 受 SELinux 限制，优先用 Magisk 的 resetprop
            return "if command -v resetprop >/dev/null 2>&1; then resetprop "
                    + key + " " + value + "; else setprop " + key + " " + value + "; fi";
        }
        return "settings put " + namespace(kind) + " " + key + " " + value;
    }

    /** 一次性读回所有项当前值。 */
    private static Map<String, String> readAll() {
        StringBuilder script = new StringBuilder();
        for (Item item : ITEMS) {
            script.append("echo \"R:").append(item.key).append('=')
                    .append("$(")
                    .append(readCommand(item.key, item.kind))
                    .append(" 2>/dev/null)\"\n");
        }
        Map<String, String> result = new LinkedHashMap<>();
        RootShell.Result shellResult = RootShell.exec(script.toString());
        for (String line : shellResult.output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("R:")) {
                continue;
            }
            int index = trimmed.indexOf('=');
            if (index <= 2) {
                continue;
            }
            String key = trimmed.substring(2, index);
            String value = trimmed.substring(index + 1).trim();
            result.put(key, value.isEmpty() ? "-" : value);
        }
        return result;
    }

    private static String readCommand(String key, Kind kind) {
        return kind == Kind.PROP
                ? "getprop " + key
                : "settings get " + namespace(kind) + " " + key;
    }
}
