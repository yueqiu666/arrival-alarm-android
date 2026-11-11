# Development Log

## 2026-10-06 — 保存发布加固检查点，暂缓统一设备验收

- 项目负责人授权：先完成已确认问题的源码修复和相关单测/build/lint，再集中进行模拟器及真机回归；设备复现依赖项保留 BLOCKED。
- 本次将此前未提交的源码、资源、测试和文档一起保存为备份 checkpoint；使用 GitHub 独立分支，不视为可发布版本。
- P0-1～P0-5 均仍有内部测试、外部材料或设备验收未完成，详细状态已汇总至 RELEASE_READINESS.md；P0-5 自动 instrumentation 仍 BLOCKED。
- SDK、AVD 和 APK 普通启动已经可用；暂停重复安装和无效 instrumentation 重试。此前未经充分证据的根因判断已在 experiment_log.md 更正。

## 2026-09-21 — P0-3/P0-4/P0-5 生命周期一致性统一修复

### 调查结论

三个问题共享同一类根因，但故障层级不同：P0-3 是持久化任务与服务运行事实不一致；P0-4 是 FGS 启动失败或定位中断后可能假激活、缺失恢复路径；P0-5 是 Activity/Service 已经失效或请求已经被替换后，旧异步结果仍尝试提交。`Future.cancel()` 不能保证结果未排队，因此取消必须和组件所有权、请求代次以及业务状态检查一起使用。

### 统一机制

- 新增 `AsyncResultGate`，用单调递增 token 标记异步请求；新请求或取消操作立即使旧结果失效。
- `MainActivity` 的地点联想、坐标确认和固定地点解析统一使用结果 gate；`onStop`/`onDestroy` 取消 Future，回调同时检查 Activity 代次、STARTED 状态、`isFinishing`、`isDestroyed` 和结果 token。
- `TrackingService` 的车站查询使用服务实例代次与结果 gate；命令切换、停止和销毁时取消 Future，旧服务或旧查询不能再显示提示。
- P0-3/P0-4 保持“持久化 `starting`/`active`/`interrupted` 状态”和“进程内运行快照”分离；只有服务进入前台并成功注册定位 Provider 后才提交 `active`，定位过期先有界重试，重试耗尽或权限/Provider 失败才转为 `interrupted`。
- 没有引入 AndroidX、第三方生命周期库或 WorkManager，也没有改变首发权限和功能边界。

### 验证

- `android-app/build-apk.ps1 -SdkRoot C:\temp\codex-android-sdk`：PASS；Debug 单测 `84 tests passed`、`LocationRecoveryPolicyTest: PASS`、Lint、编译和 APK 均成功。
- `android-app/build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`：PASS；Release 单测、Lint、R8、首发权限门禁、AAB 和 Bundletool 验证均成功。当前产物为未签名工程验证 AAB，不得作为正式上传包。
- 纯 Java 测试和静态构建不能证明真实 Activity 旋转、进程终止、Android 12–16 FGS 限制、厂商省电、权限撤销或通知动作行为；这些路径仍为 `BLOCKED`，需要真机或 instrumentation 验收。

## 2026-08-30 至 2026-09-01 — P0-3 监控中断恢复与完整复验

### 初版问题与进度

P0-3 的初版问题不是“任务数据丢失”，而是应用把持久化任务和前台定位服务运行态当成同一个事实：`AppPrefs` 中仍为 `active` 时，进程或服务实际上可能已经停止，首页仍可能误导用户认为正在守候；原服务使用 `START_STICKY`，也会绕过首发版本要求的用户主动恢复边界。

第一轮实现已经完成以下改动：

