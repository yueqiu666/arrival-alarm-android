# Release Readiness Report

- 更新时间：2026-11-10（0.4.3 小范围真机试用）
- 当前范围：`P0-1 Release 凭据与内部测试`、`P0-2 敏感权限与上架材料`、`P0-3 监控中断与手动恢复`、`P0-4 服务启动与假激活`、`P0-5 异步结果与生命周期绑定`、`P0-7 地点搜索可靠性`、`P0-8 核心场景发布测试门禁`
- 当前结论：`NOT READY`

`NOT READY` 表示应用尚未完成 Play 内部测试、真机验收和 P0-2 外部材料核验，不表示 P0-1 Release 工程仍然缺失。正式上传密钥和远端签名 CI 已有完成证据，但当前产物仍不得直接进入生产发布。

## 0.4.3 小范围真机试用

- 本轮试用 APK 为 `arrival-alarm-0.4.3-debug-amap.apk`。当前 `main` 包含对应的 Android 0.4.3 源码。
- APK 包名 `com.navobell.arrivalalarm.debug`，`versionCode 8`，最低 Android 8.0。SHA-256 为 `F0086B70C910D685B9D41B579ADA7DB5EA22E4C78407629120C5E7CFA2FF8140`，APK v2 签名验证通过。同一 APK 在 API 35 模拟器通过高德联想和 POI 搜索检查。
- 本轮收集地点搜索和确认、锁屏或切后台后守候、到站通知及声振、手动停止与中断恢复的真机反馈。优先记录手机型号、Android 版本、搜索来源、系统省电状态和实际表现。
- 正式签名包仍待验证安装与升级、压缩后行为、绑定分发证书的高德 Key，以及发行渠道的安装与权限表现。
- `Android checks` 对 PR 和直接推送到公开库 `main` 的提交运行。

## 2026-09-07 产品目标与当前任务范围

## 2026-11-10 首发 P1 批量交付

本轮按项目负责人要求一次性交付首发路径的剩余 P1。冻结的普通 Debug APK 和未签名 AAB 位于本地 `outputs/`，构建与隔离设备证据位于本地 `.tools/arrival-p1-complete/`。包生成后出现的独立首页视觉改动已保留并另行通过构建检查。

| 范围 | 本轮结果 |
| --- | --- |
| 首页状态、权限与搜索 | 保存草稿、确认框及待授权上下文，搜索结果保持代次校验。系统设置回流反馈和搜索失败重试已接入。 |
| 存储与定位 | v0 兼容、v1 写入、坏记录隔离、并发写入检查通过。实时距离只留在内存，低精度、过旧和乱序定位被拒绝。 |
| 布局与声振 | Android 16 深色、大字体横屏及完整响铃窗口通过。提前确认、过期动作隔离、服务退出、通知保留与确认清理均有设备证据。 |
| 首发守候回归 | Android 16 首次通知拒绝、仅模糊定位拒绝、正常启停、12 次立即停止和 12 次立即清空数据通过。测试进程被终止后没有自行重启，重新打开并手动恢复了同一条 v1 任务。单测、双变体 Lint、APK、AAB 和首发权限门禁通过。Android 14 测试 AVD 冷启动超时，本轮整机重启恢复未复验。 |
| 原型功能 | 自动车站提示、旧出行检测和固定日程继续延期，升级保留旧日程记录。 |

发布结论仍为 `NOT READY`。目标国产机的锁屏、TalkBack、系统权限设置回流、渠道静音和省电场景仍需人工验收。未签名 AAB 用于工程验证。

## 2026-10-06 当前检查点与统一验收安排

项目负责人决定：先推进已确认问题的代码修复及必要的单元测试、build、lint，再集中执行模拟器和真机验收。当前暂停 instrumentation 环境排障，不再重复安装工具。必须依赖设备复现才能确定根因的问题保留为 `BLOCKED`，不得猜测修复或提前关闭。

2026-10-06 再次确认：P0-1～P0-6 的真机、虚拟机/模拟器及 instrumentation 验收全部留到后续统一进行；当前不单独启动设备验收或设备环境排障。源码 Review、单元测试、build 和 lint 仍可按任务执行。未执行的设备检查保持待验收，不能据此关闭 P0 或宣称可发布。

