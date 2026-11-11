# Experiment Log

本文件记录可追溯的构建、自动测试、设备测试和发布验证。没有设备型号、Android 版本、Commit、产物摘要或日志的口头结论，不作为 PASS 证据。

## 2026-09-23 — P0-5 自动验证复验

- Run ID: `p0-5-debug-20260923`
- Git 状态: 工作区存在未提交改动；P0-5 尚未形成独立 commit
- 命令: `powershell -ExecutionPolicy Bypass -File .\\build-apk.ps1 -SdkRoot C:\\temp\\codex-android-sdk`
- 环境: JDK 21、Gradle Wrapper、Android SDK Platform/Build Tools 36.0.0
- 结果: `PASS`
- 证据:
  - `testDebugUnitTest` PASS
  - `GradleCoreLogicTest > existingCoreSuitePasses` PASS
  - Debug Java 编译 PASS
  - `lintDebug` PASS
  - Debug APK 生成成功
- 覆盖范围: `AsyncResultGate` 当前请求、请求替换、取消失效；现有提醒状态机、Provider 生命周期和地点解析回归
- 不能证明: 真实 Activity 旋转/销毁、进程回收、Service 命令竞态、Android 版本限制和厂商后台策略

## 2026-09-23 — AVD/instrumentation 环境检查

- Run ID: `p0-5-avd-preflight-20260923`
- 结果: `BLOCKED`
- 检查:
  - Android Emulator 可执行文件存在：`C:\\temp\\codex-android-sdk\\emulator\\emulator.exe`
  - `emulator -list-avds`：没有 AVD
  - `adb devices -l`：没有连接设备
  - `sdkmanager.bat`：当前 SDK 目录和常见 Android SDK 安装目录均未找到
  - `avdmanager.bat`：未找到
  - `system-images\\android-35\\google_apis\\x86_64`：只有 `.installer` 失败标记，没有可启动系统镜像
- 结论: 本次没有伪造或标记任何 instrumentation 通过结果；需要先恢复命令行工具并成功安装系统镜像。
- 推荐恢复步骤:
  1. 安装与当前 SDK 匹配的 Android Command-line Tools；
  2. 接受 SDK licenses；
  3. 安装一个 API 26 镜像和一个当前 Android 镜像；
  4. 创建 AVD 并确认 `emulator -list-avds` 返回名称；
  5. 启动 AVD，确认 `adb devices` 为 `device`；
  6. 再加入并运行 `src/androidTest` instrumentation 测试。

## 2026-10-05 — SDK 镜像安装与 AVD 启动复验

- Run ID: `p0-5-avd-install-20261005`
- 结果: `PARTIAL / BLOCKED`
- 已完成:
  - 从 Android 官方仓库下载 Command-line Tools 压缩包；
  - 接受 SDK licenses；
  - API 35 `google_apis;x86_64` 系统镜像下载完成；
  - API 26 `google_apis;x86` 系统镜像下载完成；
  - `arrival-api35` AVD 配置曾成功创建。
- 失败/阻塞:
  - API 26 首次下载出现 `unknown archive`，使用兼容下载模式后镜像文件落盘，但 SDK Manager 状态未稳定记录；
  - AVD 启动后 `adb devices -l` 始终显示 `emulator-5554 offline`；
  - Emulator 日志显示 WHPX 可用，但没有进入可执行的 `device` 状态；
  - 当前 `cmdline-tools\\latest\\bin` 文件不再完整，`sdkmanager.bat`/`avdmanager.bat` 当前不可用，需要重新恢复工具目录后再继续。
- 结论: 系统镜像安装取得进展，但没有运行 instrumentation，也没有把 AVD 运行时结果标为 PASS。

## 2026-10-05 — API 35 AVD 可用性与 APK 冒烟

- Run ID: `p0-5-avd-smoke-20261005`
- AVD: `arrival-api35-clean`
- 镜像: API 35 `google_apis;x86_64`
- 工具: Emulator 37.2.12、Platform Tools 37.0.1、WHPX
- 结果: `PASS`（虚拟设备和启动冒烟范围）
- 证据:
  - AVD 从 `offline` 进入 `device`；
  - 当前 Debug APK 安装返回 `Success`；
  - `com.example.arrivalalarm.MainActivity` 启动成功并处于 `ResumedActivity`；
  - `am force-stop com.navobell.arrivalalarm.debug` 后进程消失；
  - 再次启动后进程恢复，主 Activity 再次处于 resumed 状态。
