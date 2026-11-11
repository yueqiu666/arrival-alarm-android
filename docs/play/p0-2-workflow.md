# P0-2 敏感权限与上架材料工作流

- 状态：`PARTIAL`
- 适用范围：首个 Google Play 发布版本
- 当前决策：ADR-0004，采用“用户主动启动的最小权限提醒”
- 目标：让代码行为、Manifest、应用内披露、隐私政策、Data Safety、Play 申报和真机证据保持一致，并且能够按同一流程重新验证

## 1. 当前结论

P0-2 不是单纯删除几行权限，而是一次产品范围、运行路径和上架证据的同步收缩。

首发版只支持：

1. 用户在可见 Activity 中选择目的地。
2. 用户明确点按开始。
3. 应用在精确位置和通知权限满足后启动 location 类型前台服务。
4. 服务持续显示通知，用户可以随时停止。
5. 到站后停止位置更新，并以声音、振动和高优先级可点击通知提醒。

首发版明确不支持：后台静默定位、长期车站识别、读取其他 App 使用情况、通知读取、固定日程自动启动、开机恢复、精确闹钟和全屏通知。

代码、Manifest、构建门禁和申报草稿已完成；公开政策、网络抓包、Play Console 问卷、演示视频和真机证据尚未完成。因此当前只能写 `PARTIAL`，不能写 `PASS` 或“可生产发布”。

## 2. Source of Truth

每次复核按以下顺序读取，不以聊天记录替代仓库事实：

1. 产品范围：[`../PRODUCT.md`](../PRODUCT.md)
2. 运行边界：[`../ARCHITECTURE.md`](../ARCHITECTURE.md)
3. 权限决策：[`../DECISIONS.md`](../DECISIONS.md) 中的 ADR-0004
4. 实际声明：[`../../android-app/AndroidManifest.xml`](../../android-app/AndroidManifest.xml)
5. 实际调用链：`MainActivity`、`TrackingService`、`ScheduleManager`、`AppPrefs`
6. 构建门禁：[`../../android-app/build.gradle.kts`](../../android-app/build.gradle.kts) 中的 `verifyReleasePermissionScope`
7. 对外披露：[`../../PRIVACY.md`](../../PRIVACY.md)
8. Play 草稿：[`permission-declarations.md`](permission-declarations.md) 和 [`data-safety.md`](data-safety.md)
9. 当前状态：[`../RELEASE_READINESS.md`](../RELEASE_READINESS.md)

当文档与当前代码冲突时，以代码、合并 Manifest、最终 AAB 和真实网络行为为已实现事实，同时修正文档或记录未决项。

## 3. 可重复执行的阶段工作流

### 阶段 A：定义首发核心功能

先回答：去掉该权限后，用户是否仍能获得首发不可替代的核心价值？

本次结论是“可以”。用户主动启动的到站守候不需要后台位置、使用情况访问、通知读取、精确闹钟或全屏通知。因此优先删除非核心能力，而不是为不成熟功能准备高风险审核材料。

输出证据：

- ADR 记录选择、替代方案、影响和回滚边界。
- PRODUCT 明确首发包含和不包含的功能。
- 验收路径能够用普通用户语言描述。

门禁：如果仍无法证明某项敏感权限对首发核心功能不可替代，默认不进入首发。

### 阶段 B：建立完整权限调用链

不要只检查 Manifest。对每一项能力追踪：

```text
用户入口
→ 应用内说明
→ 系统权限请求
→ 组件启动
→ 后台/锁屏行为
→ 通知与停止入口
→ 任务结束清理
→ 进程恢复
→ 旧版本升级
```

本项目重点检查：

- `MainActivity` 是否只能由用户主动操作启动守候。
- 拒绝精确位置或通知后是否停止启动流程。
- `TrackingService` 是否使用 location FGS、显示持续通知并提供停止方式。
- 到站或用户停止后是否移除位置更新并结束服务。
- Receiver、日程、开机和进程恢复路径是否可能静默恢复已撤回能力。
- 旧版本的自动出行状态是否被清除，旧日程是否取消调度但保留原始数据。

### 阶段 C：最小化 Manifest、UI 和运行路径

