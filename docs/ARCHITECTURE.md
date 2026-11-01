# Architecture

## 系统概览

本项目是单模块 Android 原生应用，正式 Application ID 为 `com.navobell.arrivalalarm`，Java 命名空间暂时保持 `com.example.arrivalalarm`。它不使用 AndroidX、Google Play Services 或第三方运行时 SDK；`android-app/gradlew.bat` 通过固定版本的 Gradle/AGP 完成测试、Lint、Debug APK 和 Release AAB 构建。`android-app/build-apk.ps1` 仅是兼容入口，不再维护第二套编译链。

```text
用户 / 系统分享 / 深链
          ↓
     MainActivity
          ↓
地点搜索与解析 → AppPrefs → TrackingService → AlarmActivity / 通知
                       ↘
固定日程 → AlarmManager → FixedAlarmReceiver
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

## 主要组件

| 组件 | 当前职责 |
| --- | --- |
| `MainActivity` | 单页 UI、权限引导、地点选择、任务和固定日程管理、外部 Intent 处理 |
| `AmapPlaceSearch` / `PhotonPlaceSearch` | 地点联想与搜索；高德可由构建时 Key 启用，Photon 为默认网络来源 |
| `PlaceInputParser` / `ShareParser` / `PlaceQuality` | 解析外部地点输入并过滤低质量候选 |
| `CoordinateTransform` | 高德 GCJ-02 与提醒计算使用的坐标之间转换 |
| `AppPrefs` | 使用私有 `SharedPreferences` 保存任务、最近地点、固定日程和设置 |
| `TrackingService` | 前台定位、任务状态、出行检测、车站提示和提醒触发 |
| `CoreLogic` | 距离、半径、定位可靠性和出行状态等可纯 Java 测试的核心逻辑 |
| `AlarmActivity` / `ActionReceiver` | 全屏提醒展示与“已到达/停止”操作 |
| `ScheduleLogic` / `ScheduleManager` | 计算下一次日程并通过 `AlarmManager` 注册或取消 |
| `FixedAlarmReceiver` / `BootReceiver` | 触发固定日程，并在重启、更新或精确闹钟权限变化后重建调度 |
| `TransitNotificationListener` | 在用户授权后，内存中过滤支持来源的交通通知信号 |
| `JourneyPrompt` / `FixedAlarmPrompt` | 在不能静默开始任务或检测到出行信号时提示用户 |

## 关键运行流

### 手动到站提醒

1. `MainActivity` 解析输入，并依次尝试高德、Photon 和系统 `Geocoder` 获取候选。
2. 用户确认后，`MainActivity` 通过 `AppPrefs.saveTask` 保存任务。
3. `MainActivity` 以 `ACTION_START_REMINDER` 启动 `TrackingService`。
4. `TrackingService` 作为前台定位服务接收位置，使用 `CoreLogic` 判断定位可靠性和到站条件，并持续保存任务状态。
5. 触发后显示高优先级通知/全屏提醒；`AlarmActivity` 或 `ActionReceiver` 发送 `ACTION_STOP_REMINDER` 清理任务并停止服务。

### 固定日程

1. `MainActivity` 保存日程并调用 `ScheduleManager.schedule`。
2. `ScheduleManager` 使用精确或非精确 `AlarmManager` API 注册 `FixedAlarmReceiver`。
3. Receiver 先安排下一次触发，再检查活动任务、核心权限和后台启动条件。
4. 条件允许时保存任务并启动 `TrackingService`；否则使用 `FixedAlarmPrompt` 要求用户点按继续。
5. `BootReceiver` 在设备重启、应用更新和精确闹钟权限变化后调用 `scheduleAll`。

### 可选出行信号

- `TrackingService` 可基于位置、速度、位移、车站反查或支持应用的使用情况提示设置目的地。
- `TransitNotificationListener` 只在用户授予通知使用权后处理支持包名的通知，并通过 `TransitSignalMatcher` 匹配信号。
- 这些信号只生成提示，不应静默覆盖已有活动任务。

## 数据和信任边界

- 本机持久数据：`AppPrefs` 中的提醒任务、最近地点、固定日程、设置和部分出行检测状态。
- 外部网络：Photon/OpenStreetMap 或构建时配置的高德地点服务；系统 `Geocoder` 的具体实现由设备决定。
- 敏感权限：位置、后台位置、通知、精确闹钟、使用情况访问和通知使用权。
- 密钥：`ARRIVAL_AMAP_WEB_KEY` 仅作为构建输入；不得写入源码、日志、测试或 Git 历史。
- 发布签名：Debug 与 Release 分离；上传密钥只从安全环境变量读取，Play App Signing 管理面向用户的应用签名密钥。

## 架构修改门禁

以下变更实施前必须更新 `docs/DECISIONS.md`：模块或数据所有权变化、新依赖、新网络服务、权限变化、持久化格式变化、后台执行策略变化、最低/目标 Android 版本变化、构建或签名流程变化。