- 不能证明: Activity 旋转时的迟到回调、Service 异步查询竞态、FGS 真实限制和完整 instrumentation 测试。

## 2026-09-21 — P0-3/P0-4/P0-5 Release 静态验证

- Run ID: `lifecycle-release-20260921`
- 来源: `docs/DEVELOPMENT_LOG.md` 与 `docs/RELEASE_READINESS.md`
- 结果: `PASS`（自动验证范围）
- 证据: Debug/Release 测试、Lint、R8、权限门禁、APK/AAB 和 Bundletool 验证通过；核心测试记录为 `84 tests passed`，另有 `LocationRecoveryPolicyTest: PASS`
- 不能证明: 真实设备生命周期和 FGS 行为

## 历史设备工作记录状态

项目负责人说明此前已经进行过 Android 真机相关工作，但当前仓库没有对应的设备审计记录、日志、录屏、安装包 SHA-256 或绑定 Commit。现有历史文档仍记录：

- `adb devices -l` 无设备；
- `emulator -list-avds` 无 AVD；
- API 35 系统镜像安装失败；
- P0-3/P0-4/P0-5 的真实生命周期验收尚未形成可核验记录。

因此这些历史工作暂记为 `UNKNOWN（待补证据）`，不改写为 PASS。补录至少需要：

1. 设备品牌/型号、Android 版本、系统版本和 API level；
2. 安装包来源、`versionCode`、Commit 和 SHA-256；
3. 实际操作步骤和预期结果；
4. logcat、屏幕录制、截图或测试报告；
5. 失败路径、重试次数和最终结论。

## P0-5 虚拟/自动验证缺口

## 2026-10-05 — instrumentation 重试与 AVD 冲突排查

- Run ID: `p0-5-instrumentation-retry-20261005`
- 首次 instrumentation 结果: `BLOCKED`
  - `connectedDebugAndroidTest` 未进入测试代码；AVD 中已有同包名但不同签名的旧 APK，安装失败为 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
  - 卸载 `com.navobell.arrivalalarm.debug` 后重试，测试工具进入 `connectedDebugAndroidTest`，但模拟器控制台报告 `Emulator console auth failed`，随后没有产生可用测试结果，任务被停止。
- 清理与冲突排查:
  - 已删除本任务专用的旧 `arrival-api35-clean.avd`、Gradle 缓存和 `android-app/build`；项目源码未删除。
  - 旧 `C:\Users\CodexSandboxOffline\\.android\\avd\\arrival-api35.avd` 受独立 ACL 拒绝访问，未强行接管；它不再作为当前测试目标。
  - 新建 `arrival-api35-validation2` 于 `C:\temp\codex-android-avd-validation2`，使用同一已存在的 API 35 镜像，避免名称和路径冲突。
  - 新 AVD 没有复现旧 `multiinstance.lock` 删除错误，但 QEMU 启动后没有注册为 `adb device`，90 秒内 `adb devices` 仍为空；`-gpu swiftshader_indirect` 与 `-gpu off -accel off` 均未完成启动。
  - 日志仅到 `Found systemPath ... android-35\\google_apis\\x86_64`，没有 Android 内核/boot 阶段输出；WHPX 检查仍为可用。当前更准确的状态是模拟器启动链 `BLOCKED`，根因尚未确定。
- 结论: `PARTIAL / BLOCKED`。instrumentation runner 已编译，但没有形成一次可核验的运行时 PASS；P0-5 仍不能关闭。

## 2026-10-05 — 清理后 ASCII 输出目录重建

- Run ID: `p0-5-rebuild-ascii-20261005`
- 命令: `ARRIVAL_BUILD_DIR=C:\\temp\\arrival-build-p05-rebuild .\\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`
- 结果: `PASS`
- 证据:
  - `GradleCoreLogicTest > existingCoreSuitePasses` PASS；
  - `compileDebugJavaWithJavac` PASS；
  - `assembleDebug` PASS；
  - Debug APK 已生成于 `C:\\temp\\arrival-build-p05-rebuild\\outputs\\apk\\debug`。
