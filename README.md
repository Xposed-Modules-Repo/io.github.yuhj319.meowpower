# MeowPower For HyperOS

自由调节小米安全中心（`com.miui.securitycenter`）的充电策略，去除充电限制。

- 模块类型：LSPosed 模块
- API 基线：**libxposed API 102**（现代 Xposed API，非 legacy XposedBridge）
- 适配系统：**Android 16 / 17**（compileSdk 37 / targetSdk 37 / minSdk 29）
- 目标版本：安全服务 13.5.3-260822.0.1（versionCode 99999999）
- 界面：Kotlin + Jetpack Compose + **Miuix 0.9.3**（HyperOS 风格组件库）
- 构建：AGP 9.4.0 / Gradle 9.6.0 / JDK 21

---

## 1. 功能

### 1.1 充电保护拦截（Hook）

| 分组 | 功能 | 拦截目标 |
|---|---|---|
| 总开关 | 启用模块 | 关闭后所有 Hook 干预立即停止 |
| 夜间充电保护 | 拦截保护入口 | `NightChargeProtectManager.C()` + `M()` |
| 夜间充电保护 | 拦截状态写入 | `e.r(int)` → `setNightChargingState(1)` |
| 通用充电保护 | 拦截全部保护 | `hg.a.l(true)` 总闸，7 个策略一次覆盖 |
| 通用充电保护 | 拦截通用写入 | `e.n(int)` / `e.p(String,int)` |
| 通用充电保护 | 限制百分比 | 50–100 可调，100 = 不下发限制 |
| 其它保护 | pogo 底座充电保护 | `gh.e.n()` / `gh.e.m(int,int)` |
| 其它保护 | 关闭无线静音充电 | `jh.e0.b(boolean)`（smart_chg 0x80） |
| 性能与电源 | 拦截电源模式切换 | `PowerSaverProvider.call()` |
| 性能与电源 | 拦截超省电模式 | `uj.p.X(boolean,boolean,boolean)` |
| 性能与电源 | 禁止降频 / 降亮度 | `jh.x.G/H(int)`、`I()`、`O(int)` |
| 性能与电源 | 禁止温控降频 | `jh.g0.e(int,boolean)` |
| 系统与后台 | 解除 AppOps 限制 | `AppOpsUtilsCompat.setMode/setModeWithXSpace/setUidMode` |
| 系统与后台 | 禁止清理杀后台 | `MemoryCheck.d4(...)` / `O9(String)` |
| 系统与后台 | 强制允许自启动 | `AppManageUtils.w0(Context,int,String,boolean)` |
| 网络 | 解除联网限制 | `MiuiNetdFirewall.setWifiRule/setMobileRule` |
| 网络 | 解除后台联网限制 | `BackgroundPolicyService.setAppRestrictBackground/setRestrictBackground` |
| 网络 | 解除流量超限停网 | `TrafficSimManager.onNormalTrafficOverLimit` |
| 网络 | 解除热点流量限制 | `TetherStatsManager.onTetherStatsOverLimit` |
| 安装 | 解除安装签名校验 | `PackageVerificationReceiver.c(...)` |
| 快充 | 不干预 / 强制开 / 强制关 | `ng.c.i()` + 能力判定 + `jh.p.d()` |
| 旁路充电 | 不干预 / 阻止开启 / 阻止停止 | `jg.a.D()/E()/F()` |
| 电池健康度 | 伪装等级 1–4 | `jh.c.p()` |

**默认策略**：装完即生效 —— 夜充保护入口与状态双拦截 + 通用保护阈值置为 100（不下发限制）。

### 1.2 实时监控（root）

读取 sysfs 节点与系统属性，展示真实充电状态：电量、状态、电流、电压、温度、健康度、瞬时功率，以及 `smartchg`、夜充支持位、高温保护、AI 预测等关键属性的当前值。

支持手动刷新与 2 秒自动刷新。