- 将持久化任务状态与进程内 `ReminderRuntime.Snapshot` 分开建模。
- 只有前台服务、定位监听、有效 Provider、首个定位和新鲜心跳同时成立时才显示“正在守候”。
- 无服务或心跳过期的活动任务转为 `interrupted`，首页和通知提供一个用户触发的恢复入口。
- 服务改为 `START_NOT_STICKY`，不注册开机 Receiver，不静默自动恢复定位。
- 第二轮生命周期复查将 Provider 计数器改为名称集合，并用运行代次隔离旧服务的延迟销毁、心跳和定位回调；恢复改为向同一服务实例发送幂等启动动作。
- 纯 Java 状态机、Provider 生命周期和既有核心测试共得到 `55 tests passed`。

当时的进度只能记为 `PARTIAL`：全量 Java 编译和纯 Java 测试已有证据，但 `C:\temp\codex-android-sdk\build-tools\36.0.0` 缺少 `aidl.exe`，Gradle Debug/Release 复验被环境阻断；同时没有真机、有效系统镜像或 AVD，无法执行进程终止、重启、权限、前台服务和通知运行时验收。

### 本次 SDK 重建、验证与开发优化

本次保持项目固定版本不变：JDK `21.0.9`、Gradle `9.4.1`、AGP `9.2.1`、compile/target SDK `36`、Build Tools `36.0.0`。损坏的 Build Tools 目录先保留为 `C:\temp\codex-android-build-tools-36.0.0-incomplete-20260901`，再通过 SDK Manager 重新安装同一版本。首次尝试把备份留在 SDK 的 `build-tools` 子目录，SDK Manager 仍把它识别为已安装包，未执行安装；将备份移出 SDK 根目录后重装完成，并确认 `aidl.exe`、`aapt.exe`、`aapt2.exe`、`d8.bat`、`zipalign.exe` 和 `apksigner.bat` 均存在。

完整验证第一次运行到 Debug Lint 后发现当前代码的真实问题：API 26–32 的内部状态动态 Receiver 使用旧重载，没有显式导出保护，`lintDebug` 报 `UnspecifiedRegisterReceiverFlag`。本次没有建立 Lint baseline，而是：

- 为内部状态广播增加按签名保护的自定义权限；发送端和接收端都要求该权限。
- API 33+ 同时使用 `RECEIVER_NOT_EXPORTED`；API 26–32 由 `signature` 权限提供发送者限制，仅对这个已解释的兼容分支做局部 Lint 抑制。
- 独立 Review 发现固定正式权限名会使不同签名的 Debug/Release 并存安装发生同名权限冲突，因此改为 `${applicationId}.permission.INTERNAL_STATUS` / `BuildConfig.APPLICATION_ID`。最终 Debug 和 Release Manifest 分别使用各自 Application ID 的权限名。

最终证据：

- 应用代码与测试 Git checkpoint：`31d03ec fix: harden first-release behavior and reminder recovery`。
- `android-app/build-apk.ps1 -SdkRoot C:\temp\codex-android-sdk`：`PASS`；55 个核心测试、Debug Lint 和 Debug APK 全部通过，`BUILD SUCCESSFUL in 3m 10s`。
- Debug APK：`outputs/arrival-alarm-debug.apk`，952,905 bytes，SHA-256 `B0306928DECBCC33506E1CBBC5FBC0160B0C15D2CC67EE0EF18BB97D58E2F00B`；Debug v2 签名验证通过。
- `android-app/build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`：`PASS`；核心测试、Release Lint、首发权限门禁、R8、资源压缩、AAB 和 Bundletool 全部通过，`BUILD SUCCESSFUL in 3m 40s`。
- 未签名工程验证 AAB：82,633 bytes，SHA-256 `B9FE50DC460EAC1594377BE513AE9E8542DD03B37194A4740810649CA951A1F4`。本次未运行正式上传签名门禁，不能把该 AAB 称为可上传产物。
- Release Lint：0 个错误、35 个非阻断警告；Java 8 目标在 JDK 21 下仍有弃用警告，本次不扩大范围升级语言级别。
- `adb devices -l` 无设备；`emulator -list-avds` 无 AVD；系统镜像目录只有失败安装标记。因此真机/模拟器运行时验证仍为 `BLOCKED`。

### 当前结论