| 问题 | 已有证据 | 未完成 / 后续验收 | 当前状态 |
|---|---|---|---|
| P0-1 Release 凭据与内部测试 | 正式密钥备份、恢复演练、受保护签名 CI 与构建检查有历史 PASS 证据 | Play 内部测试及设备冒烟；历史 CI artifact 不保证仍在保留期 | PARTIAL |
| P0-2 权限与上架材料 | 权限裁剪、Manifest 门禁、披露草稿及历史构建检查 | Console 主体/分类核对、公开材料确认、网络抓包、设备权限验收 | PARTIAL |
| P0-3 中断与手动恢复 | P0-8 矩阵验证进程中断与重启。首页提示需要恢复，手动恢复同一任务并重新收到定位 | 真机的进程回收、强制停止、重启、权限撤销、恢复通知和厂商省电 | PARTIAL |
| P0-4 服务启动与假激活 | 启动状态与失败回滚已实现。API 35 同一 AVD 对照复现旧版崩溃，P0-8 矩阵复测修复后的正常启停与启动取消 | 真机、后台或锁屏启动限制、启动失败与恢复交错 | PARTIAL |
| P0-5 异步结果与生命周期 | 请求 token/组件代次检查、纯 Java 测试、APK 构建安装及 API 35 普通 Activity 启动 | 旋转/销毁后迟到回调、旧请求覆盖、Service 竞态；当前自定义 instrumentation 未完成 | PARTIAL；自动设备测试 BLOCKED |
| P0-6 锁屏目的地隐私 | 默认隐藏、PRIVATE 与脱敏公开版本、主动显示选项、已有通知刷新、详情页不覆盖锁屏、隐私披露；2026-10-06 单测、Debug APK、设备测试 APK、Lint PASS | 最低/当前 Android 与国产机锁屏、升级默认值、开启/关闭显示及声音振动回归；新增设备隐私检查尚未执行，设备验收继续暂缓 | PARTIAL；代码完成，待设备验收 |
| P0-7 地点搜索可靠性 | 项目负责人在真机验证单候选、多候选和联想的确认与取消，并反馈高德 Debug 对照版明显更好用。单测、Lint、Debug APK、未签名 Release AAB 和 Bundletool 校验通过 | 正式服务选型和接入、授权及配额、正式签名 Key；代表性地点覆盖和坐标、弱网、限流、分享入口和 Release 真机验收 | PARTIAL（高德 Debug 真机体验已对照，待生产接入与验收） |
| P0-8 核心场景发布测试门禁 | Android 12、13、14、15、16 模拟器已验证启停、进程中断、重启后手动恢复和各版本适用的权限用例 | 真机和厂商系统、后台启动限制、Doze/省电、定位和网络异常、通知动作与到站页、异步回调、可访问性、签名包内测及 Pre-launch Report | PARTIAL |

### 2026-11-10 — P0-7 高德搜索接入 Release

- Debug 与 Release 共用高德 Android 搜索适配器。API 35 模拟器使用 Debug Key 实查「成都市+天府广场」，联想与 POI 都返回了坐标在成都的高德候选。
- 签名构建需要在 `play-release` 环境中配置绑定正式包名与实际分发证书的高德 Android Key。正式包的联想、POI 与数据流验收仍待完成。P0-7 保持 `PARTIAL`。

### 2026-11-09 — P0-7 地点确认

- 单候选、多候选点选和输入联想共用同一确认框，展示名称、城市/区域、地址和坐标。选择多候选中的一项时保留原列表，取消确认后可直接改选。主动搜索和联想取消时都保留搜索词及候选。
- 本地交互测试桩复现了旧版的多候选提前启动和取消丢失候选。修改后三种入口均通过。设备上仍需核对弹窗返回、地址显示和启动提醒。
- 项目负责人随后用 Debug APK 在真机确认单结果、多结果和联想的确认及取消流程正常。
- Gradle 的 `testDebugUnitTest`、`lintDebug`、`lintRelease`、`assembleDebug`、`bundleRelease` 和 `verifyReleasePermissionScope` 通过。离线缓存缺少 Bundletool 的传递依赖，`validateReleaseBundle` 未完成。

### 2026-11-09 — P0-7 高德搜索 Debug 对照

- Debug 对照接入独立搜索 SDK，用户同意数据说明后可切换高德联想和 POI 搜索。高德模式单独显示结果或失败，复用地点确认界面。
- 核心单测、新增三项适配器测试、Debug/Release Lint、Debug APK 及 Release AAB 校验通过。配置本机测试 Key 后已重打 Debug APK，并核对包名、签名和 Key 注入。同期构建的 Release AAB 未包含测试 Key 或高德 Android 搜索 SDK。
- 项目负责人在真机对照后反馈高德明显更好用。本条记录为定性体验反馈，代表性地点覆盖、坐标落点和异常网络仍待验收。

### 2026-10-12 — P0-7 首轮代码修复

