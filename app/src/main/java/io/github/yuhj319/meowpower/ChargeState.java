package io.github.yuhj319.meowpower;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一次充电状态采样。 */
public final class ChargeState {

    public boolean ok;
    public String message = "";

    /** 电量百分比，-1 表示读不到。 */
    public int capacity = -1;
    /** Charging / Discharging / Full / Not charging。 */
    public String status = "-";
    /** 电流，mA，充电为正。 */
    public int currentMa;
    /** 电压，mV。 */
    public int voltageMv;
    /** 电池温度，摄氏度。 */
    public float tempC;
    /** Good / Overheat / Dead 等。 */
    public String health = "-";
    /** 剩余电量计数值，mAh。 */
    public int chargeCounter = -1;

    /** Settings.Secure 里的快充开关。 */
    public String fastCharge = "-";
    /** Settings.Secure 里的旁路充电状态。 */
    public String bypassState = "-";

    /** 关键 persist 属性原值，key 为属性名。 */
    public final Map<String, String> props = new LinkedHashMap<>();

    /** 采样时刻。 */
    public long timestamp;

    /** 瞬时功率，mW。 */
    public int powerMw() {
        long value = (long) Math.abs(currentMa) * Math.abs(voltageMv);
        return (int) (value / 1000);
    }

    public boolean isCharging() {
        return status != null
                && (status.equalsIgnoreCase("Charging")
                || status.equalsIgnoreCase("Full")
                || status.contains("充电"));
    }

    public String prop(String name) {
        String value = props.get(name);
        return value == null || value.isEmpty() ? "-" : value;
    }
}