首发保留：

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `VIBRATE`
- `INTERNET`

首发禁止：

- `ACCESS_BACKGROUND_LOCATION`
- `PACKAGE_USAGE_STATS`
- `USE_FULL_SCREEN_INTENT`
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`
- `TransitNotificationListener`
- `FixedAlarmReceiver`
- `BootReceiver`

同步处理四个层面：

1. Manifest 不声明权限或组件。
2. UI 不展示被延期功能。
3. 服务恢复、Receiver 和升级路径不可重新启用它们。
4. 文案不再承诺自动识别、固定日程或全屏提醒。

构建门禁 `verifyReleasePermissionScope` 用于防止上述权限和组件意外回流。恢复任何一项前必须先新增或修订 ADR，不能直接删除门禁条目。

当前门禁读取项目自己的 `AndroidManifest.xml`。由于未来引入的依赖也可能通过 Manifest 合并带入权限，它不能替代对合并后 Release Manifest 和最终 AAB 的检查；本项目当前没有第三方运行时 SDK，但后续新增依赖时应把门禁升级为检查合并产物。

### 阶段 D：同步披露与 Play 材料

以下材料必须描述同一个产品：

- 系统弹窗前的应用内说明。
- 应用内可点击的在线隐私政策入口。
- 公开隐私政策。
- 商店详情页隐私政策链接。
- Location FGS 申报文案和演示视频。
- Data Safety 问卷。

披露必须说明：定位只在用户主动开始守候后持续使用，锁屏或离开界面时仍可能继续；用户停止或到站触发后停止位置更新。不得把 location FGS 写成“后台位置权限”，两者是不同的 Android/Play 机制。

### 阶段 E：运行自动验证

从仓库根目录执行：

```powershell
.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk"
```

该入口应覆盖：

```text
clean
→ testDebugUnitTest
→ lintRelease
→ verifyReleasePermissionScope
→ R8
→ 资源压缩
→ bundleRelease
→ bundletool validate
```

最低通过条件：

- 构建结果为 `BUILD SUCCESSFUL`。
- 权限门禁通过。
- Lint 没有 Fatal/Error；Warning 必须记录数量并区分是否与权限整改有关。
- 最终 AAB 有且只有一个。
- 合并后的 Release Manifest 只包含批准的权限和组件。
- R8 后的最终 DEX 不包含被延期组件类名或已删除功能入口文案。
- `git diff --check` 通过。

未配置正式上传密钥时生成的 AAB只能用于工程验证，不得上传 Play。

### 阶段 F：验证真实数据流

Data Safety 不能依据“没有自建后端”猜测。使用待发布配置对以下路径分别抓包：

1. Photon/OpenStreetMap。
2. 配置高德 Key 时的高德服务。
3. 设备系统 `Geocoder`；无法直接观察时记录设备、系统实现和可验证边界。

至少记录：

- 域名和 HTTPS 状态。
- 搜索关键词是否离开设备。
- 是否发送经纬度、精度或附近坐标。
- 请求中是否出现设备标识或其他意外字段。
- Release 是否实际配置高德 Key。
- 第三方服务条款和隐私政策版本。

用抓包事实更新 `data-safety.md`，再填写 Play Console 当前问卷。保存问卷截图，不能只保存聊天结论。

### 阶段 G：公开材料与 Console 核对

在提交前人工确认：

- `PRIVACY.md` 已推送，应用内链接无需登录即可访问最新内容。
- 隐私政策开发者/发布者名称与 Play Console 完全一致。
- 联系邮箱有效。
- FGS 表单与 `permission-declarations.md` 一致。
- 演示视频来自待提交 Release AAB，不是旧原型或临时包。
- 所有活跃测试/生产轨道均不存在会干扰审核的旧高权限包。

### 阶段 H：内部测试与真机验收

只上传内部测试轨道。将测试证据绑定到准确的 Git Commit、AAB SHA-256、versionCode 和上传证书指纹。

最低设备矩阵：

- 最低支持版本：Android 8.0 / API 26。
- 当前主流或最新目标 Android 版本。

最低场景：

1. 精确位置同意、拒绝和“仅大致位置”。
2. 通知同意和拒绝。
3. 权限不足时不启动 FGS。
4. 主动开始后切后台和锁屏，持续通知仍存在。
5. 进入提醒半径后声音、振动、通知正常。
6. 点按通知能返回正确页面。
7. 用户停止或到站后位置更新、服务和通知全部结束。
8. 无网络或地点服务失败时给出可理解结果。
9. 从旧原型升级时，旧自动状态不恢复、旧日程不触发。
10. 强制结束、撤销权限和进程恢复时不存在静默自动启动。

## 4. 证据包标准

每次候选发布保存一组可以独立复核的证据：

```text
Release Commit:
versionCode / versionName:
Application ID:
AAB SHA-256:
上传证书 SHA-256:
自动构建 Run / 日志:
合并 Manifest 摘要:
Lint Fatal / Error / Warning:
Data Safety 截图:
FGS 表单截图:
演示视频链接或文件摘要:
抓包日期、设备、系统和服务:
API 26 真机结果:
当前 Android 真机结果:
旧版本升级结果:
已知限制:
结论: PASS / FAIL / UNKNOWN / BLOCKED
```

任何一项证据来自旧 Commit、旧 AAB 或旧产品范围时，都不能复用于新的候选发布。

## 5. P0-2 完成判据

只有以下项目全部具备证据，才能将 P0-2 从 `PARTIAL` 改为 `PASS`：

- [x] 首发核心范围和敏感能力取舍已有 ADR。
- [x] Manifest、UI、运行路径和升级路径完成最小权限整改。
- [x] 构建门禁阻止延期权限和组件回流。
- [x] 单元测试、Release Lint、R8、资源压缩、AAB 和 Bundletool 验证通过。
- [x] 隐私政策、Data Safety、Location FGS 文案和视频脚本已有草稿。
- [ ] 隐私政策已公开，应用内及商店链接无需登录可访问。
- [ ] 开发者/发布者主体与 Play Console 完全一致。
- [ ] Release 地点查询真实数据流已抓包并与 Data Safety 对齐。
- [ ] Play Console Data Safety 和 FGS 表单已人工复核并保存截图。
- [ ] 使用准确 Release AAB 录制 FGS 演示视频。
- [ ] API 26 和当前 Android 版本真机测试通过。
- [ ] 旧版本升级路径验证通过。
- [ ] 内部测试轨道没有旧高权限构建干扰审核。

## 6. 本次操作经验

### 有效做法

1. **先删非核心产品范围，再讨论权限材料。** 不成熟功能不应仅因为已经写过代码就进入首发。
2. **同时处理五层一致性。** Manifest、UI、运行调用链、升级状态和对外披露缺一不可。
3. **保留旧数据但取消旧调度。** 这样既避免静默删除用户数据，也避免升级后恢复已撤回的后台行为。
4. **把政策决策变成构建门禁。** 只写文档容易回归，自动失败才能长期保护首发边界。
5. **验证最终产物，不只验证源码。** 合并 Manifest、R8 后 DEX 和最终 AAB 才是实际上架内容。
6. **把“代码完成”和“合规完成”分开。** 自动测试通过后仍应保持 `PARTIAL`，直到真实数据流、Console 和真机证据闭环。

### 容易犯的错误

1. 只删除 Manifest 权限，却保留 UI、Receiver、服务恢复或升级调用链。
2. 把 location FGS 与后台位置权限混为一谈。
3. 因为没有账号或自建后端，就回答“不收集/不共享任何数据”。搜索关键词和附近坐标仍可能离开设备。
4. 使用 Debug、临时签名包或旧 Commit 录制审核视频和真机证据。
5. 在隐私政策尚未公开或链接仍是旧内容时提交审核。
6. 将无关搜索优化、UI 改动和权限整改混入同一个 Git checkpoint，导致证据无法追溯。
7. 仅看源 Manifest；库合并、构建变体或旧轨道仍可能带回权限。

### 出现变化时如何处理

以下任一变化都必须重新从阶段 A 开始，而不是直接修改 Play 表单：

- 恢复后台位置、通知读取、使用情况访问、精确闹钟或全屏通知。
- 新增自动启动、开机恢复、日程触发或其他 App 信号触发。
- 新增地图服务、分析 SDK、广告、账号、后端或云同步。
- 修改数据保存、删除或第三方接收方。
- 修改最低/目标 Android 版本或前台服务类型。

需要新增或修订 ADR，并重新执行权限调用链、抓包、Data Safety、公开政策、视频和真机验证。

## 7. 当前恢复点

下一次继续 P0-2 时，不需要重复删除权限或重新设计首发范围。直接从以下待办开始：

1. 确认“曾文骏”与 Play Console 开发者/发布者名称完全一致。
2. 将当前隐私政策推送到公开仓库，并验证应用内链接无需登录访问最新版本。
3. 使用 Release 配置抓包 Photon、高德和系统 `Geocoder` 路径。
4. 根据抓包和当前 Console 定义完成人工 Data Safety 核对并保存截图。
5. 当前代码冻结并生成准确的签名候选 AAB 后，录制 Location FGS 视频。
6. 在 Play 内部测试轨道完成 API 26、当前 Android 和旧版本升级验收。

在这些证据完成前，保持：

```text
P0-2: PARTIAL
Overall Release Readiness: NOT READY
```

## 8. 渠道变体：小米应用商店

- 状态：`DEFERRED / NOT READY`
- 当前决定：只记录工作流，不创建小米应用、不上传安装包、不改变现有签名用途
- 适用时机：项目负责人明确决定增加或改用小米应用商店后

### 8.1 与 Google Play 的本质差异

| 项目 | Google Play | 小米应用商店 |
| --- | --- | --- |
| 提交产物 | 上传密钥签名的 AAB | 开发者最终签名的 APK |
| 分发签名 | Play App Signing 生成并签署设备 APK | 小米直接分发开发者提交的签名 APK |
| 当前 `arrival-upload` 密钥 | 只用于证明上传身份，可与应用签名密钥分离 | 若直接签小米 APK，会变成用户安装包的长期分发签名密钥 |
| 隐私材料 | Data Safety、权限声明、在线隐私政策 | 首次启动隐私政策同意、明确同意/拒绝、权限用途与证明材料、在线隐私政策 |
| 基础资质 | Google Play 开发者账号及商店要求 | 还需按小米当前规则核对软著/电子版权、APP 备案、ICP 和主体一致性 |
| 测试产物 | Play 内部测试生成的设备 APK | 小米渠道最终 APK 和小米/Redmi、HyperOS 设备 |

小米当前公开规则入口：

- [应用商店上架要求](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1322)
- [应用隐私合规标准](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1325)
- [位置权限申请标准](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1809)
- [位置权限审核材料指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1816)
- [应用上传常见问题](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1482)
- [应用资质 FAQ](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2251)

平台政策可能更新。实际开始小米渠道任务时，必须重新核对当日官方规则和账号后台字段，不能直接复用本次记录作为最终申报答案。

### 8.2 可以复用的部分

- ADR-0004 的首发最小权限产品范围。
- 用户主动启动 location FGS、持续通知和明确停止操作。
- Manifest 权限裁剪、服务恢复保护和旧版本状态迁移。
- 隐私政策中的实际数据流事实。
- Photon、高德和系统 `Geocoder` 的抓包方法。
- 单元测试、Lint、R8、资源压缩、依赖锁和供应链校验。
- 独立加密备份、恢复演练和受保护 CI 环境的凭据治理方法。
- API 26、当前 Android、权限拒绝、锁屏守候和旧版本升级测试矩阵。

这些内容只能复用为工程基础。Google Play 的 AAB、Play App Signing 状态、Data Safety 问卷截图和 Play FGS 视频不能直接当作小米渠道提交证据。

### 8.3 开始小米渠道前的不可逆决策

#### 最终 Application ID 和主体

在小米后台创建应用、申请软著或办理 APP 备案前，统一确认：

```text
应用名称
Application ID / 包名
开发者主体
软著或电子版权主体
APP 备案主办单位
ICP 主体
隐私政策主体
```

当前候选包名是 `com.navobell.arrivalalarm`，但在首次创建渠道应用和办理资质前仍需由项目负责人确认它是长期使用的最终标识。

#### 跨渠道签名策略

不得默认使用现有 `arrival-upload` 密钥签小米 APK。开始实现前必须选择并记录 ADR：

1. Google Play 与小米使用不同最终签名证书：风险隔离，但用户不能直接跨渠道覆盖更新。
2. 使用一把自主管理的跨渠道应用签名密钥：可保持跨渠道更新身份，但该私钥必须按应用整个生命周期最高等级保护。
3. 小米使用独立渠道签名密钥：治理清晰，但仍不具备与其他不同证书渠道的覆盖更新能力。

现有正式密钥是按 Google Play“上传密钥”建立和验证的。在签名策略 ADR 通过前，不改变其用途、不把它当作小米分发签名密钥。

### 8.4 小米渠道需要新增的工程工作

如果以后决定开始，单独建立小米 Release 任务，不直接改写 Google Play 工作流：

```text
assembleRelease
→ 生成最终签名 APK
→ apksigner 验证 v1/v2/v3 和证书 SHA-256
→ 安装、升级和卸载测试
→ 小米/Redmi 与 HyperOS 兼容验证
→ 输出渠道 APK、mapping 和证据摘要
```

验收至少包括：

- APK 包名、应用名称、versionCode、versionName 和预期签名证书一致。
- 后续版本能够在前一小米渠道版本上直接升级。
- 最低支持版本和 Android 11+ 的签名方案满足安装要求。
- APK 中的权限与小米后台自动解析结果一致。
- 不使用临时测试 APK、Debug APK、Google Play AAB 或旧 Commit 录制材料。

### 8.5 小米隐私与权限差异门禁

当前应用已有权限申请前说明和在线隐私政策入口，但开始小米渠道前还需专项确认或实现：

- 首次运行主动显示隐私政策同意界面。
- 提供含义明确的“同意”和“拒绝”，不使用“好的”“我知道了”。
- 用户同意前不读取或传输个人信息。
- 涉及精确位置等敏感个人信息时，按当前规则判断是否需要单独同意。
- 应用内能够方便地再次查看隐私政策和撤回同意。
- 隐私弹窗、应用内政策、公开 URL 和后台提交内容保持一致。
- 位置权限由用户触发相关功能时再申请，并准备功能截图和用途说明。
- location FGS 常驻通知真实反映正在运行的守候任务，并可由用户关闭任务。

这里属于新的产品行为和合规实现，不能只修改商店文案。实施时需要独立任务、回归测试和真机验证。

### 8.6 小米资质门禁

在投入渠道 APK 和审核材料工作前，先在小米开发者后台只读确认账号实际要求：

- 开发者账号是个人还是企业，实名认证是否完成。
- 计算机软件著作权、APP 电子版权或其他认可版权材料。
- APP 备案状态和服务备案号。
- ICP 证明及主体一致性要求。
- 是否需要在应用内显著展示、并可点击查询 APP 备案编号。
- 当前应用类别是否要求额外行业资质。

如果基础资质尚未具备，将状态记录为 `BLOCKED_BY_QUALIFICATION`，不通过临时包名、借用主体或不一致材料绕过。

### 8.7 小米渠道恢复点

未来明确开始小米渠道时，严格按以下顺序恢复：

1. 打开并登录小米澎湃 OS 开发者平台，只读确认账号、认证和资质字段；暂不创建应用。
2. 确认应用名称、包名和开发者/备案/版权主体是否能够长期保持一致。
3. 盘点软著或电子版权、APP 备案、ICP 和可能的行业资质。
4. 新增跨渠道签名策略 ADR，明确现有 `arrival-upload` 密钥仍只作为 Google Play 上传密钥。
5. 设计小米正式签名 APK 流水线和凭据隔离方案。
6. 实现首次隐私同意、拒绝、撤回和同意前数据隔离。
7. 运行 APK 签名、权限、抓包、真机、升级和 HyperOS 兼容验证。
8. 所有证据绑定准确 Commit、APK SHA-256、versionCode 和签名证书指纹后，才进入小米后台创建和提交流程。

当前停止点：

```text
Xiaomi channel: DEFERRED / NOT READY
No Xiaomi app created
No APK uploaded
No signing-key role changed
```
