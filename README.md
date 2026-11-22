# MeowPower 喵力全开 — Xiaomi HyperOS / Redmi Security Center LSPosed Module

Xiaomi's Security Center plays it safe: overnight charging caps at 80%, battery saver locks 60 Hz, one-tap cleanup kills background apps… This LSPosed module puts 20+ toggles in your hands — enable or block whatever you want, changes take effect immediately. Works on all Xiaomi / Redmi devices running HyperOS with the built-in Security Center (`com.miui.securitycenter`).

- Package: `io.github.yuhj319.meowpower`, current version 7.2
- Requires: Security Services (Xiaomi/Redmi), Android 16/17, LSPosed (libxposed API 102)

---

## What You Can Control

**Charging Protection**
- Night charging protection (stop the overnight 80% cap), master protection toggle, limit percentage (50–100)
- Pogo dock protection, wireless silent charging, fast charge (don't touch / force on / force off), bypass charging, battery health spoofing
- Custom health display: override the health percentage shown on the charging protection page (leave blank to keep original; tap OK to save and auto-restart Security Services)

**Performance & Power**
- Block power mode switching, ultra battery saver, battery saver frequency/brightness throttling, thermal throttling

**System & Background**
- Remove AppOps private restrictions, prevent one-tap cleanup from killing background apps, force-allow autostart

**Network & Installation**
- Remove network / background network / data-cap cutoff / hotspot data restrictions, bypass app install signature verification

**Other (requires root)**
- Real-time monitor: battery level, current, power, temperature, etc., auto-refreshes every 2 seconds
- Persistent settings: write "disable protection" into system properties and Settings — stays effective even after disabling or uninstalling the module; auto-backup before applying, one-tap restore

The defaults that take effect right after install are night-charge double-block + unrestricted general protection. Enable the rest as needed — it's recommended to start with just "Block protection entry" and observe for a few days before adding more.

---

## How to Use

1. Install the APK.
2. In LSPosed, enable "MeowPower" and select **Security Services** (`com.miui.securitycenter`) as the scope.
3. Restart the Security Services process (or reboot the phone). After changing settings you can also tap "Restart Scope" in the top-right corner of each page.
4. Open the module and enable features as needed.

Root-dependent features (real-time monitor, persistent settings) will prompt for su authorization on first use.

---

## Notes

- "Force enable" fast charging won't work on hardware that doesn't support it; bypass charging only blocks auto on/off, it doesn't force-enable.
- `persist.vendor.*` persistent properties may be blocked by SELinux on some devices — the UI reports each item's status; success rate is highest with Magisk installed.
- This module only modifies local device behavior and does not upload any data.

---

## Changelog

### 6.0

- Added free trial mode: access all features directly (unauthorized mode, resets daily)
- Added "Restart Scope" button in the top-right corner of each page: one-tap restart of Security Services after changing settings
- Added custom health display: override the health percentage on the charging protection page (leave blank to keep original)
- Added "Override limit percentage" toggle: disable to restore the official stock threshold logic
- Home screen connection status now refreshes in real time, no waiting required