同时展示 **Hook 命中统计**：每个 Hook 点的累计拦截次数与最后上报时间。这是判断模块是否真的在工作的最直接依据 —— 如果全是 0，说明模块没装载或被作用域漏掉。

### 1.3 持久化落地（模块关闭后仍生效）

用 root 把「禁用充电保护」直接写进系统层：

| 键 | 落地值 | 作用 |
|---|---|---|
| `persist.vendor.night.charge` | false | 夜充功能判定失败，保护不注册 |
| `persist.vendor.battery.ai.predict` | false | 关闭 AI 充电预测分支 |
| `persist.vendor.battery.high.temp.protect` | false | 关闭高温充电保护 |
| `persist.vendor.high_temp_stop_charge` | false | 关闭高温停止充电 |
| `key_fast_charge_enabled` (secure) | 1 | 快充开关置为开启 |
| `key_security_side_road_charge_state` (secure) | 0 | 关闭旁路充电状态 |
| `pref_key_miui_securitycenter_last_battery_health_level` (global) | 4 | 健康等级置为最优 |

**与 Hook 的区别**：Hook 只在模块启用时生效，停用即恢复原状；这些改动落在系统属性与 Settings 里，保护逻辑在**能力判定阶段**就失败，所以**停用甚至卸载模块后依然有效**。

应用前会自动备份每项原值，支持一键还原。属性写入优先使用 Magisk 的 `resetprop`（`persist.vendor.*` 受 SELinux 限制，普通 `setprop` 常被拒绝）。

---

## 2. 构建

环境要求：JDK 21、Android SDK 含 `platforms/android-37` 与 `build-tools/37.0.0`。

```bash
./gradlew assembleDebug     # 快速验证
./gradlew assembleRelease   # R8 混淆
```

产物在 `app/build/outputs/apk/`（debug ≈ 27 MB，release ≈ 2.5 MB，Compose 体积所致）。
`local.properties` 的 `sdk.dir` 需指向本机 SDK。

### 构建期踩坑（改依赖时必读）

Miuix 用 Kotlin 2.4.x 编译，而 AGP 自带的 Kotlin 编译器版本偏低，两者 metadata 不兼容，会报
`Module was compiled with an incompatible version of Kotlin`。三处配置是配套的，缺一不可：

1. **`gradle.properties`**：`android.builtInKotlin=false` + `android.newDsl=false`
   —— 停用 AGP 内置 Kotlin（否则无法应用标准 KGP，且 `newDsl` 与 `kotlin-android` 插件不兼容）。
2. **`app/build.gradle.kts`**：`kotlin { compilerOptions { freeCompilerArgs.add("-Xskip-metadata-version-check") } }`
   —— AGP 会把 KGP 强行放到 classpath 上、版本无法覆盖，只能让编译器跳过 metadata 版本校验。
3. **`app/build.gradle.kts`**：禁用 `*ComposeMapping*` 任务
   —— 该任务要拉 `org.jetbrains.kotlin:compose-group-mapping`，内置 Kotlin 版本没有对应的发布产物。
   生产任务与校验任务要一起禁，只禁生产会因输入文件缺失而失败。

另外 release 的 R8 会误报 `androidx.window.**` 缺失（这些扩展类只在设备上存在），已在
`proguard-rules.pro` 里 `-dontwarn` 掉。

---

## 3. 安装与启用

1. 安装 APK。
2. LSPosed 管理器 → 模块 → 启用「喵力全开」。
3. 作用域勾选 **安全服务**（`com.miui.securitycenter`）。模块已声明 `scope.list`，通常会自动勾选。
4. **重启「安全服务」进程**（首次启用必须，否则 Hook 不会装载）。
5. 打开模块 App 调整配置 —— 改完即时生效，无需重启目标应用。

需要 root 的功能（实时监控、持久化落地）首次使用时会弹出 su 授权。

---

## 4. Hook 点清单