- 根因结论: 中文工作区路径会在本机当前 ACL/工具链组合下触发 `R.jar` 的 `AccessDeniedException`；项目已有 `ARRIVAL_BUILD_DIR` 机制，切换到 ASCII 输出目录后构建恢复。该问题与 AVD 启动阻塞独立。

## 2026-10-05 — 重启后 ADB 主机工具复验

- Run ID: `p0-5-adb-host-tool-20261005`
- 结果: `BLOCKED`
- 证据:
  - 重启后无残留 Emulator/QEMU/ADB 进程；
  - `adb version` 为 Platform Tools `37.0.1-15733141`；
  - `adb nodaemon server` 直接失败：`fdevent_poll.cpp:62 failed to create fdevent interrupt socketpair: Invalid argument`；
  - 因 ADB 服务未启动，`adb devices` 无法成为有效的 instrumentation 前置条件。
- 结论: 当前阻塞点已从 AVD 锁/签名冲突收敛为 Platform Tools 37.0.1 与本机 Windows 10.0.26200 的 ADB 服务启动兼容问题。需要替换为另一版本的完整 Platform Tools 后再继续；无需重新下载系统镜像。

## 2026-10-06 — 管理员 ADB 与 API 35 AVD 复验

- Run ID: `p0-5-admin-adb-avd-20261006`
- 结果: `PARTIAL / BLOCKED`
- 已完成:
  - 下载并校验官方 Platform Tools 35.0.2：SHA-256 `2975a3eac0b19182748d64195375ad056986561d994fffbdc64332a516300bb9`；
  - 普通权限下 35.0.2 与 37.0.1 均无法创建 ADB socket；管理员权限下 35.0.2 ADB 服务启动成功；
  - `arrival-api35-validation2` 从 `offline` 进入 `device`，`sys.boot_completed=1`，API level 35；
  - Debug APK 和 androidTest APK 均安装成功；直接 `am start -W` 启动 `MainActivity` 返回 `Status: ok`。
- 仍阻塞:
  - `am instrument -w` 调用自定义 Runner 不返回，设备上没有持续的 test 进程；即使 Runner 只调用 `finish(OK)` 也无法形成结果；
  - 因此尚未形成可核验的 instrumentation PASS。当前剩余问题是模拟器 instrumentation 服务/调用链，不能据此判定 P0-5 代码失败。

## 2026-10-06 — Android Studio 与最终 API 35 验证

- Run ID: `p0-5-android-studio-avd-final-20261006`
- 结果: `PARTIAL / BLOCKED`
- 证据:
  - Android Studio 已安装于 `C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe`；未再次下载或重复安装；
  - Platform Tools 35.0.2 可连接 `emulator-5554`，`sys.boot_completed=1`，API level 35；
  - Debug APK 与 androidTest APK 均存在并已安装；
  - `am start -W -n com.navobell.arrivalalarm.debug/com.example.arrivalalarm.MainActivity` 返回 `Status: ok`，`topResumedActivity` 为 `MainActivity`；
  - 带 20 秒边界的 `am instrument -w` 仍未产生测试结果，命令异常退出，未形成 instrumentation PASS；ADB/AVD 随后仍保持 `device`。
- 结论: Android Studio/AVD/应用启动链路 PASS；P0-5 生命周期 instrumentation 仍 BLOCKED，剩余问题集中在 instrumentation 服务调用链。

## 2026-10-06 — instrumentation 启动链定位

- Run ID: `p0-5-instrumentation-root-cause-20261006`
- 结果: `PARTIAL / BLOCKED`
- 调查证据:
  - `pm list instrumentation` 正确注册 `LifecycleInstrumentationRunner`，target 为 `com.navobell.arrivalalarm.debug`；
  - 运行时最初出现目标进程 crash；runner 改为按组件名字符串启动后，目标进程不再立即崩溃，但 instrumentation 保持 `mFinished=false` 且未启动 MainActivity；
  - 诊断日志未出现 runner `onStart` 入口日志，说明故障发生在 runner 回调前的 instrumentation 启动链；
  - APK 可独立启动 MainActivity，故不能把该失败归因于普通 Activity 启动或 APK 安装失败。
- 本轮代码变更: runner 不再直接引用目标 APK 的 `MainActivity.class`，改用 `ComponentName` 字符串；临时诊断日志已移除。
- 结论: P0-5 业务代码没有新增运行时失败证据；Android instrumentation 启动链仍 BLOCKED，需要 Android Studio/Gradle managed device 或更标准的 AndroidX runner 继续验证。

