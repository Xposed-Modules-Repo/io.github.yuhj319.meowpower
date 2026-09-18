package io.github.yuhj319.meowpower;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 充电状态实时采样。一次 root 调用读完所有需要的节点，避免多条命令的开销。
 *
 * <p>必须在线程外调用，{@link #snapshot()} 会阻塞（内部走 su）。</p>
 */
public final class ChargeMonitor {

    private static final String SCRIPT = """
            D=/sys/class/power_supply/battery
            [ -d "$D" ] || D=/sys/class/power_supply/bms
            echo "CAP=$(cat $D/capacity 2>/dev/null)"
            echo "STATUS=$(cat $D/status 2>/dev/null)"
            echo "CUR=$(cat $D/current_now 2>/dev/null)"
            echo "VOLT=$(cat $D/voltage_now 2>/dev/null)"
            echo "TEMP=$(cat $D/temp 2>/dev/null)"
            echo "HEALTH=$(cat $D/health 2>/dev/null)"
            echo "COUNTER=$(cat $D/charge_counter 2>/dev/null)"
            echo "FAST=$(settings get secure key_fast_charge_enabled 2>/dev/null)"
            echo "BYPASS=$(settings get secure key_security_side_road_charge_state 2>/dev/null)"
            echo "PROP:persist.vendor.night.charge=$(getprop persist.vendor.night.charge)"
            echo "PROP:persist.vendor.smartchg=$(getprop persist.vendor.smartchg)"
            echo "PROP:persist.vendor.battery.ai.predict=$(getprop persist.vendor.battery.ai.predict)"
            echo "PROP:persist.vendor.battery.high.temp.protect=$(getprop persist.vendor.battery.high.temp.protect)"
            echo "PROP:persist.vendor.high_temp_stop_charge=$(getprop persist.vendor.high_temp_stop_charge)"
            echo "PROP:persist.vendor.smart.bypass.plus=$(getprop persist.vendor.smart.bypass.plus)"
            echo "DONE"
            """;

    private ChargeMonitor() {
    }

    public static ChargeState snapshot() {
        ChargeState state = new ChargeState();
        state.timestamp = System.currentTimeMillis();

        RootShell.Result result = RootShell.exec(SCRIPT);
        if (!result.ok) {
            state.ok = false;
            state.message = result.error.trim().isEmpty()
                    ? "root 不可用或未授权"
                    : result.error.trim();
            return state;
        }

        Map<String, String> map = parse(result.output);
        state.capacity = parseInt(map.get("CAP"), -1);
        state.status = value(map.get("STATUS"), "-");
        state.health = value(map.get("HEALTH"), "-");

        // sysfs 单位因平台而异：电流多为 µA、电压多为 µV、温度多为 0.1℃
        int current = parseInt(map.get("CUR"), 0);
        state.currentMa = Math.abs(current) > 20000 ? current / 1000 : current;

        int voltage = parseInt(map.get("VOLT"), 0);
        state.voltageMv = voltage > 10000 ? voltage / 1000 : voltage;

        int temp = parseInt(map.get("TEMP"), 0);
        state.tempC = temp > 200 ? temp / 10f : temp;

        int counter = parseInt(map.get("COUNTER"), -1);
        state.chargeCounter = counter > 100000 ? counter / 1000 : counter;

        state.fastCharge = value(map.get("FAST"), "-");
        state.bypassState = value(map.get("BYPASS"), "-");

        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().startsWith("PROP:")) {
                state.props.put(entry.getKey().substring(5), entry.getValue());
            }
        }

        state.ok = true;
        state.message = "已更新";
        return state;
    }

    /** 解析 KEY=VALUE 行，值里允许再出现 '='。 */
    private static Map<String, String> parse(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (output == null) {
            return map;
        }
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || "DONE".equals(trimmed)) {
                continue;
            }
            int index = trimmed.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = trimmed.substring(0, index).trim();
            String val = trimmed.substring(index + 1).trim();
            if (val.isEmpty() || "null".equals(val)) {
                val = "-";
            }
            map.put(key, val);
        }
        return map;
    }

    private static String value(String raw, String fallback) {
        return raw == null || raw.isEmpty() || "-".equals(raw) ? fallback : raw;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty() || "-".equals(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }
}