所有类名均经 jadx 反编译 + baksmali 反汇编双向核验。混淆类名在应用更新后可能变化，代码里每个 Hook 都做了存在性检查，缺失只打日志不影响其它 Hook。

| 目标 | 方法 | 干预方式 |
|---|---|---|
| `hg.a`（BaseChargeProtect） | `l(Z)V` | 参数 true 时 return（保护总闸） |
| `…charge.protect.NightChargeProtectManager` | `C(Landroid/content/Context;)V` | 直接 return |
| 同上 | `M(Landroid/content/Context;)V` | 直接 return |
| `…charge.protect.e` | `r(I)Ljava/lang/Boolean;` | 参数 1 时返回 `Boolean.FALSE` |
| 同上 | `n(I)V` / `p(Ljava/lang/String;I)V` | 整体拦截或改写百分比 |
| `gh.e`（SmartChargeManager） | `n()V` / `m(II)V` | pogo 保护不启动 |
| `jh.e0`（SmartChargeUtils） | `b(Z)V` | 强制关闭无线静音 |
| `ng.c`（FastChargeController） | `i(Z)V`、`e()Z`、`f()Z`、`g()Z` | 强制改写开关与能力判定 |
| `jh.p`（MiChargeWrapper） | `d(Z)V` | 底层 `smart_chg` bit0 强制改写 |
| `jg.a`（SideRoadChargeManager） | `D()V`、`E()V`、`F()V` | 阻止自动开启 / 自动停止 |
| `jh.c`（BatteryHealthUtils） | `p()I` | 返回伪装的健康度等级 |
| `com.miui.powercenter.powersaver.PowerSaverProvider` | `call(String,String,Bundle)` | 电源模式切换来一个拦一个，查询放行 |
| `uj.p`（SuperPowerSaveManager） | `X(ZZZ)V` | 超省电不进入（含禁用应用/杀后台/冻结自启/动画归零） |
| `jh.x`（PowerTaskManager） | `G(I)V`、`H(I)V`、`I()V`、`O(I)V` | 亮度、刷新率、息屏超时的省电降级 |
| `jh.g0`（ThermalStoreUtils） | `e(IZ)V` | 温控配置不下发 |
| `com.miui.permcenter.compact.AppOpsUtilsCompat` | `setMode`/`setModeWithXSpace`/`setUidMode` | 7 种 AppOps 私有限制统一放行 |
| `com.miui.securitycenter.memory.MemoryCheck` | `d4(List,IMemoryCleanupCallback)`、`O9(String)` | 一键清理不杀进程 |
| `com.miui.appmanager.AppManageUtils` | `w0(Context,int,String,boolean)` | 自启动强制置为允许 |
| `com.miui.networkassistant.firewall.impl.MiuiNetdFirewall` | `setWifiRule(String,FirewallRule)`、`setMobileRule(String,FirewallRule,int,boolean)` | 规则强制改为 `FirewallRule.Allow` |
| `com.miui.networkassistant.firewall.BackgroundPolicyService` | `setAppRestrictBackground(int,boolean)`、`setRestrictBackground(boolean)` | 后台联网限制置 false |
| `com.miui.networkassistant.service.tm.TrafficSimManager` | `onNormalTrafficOverLimit()` | 不执行（流量超限不停网） |
| `com.miui.networkassistant.service.tm.TetherStatsManager` | `onTetherStatsOverLimit()` | 不执行（热点不限流） |
| `com.miui.permcenter.install.PackageVerificationReceiver` | `c(Context,PackageInfo,String,String)` | 返回 true 放行安装 |

### 被覆盖的 7 个保护策略

`hg.a.l(boolean)` 是它们的共同启动闸门，且**全库只有一份实现、没有任何子类重写**，所以一条 Hook 就能覆盖全部：