## 2026-10-06 — 状态更正、统一验收与 Git 备份

- 用户决定：先处理已确认问题的代码修复，保留相关单测、build、lint 门禁；之后统一执行模拟器和真机回归。暂停当前 instrumentation 排障。
- 当前工具链：官方 Android Studio 已存在于 `C:\Program Files\Android\Android Studio\bin\studio64.exe`；IDE 可持续启动及 Device Manager 接管尚未验证。API 35 AVD、管理员 ADB 35.0.2、APK 安装和普通 MainActivity 启动已有 PASS 证据。不再重复下载或安装。
- 根因结论更正：先前把故障确定为 MainActivity.class 类解析、API 35/target 36 不兼容或系统服务问题，均缺少异常堆栈及对照实验。当前只能确认自定义 runner 没有完成测试，真正根因为 UNKNOWN。runner 未覆盖 onCreate/start 初始化契约也需后续核验。缺少 onStart 日志不能单独证明系统不兼容；强制停止或重跑造成的进程终止也不能认定业务崩溃。
- 当前 runner 只请求启动 Activity 并等待 500ms，没有旋转、销毁、迟到回调或 Service 断言；即使返回成功也不足以关闭 P0-5。后续须建立有断言的生命周期测试或人工验收证据。
- 历史真机工作缺少设备/版本/commit/日志关联，保持 UNKNOWN，不删除历史记录，不作为当前 PASS 证据。
- P0-1～P0-5 总表和未完成项见 RELEASE_READINESS.md 的 2026-10-06 检查点；各项保留 PARTIAL，整体 NOT READY。
- 备份：项目负责人明确授权将当前工作区保存为 Git checkpoint 并推送 GitHub 独立备份分支。该快照包含已有未提交改动，不作为逐项修复完成、发布或完整 review 的证明。不纳入安装器、SDK、AVD、产物、密钥和本地配置。
- 备份分支：`codex/checkpoint-20261006-deferred-device-validation`；提交说明 `chore: checkpoint hardening work pending device validation`。可通过该分支的 Git 历史找到本次源码快照。
- 项目负责人随后明确确认上传目的地 `https://github.com/yueqiu666/arrival-alarm-android`，并要求作为第 1 部分修改分支整合保存。上传分支采用 `codex/part-1-modifications`，包含源码快照 `25cd5eb` 与本条记录；原本地 checkpoint 分支保留。设备验收及发布状态不因推送而改变。
- 本次备份检查：离线 `testDebugUnitTest` 实际执行 PASS（GradleCoreLogicTest > existingCoreSuitePasses PASSED，BUILD SUCCESSFUL）；`git diff --check` PASS；新增内容及未跟踪源码/文档常见敏感凭据模式扫描无命中。未重跑完整 Release/Lint/设备验收；历史证据继续单独保留。
- 后续恢复：先完成已确认修复，再使用已有 SDK/AVD 开展统一验收；测试入口失败时记录明确日志并停止无效重试。补充 Android 最低/当前版本覆盖、权限与后台/锁屏、进程恢复、异步回调及各修复交互；厂商省电场景留待真机。

当前自动验证仍缺少以下可重复测试：

| 场景 | 当前状态 | 需要的证据 |
|---|---|---|
| Activity 旋转后旧查询结果 | UNKNOWN | Android instrumentation 或真机日志 |
| Activity 销毁后迟到回调不显示 Dialog | UNKNOWN | instrumentation/真机复现 |
| 同一 Activity 新请求覆盖旧请求 | PASS（纯 Java token 测试；UI 回调仍未运行时验证） | UI/设备测试补强 |
| Service 停止后车站查询迟到结果 | UNKNOWN | Service instrumentation 或真机日志 |
| Service 恢复与旧 Service `onDestroy()` 交错 | UNKNOWN | Android 服务生命周期测试 |
| 进程终止后持久化任务显示“需要恢复” | UNKNOWN | 真机/模拟器强制终止测试 |
| Android 12–16 FGS 限制与启动失败回滚 | UNKNOWN | 对应 API 设备测试 |
| 权限撤销、Provider 关闭和恢复通知 | UNKNOWN | 真机/模拟器运行时测试 |
