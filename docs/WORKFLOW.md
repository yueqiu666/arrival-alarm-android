# Engineering Workflow

## 共同门禁

每个任务只解决一个明确问题。开始前先读取相关产品、架构、决策和历史经验，追踪当前实现调用链，并写出可验证的完成条件。涉及安全、权限、位置、用户数据、后台服务、网络、并发或 Release 配置时，实施前先向项目负责人说明方案、影响和回滚边界。

## Feature

Problem
↓
Requirements
↓
Acceptance Criteria
↓
Codex Plan
↓
Human Review
↓
Implementation
↓
Automated Tests
↓
Manual Test
↓
Diff Review
↓
Commit / PR
↓
Feedback

执行要求：

1. 在 `docs/PRODUCT.md` 的现有边界内定义问题、非目标和验收标准。
2. 对照 `docs/ARCHITECTURE.md` 确认组件、接口、权限和数据边界。
3. 需要产品取舍、改变现有行为或扩大范围时，在实现前等待 Human Review。
4. 实现最小完整切片；核心逻辑补充防回归测试。
5. 完成自动检查、真机路径和 diff review 后，提出聚焦的 commit message；未经明确授权不代替项目负责人发布。

## Bug

Reproduce
↓
Find root cause
↓
Create regression test
↓
Minimal fix
↓
Run tests
↓
Manual verification
↓
Commit

执行要求：

1. 用当前代码、日志或失败测试建立可重复证据。
2. 追踪完整调用链并确认根因；未知部分标为“需要进一步验证”。
3. 让回归测试在修复前能够证明问题，或说明无法自动化的原因。
4. 只修改根因及同一调用链上不可分割的边界情况。
5. 验证正常路径、失败路径和最可能的副作用，再提出 commit message。

## Release

Finish planned issues
↓
Release Readiness Audit
↓
Fix P0
↓
Fix P1
↓
Regression Test
↓
Release Build
↓
Real-user Test
↓
Release
↓
Monitor
↓
Retrospective

Release Readiness Audit 和 P0/P1 的分级、门禁与报告格式以根目录 `AGENTS.md` 为准。发布结论必须由当前 build、签名、版本、权限、隐私、测试和真机证据支持。

## 当前验证入口

当前受支持的 Debug 本地检查是：

```powershell
.\android-app\build-apk.ps1 -SdkRoot "C:\path\to\android-sdk"
```

该脚本调用固定版本的 Gradle Wrapper，运行核心测试、Debug Lint 和 Debug APK 构建，再复制兼容文件名到 `outputs/arrival-alarm-debug.apk`。

### Android 到站提醒门禁

启动一台已解锁、打开定位的 API 31–36 测试模拟器。用 `adb devices` 查到模拟器序列号后，在仓库根目录用 PowerShell 7 执行：

```powershell
.\android-app\test-api35-reminder-gate.ps1 -SdkRoot "C:\path\to\android-sdk" -Serial emulator-5554
```

门禁运行单测和 Debug Lint，构建并安装隔离包 `com.navobell.arrivalalarm.debug.p08gate` 与设备测试 APK。脚本只重置隔离包的数据和权限，使用模拟器位置注入，适合在专用测试 AVD 上逐次运行。未跑全、超时、前台服务计数不符或检测到崩溃均返回失败。终端的 `Evidence` 目录包含 APK 哈希、instrumentation 输出和 Android 服务事件。

API 33–36 额外核对系统记录的前台服务启动和退出次数。Android 12 镜像缺少这组事件，门禁仍在设备上检查正常启动后的前台服务、每次停止后的服务与任务是否清除，并通过系统日志检查应用崩溃和 ANR。

记录发布候选时，应附上同一源码版本的门禁日志。签名包和真机的行为另行验收。

有完整本地 Gradle 缓存时可加 `-Offline`。若 Wrapper 下载受阻，可用 `-GradleExecutable` 指向已经过校验的同版本 Gradle 9.4.1。

在同一命令加 `-Recovery` 可运行中断恢复门禁。它会先用 `SIGKILL` 终止隔离包的进程，再另做一次真实的模拟器重启。请使用没有锁屏密码的专用 AVD，避免中断其他测试。门禁检查原任务仍在、重新打开后显示「需要恢复」，点击后才有前台服务和定位更新。终端输出的证据目录分别记录两条路径的进程号、开机标识、instrumentation 和 Android 服务事件。

加 `-Permissions` 可运行权限拒绝门禁。API 33–36 的定位和通知各覆盖已记住拒绝与首次系统弹窗两种情况，Android 12 检查首次定位弹窗。测试实际点击首次弹窗里的拒绝通知，或选择模糊位置并授予使用期间权限。门禁检查对应提示、任务和服务均未启动、首页不显示守候卡片。

加 `-Revocation` 可运行守候中撤销精确定位与通知的用例。脚本等定位服务开始工作再用系统包管理命令撤销权限，检查重新打开前进程和服务均已退出。重开后必须显示原任务「需要恢复」，并在权限仍缺失时提示原因、阻止恢复。API 33–36 点「继续」后验证系统拒绝回调。Android 12 没有脚本用来预设拒绝状态的包管理命令，测试查看权限说明后点「暂不」并检查原任务仍待恢复。

加 `-All` 可在同一个专用 AVD 上依次跑启停、权限拒绝、权限撤销和中断恢复。某个用例失败时会重置隔离包并继续后续用例，最终仍返回失败并列出失败项。每次仅传一种模式。真机复验时可在系统设置手动改权限，返回 App 查看状态，重新授权后用「恢复」继续同一条提醒。

### Release 构建与静态验证

```powershell
.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk"
```

脚本会在含中文用户名的 Windows 机器上自动使用项目专用的 ASCII Gradle 缓存目录。未配置上传密钥时生成的是用于工程验证的未签名 AAB。只有安全配置四个 `ARRIVAL_UPLOAD_*` 环境变量并通过 `.\android-app\build-release.ps1 -VerifyPlayBundle`，才可把产物称为“签名验证通过”；即使如此，也仍需真机和 Play 内部测试验收。

## Definition of Done

Implementation complete
→ Relevant tests pass
→ Build/check passes
→ Diff reviewed
→ Manual verification instructions provided

如果环境缺失导致任何检查不能运行，任务报告必须明确写出未验证项、原因和剩余风险，不能把“代码已写”当成完成。

## 完成报告

每个任务至少报告：

- 修改文件及原因。
- 根因或需求如何被满足。
- 自动测试、build、lint 和手动验证的实际结果。
- 最值得项目负责人检查的 diff。
- 副作用、剩余风险和未验证项。
- 项目负责人可执行的手动验收步骤。
- 一个聚焦、可独立回滚的推荐 commit message。