- P0-3 代码、单测、Debug/Release 构建、Lint、R8、权限门禁和静态产物验证：`PASS`。
- P0-3 真机生命周期验收：`BLOCKED`。
- P0-3 整体状态：`PARTIAL`，不能仅凭本次自动验证宣称完整关闭。
- 本次没有引入论文参数、产品参数或其他静默假设。

## 2026-08-29 — P0-1 正式上传密钥、备份与远端签名 CI

### 已完成

- 已创建正式上传密钥，并验证独立加密备份及恢复流程。
- 已为签名工作流配置受保护的发布环境。
- 手动触发并批准远端签名 CI #1；运行 `main@4aa2af4`，结果 `SUCCESS`。

### 验证证据

- CI Run：`33254626252`，Job：`99106084242`。
- `testDebugUnitTest`、`lintRelease`、`validateReleaseBundle`、`verifyReleaseBundleSignature`、`verifyPlayBundle`：PASS。
- `jarsigner` 输出 `jar verified.`，Gradle 输出 `BUILD SUCCESSFUL in 46s`。
- Artifact：`android-play-bundle-signed`，ID `9715448302`，大小 190,688 bytes。
- Artifact ZIP SHA-256：`75ddca52a66e423259c1c7bbab69c70af8dffddac77e14a058ead0e7e01064e5`。
- 临时下载 CI 产物后，AAB 证书指纹与正式上传证书完全一致；验证后的临时文件已删除。
- 没有上传 Google Play，也没有发布应用。

### 当前状态与下一步

- P0-1 技术状态：`READY FOR INTERNAL TEST`。
- 下一步是 Google Play Console 建立应用、启用 Play App Signing，并进入内部测试轨道；不得直接进入生产发布。
- P0-2 的正式隐私主体、Data Safety 核验、抓包和真机验证仍未完成，整体发布状态仍为 `NOT READY`。

## 2026-08-27 — P0-2 首发最小权限整改（代码完成，外部验收待办）

完整的可重复执行流程、证据标准、完成判据和本次操作经验已整理到 [`play/p0-2-workflow.md`](play/p0-2-workflow.md)。后续继续 P0-2 时以该文档的“当前恢复点”为入口，不依赖聊天记录恢复上下文。

### 任务目标

首个 Google Play 发布版本只保留用户主动设置目的地、主动启动 location 前台服务并在到站时响铃/振动/通知。长期车站识别、其他 App 出行信号识别和固定日程自动启动暂不进入发布版。

### 已解决的问题

- Release Manifest 已移除：
  - `ACCESS_BACKGROUND_LOCATION`
  - `PACKAGE_USAGE_STATS`
  - `USE_FULL_SCREEN_INTENT`
  - `SCHEDULE_EXACT_ALARM`
  - `RECEIVE_BOOT_COMPLETED`
- Release Manifest 已取消注册：
  - `TransitNotificationListener`
  - `FixedAlarmReceiver`
  - `BootReceiver`
- 首页和设置页已移除自动出行、车站识别、其他 App 信号识别和固定日程入口。
- `TrackingService` 启动时会关闭旧版本遗留的出行检测状态；任务结束或进程恢复后不会回到自动检测模式。
- 升级用户已有固定日程数据暂时保留，但应用启动时会取消旧系统调度，避免静默后台启动。
- 到站提醒已从全屏通知改为响铃、振动和高优先级可点击通知。
- 系统位置/通知权限弹窗前增加应用内说明，明确定位只在用户主动开始后持续使用，任务停止或到站触发后停止位置更新。
- 应用内隐私说明增加公开 GitHub 隐私政策入口。
- Gradle 增加 `verifyReleasePermissionScope` 门禁，禁止被延期的权限和组件重新进入首发 Manifest。
- 已记录 ADR-0004，并同步更新产品、架构、README 和隐私政策。
- 已建立 Google Play Data Safety 与 Location FGS 申报草稿。

### 已有验证证据

最终代码已完成以下检查：

