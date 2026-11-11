# Architecture

## 系统概览

本项目是单模块 Android 原生应用，正式 Application ID 为 `com.navobell.arrivalalarm`，Java 命名空间暂时保持 `com.example.arrivalalarm`。它不使用 AndroidX 或 Google Play Services。Debug 与 Release 共用高德 Android 搜索适配器，分别从本机测试配置和发布构建环境读取对应 Key。`android-app/gradlew.bat` 通过固定版本的 Gradle/AGP 完成测试、Lint、Debug APK 和 Release AAB 构建。`android-app/build-apk.ps1` 仅是兼容入口，不再维护第二套编译链。

```text
用户 / 系统分享 / 深链
          ↓
     MainActivity
          ↓
地点搜索与解析 → AppPrefs → TrackingService → AlarmActivity / 通知
```

## 仓库结构

| 路径 | 职责 |
| --- | --- |
| `android-app/src/com/example/arrivalalarm/` | Android 应用源码 |
| `android-app/test/com/example/arrivalalarm/` | 可脱离 Android Runtime 执行的纯 Java 核心测试 |
| `android-app/res/` | 字符串、颜色、主题、图标等资源 |
| `android-app/AndroidManifest.xml` | 权限、Activity、Service、Receiver 和外部入口 |
| `android-app/gradlew.bat` | 唯一构建系统入口；生成 Debug APK、Release AAB 并运行测试和 Lint |
| `android-app/build-apk.ps1` | 调用 Gradle Wrapper 并复制旧文件名 Debug APK 的兼容入口 |
| `outputs/` | 本机构建产物；不作为源码或发布事实来源 |
| `docs/` | 产品、架构、流程、决策和复盘文档 |

根目录的空 `src/`、`tests/` 和 `icons/` 目前不属于 Android 构建调用链。新增实现前应先决定保留、迁移或删除策略，不能形成第二套平行源码结构。

## 主要组件

| 组件 | 当前职责 |
| --- | --- |
| `MainActivity` / `HomeUiState` | 首页渲染、用户操作和外部 Intent，保存草稿、待确认内容和权限上下文 |
| `PlaceSearchRepository` | 组合地点搜索提供方、系统反查和搜索附近位置，保存每次请求选定的提供方 |
| `ReminderPermissions` / `ReminderRuntimeCoordinator` | 核心权限检查与提醒启动、恢复、确认和停止 |
| `AmapPlaceSearch` / `PhotonPlaceSearch` | 地点联想与搜索；高德可由构建时 Key 启用，Photon 为默认网络来源 |
| `PlaceInputParser` / `ShareParser` / `PlaceQuality` | 解析外部地点输入并过滤低质量候选 |
| `CoordinateTransform` | 高德 GCJ-02 与提醒计算使用的坐标之间转换 |
| `AppPrefs` | 使用私有 `SharedPreferences` 保存任务、最近地点和设置；升级时保留但停用旧固定日程数据 |
| `TrackingService` / `ArrivalNotification` | 前台定位、定位恢复、到站提醒节奏和通知渠道 |
| `CoreLogic` / `ReminderStateLogic` / `LocationRecoveryPolicy` / `AsyncResultGate` | 距离、半径、定位可靠性、提醒运行态、有界恢复策略和异步结果有效期等可纯 Java 测试的核心逻辑 |
| `AlarmActivity` / `ActionReceiver` | 到站页面展示与“已到达/停止”操作 |
| `ScheduleLogic` / `ScheduleManager` 等原型类 | 后续固定日程实验代码；首发无 Manifest Receiver、无 UI 入口且升级时取消旧调度 |
| `TransitNotificationListener` / `JourneyPrompt` 等原型类 | 后续出行识别实验代码；首发无 Manifest 服务、无 UI 入口且服务拒绝恢复该模式 |

## 关键运行流

### 手动到站提醒

1. `MainActivity` 解析输入，`PlaceSearchRepository` 依次尝试已配置的高德 Web 服务、Photon 和系统 `Geocoder` 获取候选。用户同意启用高德 Android 搜索后，仅使用该提供方查询。
2. 用户确认后，`ReminderRuntimeCoordinator` 原子更新 `starting` 任务与最近地点。
3. `ReminderRuntimeCoordinator` 以 `ACTION_START_REMINDER` 启动 `TrackingService`。
4. `TrackingService` 进入前台并成功注册至少一个定位 Provider 后，将任务提交为 `active`；在此之前任务不会被持久化为 active。短暂监听异常会在服务内部进行有限重试；权限、Provider 关闭、前台启动限制或重试耗尽后转为 `interrupted`，并提供手动恢复入口。
5. `TrackingService` 通过 `CoreLogic` 丢弃低精度、非法坐标、过旧或乱序定位。实时距离随任务 ID 保存在进程内，任务只在状态变更时持久化。
6. 到站后停止定位，`TrackingService` 切换到媒体播放前台服务类型，`ArrivalAlarm` 使用系统闹钟音量循环播放默认闹钟铃声并振动。「到站确认」通知提供闹钟页和确认入口。确认、停止或替换任务会停止声振。五分钟后服务退出，通知和待确认任务保留。