- 搜索和固定目的地的单结果不再自动启动，先显示名称、城市/区域、地址、WGS-84 坐标和“确认使用”。联想卡片在地址缺失时明确提示核对坐标。
- 搜索管线保留服务失败状态；只有成功或明确无匹配的结果可进入缓存。超时、限流、配置错误、无效响应和网络失败会显示重试提示。
- `ACTION_SEND`、`PROCESS_TEXT`、应用深链和手动输入统一受输入长度限制；网络响应在读取过程中限制为 256 KiB。
- 纯 Java 检查：`P07Check: PASS`。完整 Gradle 验证被环境阻断：Wrapper 缓存锁权限问题后，指定 ASCII 缓存仍因 `downloads.gradle.org` DNS 不可达而无法下载 Gradle 9.4.1；因此本次不能宣称 APK、Lint 或完整单测 PASS。
- 生产 POI 服务选择、授权/配额和客户端 Key 暴露仍是外部决策，不能由本次客户端修改关闭。

### 2026-11-10 — P0-8 API 35 启停门禁

- 用隔离 Debug 包在 `arrival-api35-fresh` 上执行 [Android 到站提醒门禁](WORKFLOW.md#android-到站提醒门禁)。正常场景注入模拟器 GPS，先等实际前台服务和首页「正在守候」，再点击停止。立即取消场景校验取消发生在服务启动命令送达之前，并等待超过启动超时窗口。
- 两轮均通过。最近一轮 25 次均确认服务退出、任务清除和守候卡片隐藏。Android 系统日志记录 25 次前台服务启动、25 次退出、0 次崩溃或 ANR。APK 哈希和日志位于 `C:/temp/arrival-api35-gate-afbd10a83969420487fe43425b432594/`。
- 此门禁由本地脚本运行。其余 Android 场景、签名候选包、真机和 Play 验收继续单独跟踪。

### 2026-11-10 — P0-8 API 35 中断恢复门禁

- 在同一脚本加 `-Recovery`。先用测试定位启动真实前台服务并确认任务已写盘。进程中断使用同 UID 的 `SIGKILL`，用 PID 变化验证；重启用 `adb reboot`，用开机标识变化验证。
- 两条路径都确认重开前进程和服务未自行运行。打开首页后原任务显示「需要恢复」，点击「恢复」才重新启动前台服务并收到定位。最后可正常停止并清除任务。
- 两轮恢复门禁通过。最近一轮在每条路径的恢复阶段各记录 1 次前台服务启动、1 次退出、0 次崩溃或 ANR。APK 哈希和日志位于 `C:/temp/arrival-api35-gate-16f3a63d27294ed58b6756611eedbf3d/`。
- 默认启停门禁随后复跑通过，仍记录 25 次前台服务启动和退出、0 次崩溃或 ANR。证据位于 `C:/temp/arrival-api35-gate-33c7e6b679644ed9b69482340fb16f6f/`。

### 2026-11-10 — P0-8 API 35 权限拒绝门禁

- 在专用 API 35 模拟器用 `-Permissions` 验证通知已拒绝及仅模糊定位。脚本对隔离包设置已拒绝状态，点 App 权限说明中的「继续」后由 Android 回调拒绝结果。
- 两条路径均捕捉到对应的拒绝提示，前台服务启动、崩溃与 ANR 事件均为 0。任务为空，首页守候卡片隐藏，待启动目的地已清除。证据位于 `C:/temp/arrival-api35-gate-44e8d783f4924ff3bbe45ecd6031880a/`。
- 原启停门禁随后复跑通过，Android 记录 25 次启动、25 次退出和 0 次崩溃或 ANR。证据位于 `C:/temp/arrival-api35-gate-f9d05f5ed7e94afdb7eab125232fad22/`。

### 2026-11-10 — P0-8 首次授权、权限撤销和版本矩阵

- 首次系统授权弹窗实际点击「不允许通知」，或选择「模糊位置」后点击「仅在使用应用时允许」。另保留系统已经记住拒绝的两条回调测试。守候中撤销精确位置或通知前，等待定位更新和真实前台服务，撤销后在重新打开前用系统进程及服务列表确认退出。重开时原任务显示「需要恢复」，权限仍缺失时恢复被拦截并显示对应提示。
- 首轮 API 35 合并跑法在重启后遇到系统 SystemUI ANR，导致权限提示未被测试捕获。独立权限用例通过，合并模式把重启放在最后。API 33 的 `run-as kill` 曾返回找不到 PID，改为同一个活跃测试进程向自身发出 SIGKILL，并由宿主检查其退出。Android 12 用设备服务状态替代镜像中缺失的前台服务计数，并跳过旧包管理命令无法构造的已记住拒绝状态。
- 模拟器使用同一隔离 Debug APK，SHA-256 为 `07F245E1656F2C51C806C718FE5DBECE0031F64D4B551646329F2156BAEB9C99`。每个版本的启停各 25 次，包含正常启停 1 次、立即停止和立即清空各 12 次，检查结束后服务、任务和守候卡片的状态。API 33 及以上在通过的启停轮次中各记录 25 次前台服务启动和 25 次退出。通过的用例未记录隔离应用崩溃或 ANR。

| 模拟器 | 启停 | 进程中断与重启 | 权限拒绝与运行中撤销 | 证据目录 |
|---|---|---|---|---|
| Android 12 / API 31 | PASS | PASS | 首次仅模糊定位、守候中撤销精确定位与重开后的恢复提示 PASS | `C:/temp/arrival-api35-gate-41c1c7703c3a40ef9bdde6bbc5835d54/` |
| Android 13 / API 33 | PASS | PASS | 4 条拒绝、2 条撤销 PASS | `C:/temp/arrival-api35-gate-53826febb45d457288071d695cfeedf7/` |
| Android 14 / API 34 | PASS | PASS | 4 条拒绝、2 条撤销 PASS | `C:/temp/arrival-api35-gate-dcb5a15d28e64a4fbf098d3aff9a712a/` |
| Android 15 / API 35 | PASS | PASS | 4 条拒绝、2 条撤销 PASS | 启停 `C:/temp/arrival-api35-gate-f9d05f5ed7e94afdb7eab125232fad22/`，恢复 `C:/temp/arrival-api35-gate-b9f80b2432504f569d33d12b510b6fd6/`，拒绝 `C:/temp/arrival-api35-gate-da3c0c261bcf404ab4ca600a571c0ae2/`，撤销 `C:/temp/arrival-api35-gate-89e104a42f04497ea365cddc9dde3afb/` |
| Android 16 / API 36 | PASS | PASS | 4 条拒绝、2 条撤销 PASS | `C:/temp/arrival-api35-gate-8549abe3f2154ddfb4a28456eba66553/` |

- API 36 首轮未能自动点选首次通知弹窗，之后模拟器离线且部分启动进程退出。清理先前测试遗留的模拟器网络进程后，同一应用 APK 的 `-All` 完整通过。API 36 现有通知隐私 instrumentation 也报告 `notification_privacy=PASS`，证据为同目录的 `existing-runner.log`。该检查覆盖通知脱敏与设置存储，通知栏按钮和到站页面另行验收。
- 当前工作区的 `testDebugUnitTest`、`lintRelease`、`bundleRelease` 和 `validateReleaseBundle` 通过。打包用 Release Manifest 未声明后台定位、精确闹钟或全屏通知权限，也未注册固定闹钟与开机 Receiver。手动到站提醒使用的前台定位仍需设备验证。证据位于 `C:/temp/arrival-p08-release-434662ab2c904d7cbf28e049d6a0ee2e/`。
- 当前 AAB 未签名，工作区未配置正式上传凭据。现有 GitHub 签名历史是旧版源码的证据。当前候选签名、Play Internal Testing 和 Pre-launch Report 待有对应版本及受保护的签名审批后执行。

### P0-6 本次验证与人工验收

- 命令：`gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --no-daemon --offline`。
- 最终结果：`BUILD SUCCESSFUL`；现有核心测试 0 failures/0 errors，Lint 无阻断错误；检查生产 classes.jar，NotificationPrivacy 存在，设备测试类不存在。
- 新增 NotificationPrivacyChecks：默认隐藏、偏好保存/关闭、清空后恢复隐藏、公开通知仅含通用标题且无正文/动作。设备测试 APK 已编译，未执行设备检查，不计为测试 PASS。
- 本次 Review：所有首发目的地通知均有脱敏公开版本；设置变更不重启定位和响铃流程；监听器销毁时注销，已停止服务不重发通知；保留渠道 ID 和声音/振动设置。主源码目录收窄为 src/com，防止 src/androidTest 测试类进入应用；为通过 Lint 给原有服务分支加括号，行为不变。
- 人工验收：用虚构地点“测试医院”开始提醒，锁屏检查守候、自动恢复、手动恢复和到站通知均默认不含地点；点击到站通知需解锁查看详情。开启显示并保存后检查普通通知包含地点，关闭保存后当前通知立即隐藏；公开版本仍为通用状态。检查取消设置不保存、旧版本升级默认关闭、清空数据恢复默认，并回归声音、振动、停止和恢复动作。至少覆盖 Android 8、当前 Android 和目标国产机。
- 剩余风险：系统及厂商通知展示、旧渠道用户设置、升级和设置切换仍需统一设备验证；公开隐私政策尚需按发布流程同步。P0-6 源码已提交本地 Git：`964e1bb`，未推送或发布。

上述历史 PASS 不代表本次检查点已重新完成全部测试，也不代表 P0 已全部关闭。统一验收须覆盖各项修复的交互、最低支持版本与当前版本；厂商后台限制仍需真机。整体结论保持 `NOT READY`。

备份范围为当前工作区源码、测试、资源及文档；SDK、AVD、安装包、构建产物、密钥和本地配置不纳入 Git。恢复步骤及工具链现状见 [实验日志](experiment_log.md)。

### 历史产品范围说明

项目负责人确认自动车站提示属于核心目标，首批面向中国大陆常见国产 Android 手机。此前将自动提示视为非核心增强功能的前提已由 ADR-0006 调整；当前代码和以下历史验证记录仍对应手动启动版本。新核心能力尚未实现或验证，整体仍为 `NOT READY`。

按项目负责人要求，P0-1 Play 内部测试、P0-2 上架证据提交和 P0-3 真机验收暂缓。没有 Play Console 不妨碍源码数据流核对、披露整改、自动测试设计及设备方案评估。自动检测所需权限、SDK、数据源、生命周期与商店兼容性需要重新建立证据，详见 [自动车站提示设计](STATION_PROMPT_DESIGN.md)。以下“当前停止点”保留为历史发布流程，恢复任务时优先以本节和新设计为准。

## 已完成

### Release 工程

- 已建立标准 Gradle/AGP 工程与 Gradle Wrapper，固定 AGP `9.2.1`、Gradle `9.4.1`、compile/target SDK `36` 和 Build Tools `36.0.0`。
- 已建立独立 `debug` / `release` 构建类型；Debug Application ID 带 `.debug` 后缀。
- 正式 Application ID 已确定为 `com.navobell.arrivalalarm`，版本为 `versionCode 8` / `versionName 0.4.3`。
- Release 已启用 R8、资源压缩和优化 ProGuard 默认规则。
- 已生成有效的 Release AAB，并使用 Bundletool `1.18.3` 完成格式验证。
- 已建立上传密钥环境变量接入；四项签名变量必须全部配置，否则签名验证失败。
- 已建立签名门禁：拒绝 Android Debug 证书，并检查 AAB 的签名文件、签名块和 `jarsigner` 结果。
- 高德 Web Key 已改为构建时环境变量注入，不再使用源码占位字符串。

### 质量和可复现性

- 核心 Java 测试套件已通过，并已接入 Gradle/JUnit；2026-09-21 复验为 `84 tests passed`，另有 `LocationRecoveryPolicyTest: PASS`，包含提醒状态机、Provider 生命周期、异步结果代次和地点联想交互门禁。
- `lintDebug` 与 `lintRelease` 已通过，阻断错误为 0；Release 警告经 2026-09-17 重新生成后从 35 条降至 5 条，处置明细见 [Lint 处置记录](LINT_DISPOSITION.md)。
- 已生成 `gradle.lockfile` 和依赖 SHA-256 校验元数据。
- Gradle 发行包配置了官方 SHA-256 校验值。
- 已针对中文 Windows 用户目录设置独立 ASCII Gradle 缓存和构建输出路径。
- 本地已完成一次干净的测试、Lint、R8、资源压缩、AAB 和 Bundletool 验证。

### P0-2 首发权限裁剪

- 当前状态：`PARTIAL`。代码、Manifest、构建和申报草稿已完成；正式隐私主体、Data Safety 核验、抓包和真机证据尚未完成。
- 可重复执行的工作流、证据包标准、完成判据和恢复点见 [`play/p0-2-workflow.md`](play/p0-2-workflow.md)。
- 首发产品范围已收缩为用户主动设置目的地并启动到站守候；长期车站识别、其他 App 信号识别和固定日程自动启动延期。
- Release Manifest 已移除后台位置、使用情况访问、精确闹钟、开机恢复和全屏通知，并取消注册通知读取服务、固定日程 Receiver 和开机 Receiver。
- 到站行为已降级为声音、振动和高优先级可点击通知；应用不再申请全屏通知特殊访问。
- 已增加权限申请前说明和应用内在线隐私政策入口。
- 已增加 `verifyReleasePermissionScope` 构建门禁，最终合并 Manifest 核验通过。
- 已建立 ADR-0004、Data Safety 草稿、Location FGS 申报文案和演示视频脚本。
- 2026-08-27 最终本地验证通过：核心测试、Release Lint、R8、AAB、Bundletool 和权限门禁全部 PASS；Lint 仍有 34 条非阻断结果，但没有本次权限范围相关的 `MissingPermission` 或特殊权限警告。

### P0-3 监控中断与手动恢复

- 当前状态：`PARTIAL`。代码、状态机测试、Debug/Release 构建和静态验证已通过；真机生命周期验收仍为 `BLOCKED`。
- 持久化任务与前台服务运行态已分开建模；无服务、启动超时、心跳过期或 Provider 全部失效会进入“需要恢复”，不会伪装成正在监控。
- `TrackingService` 使用 `START_NOT_STICKY`，不在重启或进程终止后静默恢复；恢复只能由可见 Activity 或用户点击恢复通知触发。
- Provider 以名称集合维护，运行代次隔离旧服务的延迟销毁、心跳和定位回调；恢复复用幂等启动动作，避免异步停止再启动竞态。
- 内部状态广播由按变体 Application ID 派生的 `signature` 权限保护；API 33+ 同时使用 `RECEIVER_NOT_EXPORTED`。
- 2026-09-21 复验：Debug 与 Release 全链均 `PASS`；84 个核心测试、两种 Lint、R8、权限门禁、Debug APK、Release AAB 和 Bundletool 均通过。
- 2026-11-10：API 35 模拟器的进程中断和重启后手动恢复通过，见上面的 P0-8 记录。强制停止、厂商省电、权限撤销、FGS 限制和恢复通知仍需在目标设备上验收。

### P0-4 服务启动与假激活

- 当前状态为 `PARTIAL`。API 35 模拟器已完成下述启动取消对照。其他 Android 版本和真机仍待验收，后台启动限制、权限变化和真实启动失败路径保留为 `BLOCKED`。
- 任务先持久化为 `starting`，只有 `TrackingService` 成功进入前台并注册至少一个定位 Provider 后才提交 `active`；`startForegroundService()` 同步异常、前台化失败、启动超时和 Provider 初始化失败统一转为 `interrupted` 并显示恢复通知。
- 服务存活期间定位过期先进入 `RECOVERING`，以 10 秒、30 秒退避最多重注册两次；收到新定位后恢复监控，权限/Provider 关闭或重试耗尽才要求用户手动恢复。
- 通知动作和固定入口复用 `ReminderRuntimeCoordinator`，不再用普通 `startService()` 绕过 FGS 启动契约；启动失败不会留下“active 但实际未运行”的任务。

#### 2026-11-09 — API 35 启动后立即取消对照

在同一个 API 35 AVD `arrival-api35-fresh` 测试两份 Debug APK。旧版来自 `7cc102e`，修复版包含 P0-4 防护。设备测试在同一主线程回调发出启动请求并立即取消，使取消先于 `TrackingService.onStartCommand()`。用 Logcat 核对进程崩溃及前台服务启动、退出事件。

| 场景 | 旧版 `7cc102e` | 含 P0-4 防护的 APK |
|---|---|---|
| UI 快速停止 | 8 次，未崩溃 | 8 次，未崩溃 |
| UI 清空数据 | 1 次，未崩溃 | 1 次，未崩溃 |
| 启动命令交付前停止 | 两轮独立试验均在第 1 次崩溃 | 12 次通过 |
| 启动命令交付前清空 | 12 次，未复现崩溃 | 12 次通过 |

旧版停止时记录了 `ForegroundServiceDidNotStartInTimeException`，异常指向 `TrackingService` 未调用 `startForeground()`。修复版 24 次竞态试验记录到 24 次前台启动和 24 次退出，最终状态均为 `STOPPED`、任务为空，未出现同类异常。结论限于 API 35、已授予定位和通知权限的测试环境。

单独导出 P0-4 暂存内容复验，`testDebugUnitTest`、`lintDebug`、`assembleDebug` 和 `assembleDebugAndroidTest` 全部通过。由该快照构建的 APK 在同一 API 35 AVD 再次通过停止、清空各 12 次。结果位于 `C:/temp/arrival-p04-checkpoint-logs-20261109/` 的 `probe-result.log` 和 `probe-events.log`。

本机原始日志位于 `C:/temp/arrival-p04-baseline-logs-20261109/`（`probe-stop-crash.log`、`probe-stop-repeat-crash.log`、`probe-clear-result.log`）和 `C:/temp/arrival-p04-repro-20261109/`（`probe-result.log`、`probe-events.log`）。

### P0-5 异步结果与生命周期绑定

- 当前状态：`PARTIAL`。代码、纯 Java 代次测试、Debug/Release 构建和静态验证已通过；Activity 旋转/销毁后的真实回调、进程回收和 Service 命令竞态仍需 instrumentation 或真机验收。
- `AsyncResultGate` 为页面任务和服务查询提供统一请求 token；`Future.cancel()` 后仍到达的回调必须再次通过组件代次、生命周期和 token 检查才能提交。
- `MainActivity` 的处理覆盖地点联想、坐标确认和固定地点解析；`TrackingService` 的处理覆盖车站反查。取消、替换请求和组件销毁都会使旧结果失效。
- 自动验证复验与缺口矩阵见 [`docs/experiment_log.md`](experiment_log.md)。此前用户提及的真机工作目前缺少设备、产物和日志绑定信息，暂记为 `UNKNOWN`，不作为 P0-5 PASS 证据。
- 2026-10-05 已建立可用的 API 35 AVD `arrival-api35-clean`；Debug APK 安装、主 Activity 启动、force-stop 后重启冒烟通过。Activity 旋转和迟到回调仍未运行 instrumentation，P0-5 继续为 `PARTIAL`。
- 2026-10-05 instrumentation 重试未形成 PASS：首次因 AVD 中旧 APK 签名不一致而被拒装，卸载后重试又卡在模拟器控制台/启动链；已更换为独立的 `arrival-api35-validation2` AVD，仍未进入 `adb device`。详见 [`docs/experiment_log.md`](experiment_log.md)，P0-5 继续为 `PARTIAL / BLOCKED`。
- 2026-10-05 清理后使用 `ARRIVAL_BUILD_DIR=C:\\temp\\arrival-build-p05-rebuild` 重建，Debug 单元测试、Java 编译和 `assembleDebug` 均 `PASS`；这只证明构建路径问题已绕开，不替代 instrumentation 运行时证据。
- 2026-10-06 已用管理员 ADB 35.0.2 启动 API 35 AVD，APK 安装和 `MainActivity` 的直接 `am start -W` 均 `PASS`；`am instrument` 自定义 Runner 不返回，instrumentation 仍为 `BLOCKED`，P0-5 不关闭。

### 签名流程验证

- 使用明确不会用于发布的临时非 Debug 测试密钥完成了 `verifyPlayBundle`，证明签名配置、证书检查和 AAB 校验链路可运行。
- 临时测试密钥和临时签名 AAB 不得用于正式上传，也不构成正式上传密钥已建立的证据。
- 已检查仓库，没有发现临时测试密码、测试密钥路径或私钥进入源码。

### CI 和凭据准备

- 仓库中已增加普通 Release 检查工作流和 `play-release` 环境签名工作流。
- 上传密钥已在仓库外保存，独立加密备份和恢复演练已通过。
- 签名工作流使用受保护的 GitHub `play-release` 环境和 `ARRIVAL_UPLOAD_*` Secrets。
- 远端签名 [`Build signed Play bundle #1`](https://github.com/yueqiu666/arrival-alarm-android/actions/runs/33254626252) 在 `main@4aa2af4` 上成功。远端 clean build、测试、Release Lint、R8、资源压缩、Bundletool 和 `jarsigner` 全部 PASS。
- 签名产物 `android-play-bundle-signed` 的 GitHub Artifact ID 为 `9715448302`，大小 190,688 bytes，归档摘要为 `sha256:75ddca52a66e423259c1c7bbab69c70af8dffddac77e14a058ead0e7e01064e5`，保留至 2026-09-12。
- 已临时下载该 CI 产物做独立核验：AAB 证书 SHA-256 与正式上传证书完全一致；核验后的本地临时产物已删除，未上传 Google Play。

## 尚未完成

### P0：关闭 P0-1 前必须完成

- [x] 已创建正式上传密钥、验证独立加密备份并完成恢复演练。
- [x] 已为签名工作流配置受保护的发布环境。
- [x] 已完成独立 Diff Review，并将 P0-1 Release 工程隔离为聚焦 Git checkpoint；地点联想和动画优化仍留在工作区，未混入本次提交。
- [x] P0-1 checkpoint 已推送到 GitHub；未签名 [`Android checks #3`](https://github.com/yueqiu666/arrival-alarm-android/actions/runs/33066043677) 在 commit `3ecd12f` 上成功，远端 clean build、test、lint、R8、资源压缩和 Bundletool 均通过。验证产物摘要为 `sha256:cdf1465e1c56c5b86c2085d760005dfdd91b06882b3162446aae41e67f407f3e`，不得作为 Play 上传包使用。
- [x] 已实际运行远端签名工作流，远端 clean build、test、lint、R8、签名和 Bundletool 全部成功。
- [x] 已保存远端签名 CI 的产物摘要，并独立确认 CI AAB 的证书指纹与正式上传证书一致；未使用此前的临时测试 AAB。

### 发布前仍需完成

- [ ] 创建 Google Play Console 应用并启用 Play App Signing；当前没有创建或上传任何 Play 应用。
- [ ] 上传到内部测试轨道，而不是直接生产发布。
- [ ] 使用 Bundletool/Play 生成的安装包，在最低支持版本和当前 Android 版本真机验证提醒、前台服务、通知、Receiver 和权限拒绝路径。
- [ ] 复核 Play 数据安全表、隐私政策、特殊权限声明、商店素材和内容分级。
- [x] 将正式开发者/发布者主体“曾文骏”及联系邮箱 `yueqiu@hrbeu.edu.cn` 补入隐私政策。
- [ ] 确认隐私政策中的开发者/发布者主体与 Play Console 开发者名称完全一致。
- [ ] 推送更新后的公开隐私政策，确认应用内 GitHub HTTPS 链接无需登录即可访问最新内容。
- [ ] 对 Release 地点查询进行抓包，确认 Photon、高德和系统 `Geocoder` 实际发送的域名、搜索关键词和坐标字段，再完成 Data Safety。
- [ ] 在最低支持版本及当前 Android 版本真机验证权限拒绝、主动启动、锁屏/后台守候、到站响铃通知、停止任务和旧版本升级路径。
- [ ] 在最低支持版本及当前 Android 版本真机验证 P0-3/P0-4/P0-5：启动提醒后强制停止/终止进程、重启设备、打开应用出现“需要恢复”、点击一次恢复后前台服务和定位监听重新建立；在 FGS 启动限制、权限撤销、Provider 关闭、Activity 旋转/退出和迟到查询各路径上确认不会假显示监控或回写旧结果。
- [x] 已重新生成 Release Lint 报告，35 条警告已逐条分类与处置，降至 5 条并记录理由，见 [Lint 处置记录](LINT_DISPOSITION.md)。
- [x] 已删除第三方网站链接入口（9 个 `https` 域名 + `BROWSABLE`）。`autoVerify` 只对自有域名有效，该入口无法实现“点击链接直接启动本应用”，只会在系统“打开方式”里出现，同时扩大导出面。目的地导入统一走系统分享、`PROCESS_TEXT`、`arrivalalarm://set` 深链和粘贴。

## 当前停止点

P0-1 的 Release 工程、正式凭据、独立备份、恢复演练、受保护 GitHub Secrets 和远端签名 CI 已完成，技术状态为 `READY FOR INTERNAL TEST`。

下一步是在 Google Play Console 创建应用、启用 Play App Signing，并用受控签名流程生成的产物进入内部测试轨道。P0-2 仍需核对 Play Console 名称、公开链接、Data Safety、抓包和真机证据。P0-3/P0-4/P0-5 仍缺最低与当前 Android 设备的生命周期验收，因此整体 Release Readiness 继续为 `NOT READY`。

## 2026-08-30：Play 发布准备补充

- `build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`：`PASS`。核心单测、Release Lint、R8、AAB、Bundletool 和首发权限门禁均通过。
- `assembleRelease`：`PASS`，生成本地未签名 APK，仅用于工程检查，不得上传。
- APK 静态字段：Application ID `com.navobell.arrivalalarm`、`versionCode 8`、`versionName 0.4.3`、minSdk 26、targetSdk 36；未发现 native libraries。
- 当前 SDK 没有 Android Emulator、系统镜像或 AVD，因此模拟器权限、后台定位、模拟位置和通知冒烟仍为 `BLOCKED`。
- 2026-08-30 已接受 Android SDK 许可并安装 Emulator 本体；API 35 `google_apis;x86_64` 系统镜像在 HTTPS 与 `--no_https` 两种模式下均卡在 `Preparing Install`，目标目录未下载有效镜像，判定为 SDK 仓库网络阻塞。未创建 AVD，未执行模拟器运行时测试。
- 公开 GitHub `main/PRIVACY.md` 可访问，但内容仍是旧版原型权限说明，与当前工作区首发隐私政策不一致；在同步公开文件前，不得将该 URL 作为最终 Play 合规材料。
- 已新增 [`play/store-listing.md`](play/store-listing.md) 商店资料草稿；尚未提交 Console。

## 完成判据

只有同时具备以下证据，`P0-1` 才能关闭：

1. 正式上传密钥存在两处独立、可恢复的加密副本。
2. 正式证书指纹已记录并与本地、CI 产物一致。
3. GitHub 受保护环境中的签名 CI 从干净检出成功运行。
4. 测试、Release Lint、R8、资源压缩、AAB 格式和 AAB 签名全部通过。
5. 项目负责人已完成 Diff Review 和 Git Checkpoint。
6. 产物进入 Play 内部测试并完成关键真机冒烟测试；尚未满足时不得称为可生产发布。