| MODE | 类 | 含义 |
|---|---|---|
| `MODE_NIGHT` | `NightChargeProtectManager` | 夜间充电保护 |
| `MODE_NAVIGATION` | `NavigationChargeProtectManager` | 导航充电保护 |
| `MODE_ALWAYS` | `charge.protect.a` | 始终保护 |
| `MODE_HANDLE` | `charge.protect.c` | 手柄场景保护（机型 aurora） |
| `MODE_HIGH_TEMP` | `charge.protect.f` | 高温保护 |
| `MODE_LONG_TIME_CHARGE` | `charge.protect.h` | 长时间充电保护 |
| `MODE_NO_PROTECT` | `charge.protect.q` | 无保护占位 |

### 关键事实（易踩坑）

- **「卡 80%」不是代码里的数字。** 夜充保护走 `e.r(1)` → `IMiCharge.setNightChargingState(1)`，80 是底层 charge 固件里的阈值。所以只能拦调用，不能"把 80 改成 100"。
- **`e.n(int)` 是另一条路。** 它写 `setMiChargePath("smart_chg", "0x"+(percent<<16|17))`，高 16 位是限制百分比，只被高温/导航/长充等通用保护使用。
- **`e.r(int)` 全库只被夜充调用**（`M()`/`L()`/`N()`），拦它不影响其它策略。
- **选 Hook 点必须确认方法有没有被重写。** 反例：`hg.a.i(boolean)` 被 `hg.c` 重写了，如果去拦 `i()` 会静默失效。拦 `l()` 才是正确的总闸。
- **`PowerSaveService` 跑在 `com.miui.securitycenter.remote` 进程**，Hook 框架必须覆盖该进程（本模块不区分进程，目标包内所有进程都会装载）。
- **AppOps 封装的参数顺序与标准 API 相反**：安全中心自己的 `AppOpsUtilsCompat.setMode(appOps, pkg, uid, op, mode)` 把 pkg 放在第 2 位、op 在第 4 位，而标准 `AppOpsManager.setMode(op, uid, pkg, mode)` 顺序不同。照抄标准顺序会导致判断完全失效。
- **自启动不走 Settings 也不走 AppOps**，走平台权限库（`PermissionManager.PERM_ID_AUTOSTART`），常规 AppOps 方案对它无效，必须打在 `AppManageUtils.w0` 这个写入点上。
- **`jh.x`（PowerTaskManager）的方法是实例方法，`PermissionManager` 也是**。libxposed 的 Hook 按方法拦截，与实例无关，但取参数时要注意 index 从 0 开始算的是形参，不含 this。

---

## 5. 配置存储

配置存放在 LSPosed **RemotePreferences**（group = `config`）：

- 模块 App 侧：`XposedService.getRemotePreferences("config")`
- 目标进程侧：`XposedModule.getRemotePreferences("config")`

两侧共享同一份数据，LSPosed 内部带失效检测，因此改完即时生效，不需要热重载。

Hook 命中统计以 `stat_<hook名>` 为键写在同一份存储里，节流到每秒最多上报一次。

---

## 6. 已知限制

- 只适配安全服务 **13.5.3-260822.0.1**。其它版本混淆类名可能变化，需要重新核验（搜索字符串 `"BaseChargeProtect_Night"`、`"ChargeProtectionUtils"`、`"MODE_NIGHT"` 定位）。
- 快充「强制开启」依赖 `ng.c` 的三个能力判定方法；机型底层若不支持对应硬件，改写判定也无法凭空开启。
- 旁路充电只做「阻止自动开启 / 自动停止」，不做强制开启（需重放整套状态机，风险过高）。
- 健康度伪装只影响 `jh.c.p()` 的等级出口，不改写底层 SOH 原始数据。
- 持久化落地中的 `persist.vendor.*` 属性在部分设备上会被 SELinux 拦截，界面会逐项报告成功/失败；装 Magisk 后走 `resetprop` 成功率最高。
- 监控读取的 sysfs 单位因平台而异，代码做了自动换算（电流 µA/mA、电压 µV/mV、温度 0.1℃/℃），个别机型可能显示偏差。