任务的持久化状态和服务运行态是两个独立事实。`ReminderStateLogic` 只有在任务为 `active`、当前进程中的 `TrackingService` 存在且心跳未过期时才判定为“正在监控”；应用打开时若本地仍有任务但服务不在运行，会立即标记为 `interrupted`，首页显示“需要恢复”和一个恢复按钮。恢复只能由用户在可见 Activity 或恢复通知中主动触发，服务使用 `START_NOT_STICKY`，不会因系统重启或进程终止自行拉起。

运行态由 `ReminderRuntime.Snapshot` 表达，生命周期依次区分“启动请求 (`STARTING`)”、“已进入前台 (`FOREGROUND`)”、“已注册定位监听 (`LOCATION_READY`)”、“有限自愈中 (`RECOVERING`)”和“已失败 (`FAILED`)”。只有定位监听注册成功、至少一个 Provider 仍被本服务持有、收到过首个定位、最近定位未超过允许窗口且单调时钟心跳未过期时，UI 才显示“正在守候”；仅进入前台、仅注册监听或长期没有新定位都不会被伪装成监控中。精确提醒使用零距离过滤和周期定位，避免静止设备被误判为挂机。服务心跳仍会主动检查定位新鲜度，即使 Activity 在后台也会进入 `RECOVERING`；服务先以 10 秒、30 秒退避重新注册 Provider，最多尝试两次，并使用零距离过滤的周期探针区分静止与挂机。收到新定位后恢复普通定位参数和监控状态；权限或 Provider 问题以及重试耗尽才会降级为 `interrupted` 并提供恢复入口。Provider 由 `ReminderProviderRegistry` 按名称集合维护，重复或乱序的禁用回调不会错误减少数量。启动请求使用运行代次隔离旧服务的延迟 `onDestroy()` 和迟到定位回调，避免旧实例覆盖新的恢复状态。

`ReminderRuntimeCoordinator` 是 Activity、通知 `ActionReceiver` 和服务之间的统一动作入口：开始、恢复、停止和状态协调都经过它。恢复时向同一服务实例发送幂等启动请求，不先异步停止再启动，避免 `stopService()` 与 `startForegroundService()` 竞态。运行心跳和启动超时使用 `SystemClock.elapsedRealtime()`，不使用可被用户修改的墙上时间；运行快照只保存在进程内，不写入 `AppPrefs`。

服务到 Activity 的进程内状态广播使用按变体 Application ID 派生的 `signature` 权限；API 33+ 还注册为 `RECEIVER_NOT_EXPORTED`。Debug 与 Release 因而拥有不同权限名，可以并存安装，也不会接受其他签名应用伪造的状态刷新。

Activity 搜索任务由页面取消 Future，结果 gate 负责使旧请求失效，提交前同时检查组件生命周期和请求 token。页面重建后恢复当前草稿与确认状态，并重新发起仍适用的未完成查询。

### 首发禁用的原型调用链

- 长期车站识别、速度/位移出行检测、使用情况访问和通知读取没有首发 UI 或 Manifest 入口。
- 固定日程 Receiver 与开机 Receiver 不注册，已有日程只保留本机数据并取消系统调度。
- `TrackingService` 启动时清除旧出行检测运行状态，不会在任务结束或进程恢复后进入自动检测模式。
- 首发 Activity 和服务中的无入口原型实现已清除。旧日程数据格式与后续自动车站设计独立保留。

当服务在应用仍处于前台时主动发现权限、Provider 或监听初始化失败，会立即将任务标记为 `interrupted` 并发送恢复通知；通知提供用户触发的“恢复”动作。设备重启而应用从未运行的场景不能产生即时通知，必须等用户打开应用触发状态协调，这是 P0-2 禁止开机 Receiver 的明确边界。

## 数据和信任边界

目的地通知默认隐藏名称、地址、坐标和距离。`AppPrefs` 保存用户主动开启的通知显示偏好，`NotificationPrivacy` 构建只含通用状态的公开版本；Service 监听该偏好并更新当前通知，Activity 保存设置时更新中断恢复通知。到站详情页面不覆盖锁屏。系统锁屏/渠道设置仍决定实际通知展示，用户开启目的地显示前通过设置文案告知这一边界。

- 本机持久数据：`AppPrefs` 中的提醒任务、最近地点和设置；升级用户可能仍保留不可触发的旧固定日程记录。
- 外部网络：Photon/OpenStreetMap 或构建时配置的高德地点服务；系统 `Geocoder` 的具体实现由设备决定。
- 高德 Android 搜索：用户在首页选择高德并同意说明后，查询交给 Android 搜索 SDK。高德模式独立返回结果及失败，应用不主动传入附近位置。
- 敏感权限：精确/大致位置和通知；location 前台服务只由用户在可见 Activity 中主动启动。
- 密钥：`ARRIVAL_AMAP_WEB_KEY` 仅作为构建输入；不得写入源码、日志、测试或 Git 历史。
- 高德 Android 测试 Key：从被 Git 忽略的 `android-app/local.properties` 读取，只注入 Debug 构建。Release Key 通过 `ARRIVAL_AMAP_ANDROID_RELEASE_KEY` 注入，签名工作流从 `play-release` 环境读取对应 Secret。
- 发布签名：Debug 与 Release 分离；上传密钥只从安全环境变量读取，Play App Signing 管理面向用户的应用签名密钥。

## 架构修改门禁

以下变更实施前必须更新 `docs/DECISIONS.md`：模块或数据所有权变化、新依赖、新网络服务、权限变化、持久化格式变化、后台执行策略变化、最低/目标 Android 版本变化、构建或签名流程变化。