- `testDebugUnitTest`：PASS
- `verifyReleasePermissionScope`：PASS
- `lintRelease`：PASS，0 个阻断错误
- R8 / 资源压缩 / `bundleRelease`：PASS
- Bundletool `validateReleaseBundle`：PASS
- 已生成有效的未签名工程验证 AAB：`C:\temp\arrival-alarm-gradle-build\outputs\bundle\release\arrival-alarm-android-release.aab`，大小 72,945 bytes
- 合并后的 Release Manifest 只包含：精确/大致位置、location FGS、通知、振动和网络权限；只注册两个 Activity、`TrackingService` 和 `ActionReceiver`
- 对最终 AAB 的 R8 DEX 进行字符串核验，未发现被延期的权限、Receiver/Listener 类名或自动出行/固定日程 UI 文案

最终验证命令：`android-app/build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`。结果：`BUILD SUCCESSFUL`，用时 1 分 8 秒；71 个任务中 33 个执行、33 个命中缓存、5 个已是最新状态。Release Lint 仍有 34 条非阻断结果；本次权限裁剪后没有 `MissingPermission`、受保护权限或精确闹钟相关结果。

### Review 结论

- 没有发现仍可从首发 UI、Manifest 组件或服务恢复路径进入长期出行检测与固定日程自动启动的调用链。
- 升级时会取消旧日程调度但保留用户原始日程数据，避免静默删除；旧出行检测运行状态会被清除。
- 到站通知不再设置 full-screen intent，用户点按通知后仍可进入到站页面。
- 最终合并 Manifest 与产品、架构、隐私和申报草稿一致。
- 未发现本任务引入的 logic bug、特殊权限回流或新的 Lint 阻断错误。

### 当前面临的问题

1. 在线隐私政策已填写开发者/发布者主体“曾文骏”和隐私联系邮箱 `yueqiu@hrbeu.edu.cn`；仍需确认该主体名称与 Google Play 开发者名称完全一致。
2. GitHub 隐私政策链接只有在本次修改 commit 并推送后才会展示最新首发范围内容。
3. Data Safety 中搜索关键词的当前 Console 数据分类仍需人工核对，不能凭经验静默填写。
4. Photon、高德和系统 `Geocoder` 的 Release 实际请求字段仍需抓包确认。
5. 尚未进行 Android 真机验证，尤其是：
   - 权限同意/拒绝；
   - 主动启动后切后台或锁屏持续定位；
   - 到站响铃、振动和通知；
   - 通知点按进入页面；
   - 停止任务后定位和前台服务结束；
   - 从包含旧自动化设置的版本升级。
6. 工作区中存在用户此前未提交的地点搜索交互优化：`MainActivity.java`、`CoreLogicTest.java`、`InteractionGate.java` 和 `android-app/README.md`。P0-2 修改保留了这些改动；提交前必须仔细分离或由项目负责人确认共同 checkpoint。
7. `AGENTS.md` 当前是未跟踪的用户文件，不属于本次 P0-2 修改，不能擅自提交或删除。

### 当前思路

- 独立 Review 重点检查：旧版本状态迁移、是否仍有可达的后台自动启动路径、到站通知降级、权限申请顺序和文档一致性。
- 若自动验证通过，将 P0-2 标记为 `PARTIAL` 而不是 `PASS`：代码与构建范围可以关闭，但正式开发者主体、Data Safety 人工确认、抓包和真机验证仍未完成。
- 不在本任务中删除全部实验源码或重构存储模型；首发通过 Manifest、UI、服务恢复保护和 R8 隔离这些能力。后续恢复功能时必须独立 ADR 和测试。

### 下一步

1. 确认“曾文骏”与 Google Play 开发者/发布者名称完全一致。
2. 推送公开隐私政策后验证无需登录访问。
3. 完成地点服务抓包、Data Safety 人工核对和真机验收。
4. 项目负责人检查 diff 并决定如何与现有搜索交互优化分离 Git checkpoint；未经授权不提交。
