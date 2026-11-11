# Technical Decisions

本文件是重要技术决策的追加式记录。修改架构、依赖、权限、用户数据、网络服务、后台执行、平台版本、构建或签名策略前，先新增或修订一条决策记录。已被替代的决策保留历史并标为 `Superseded`。

## ADR-0001：保持无 Gradle 和无第三方 SDK 的原生 Android 构建

- Status: Superseded by ADR-0003
- Recorded: 2026-08-26
- Original decision date: Unknown

### Context

当前应用最低支持 Android 8.0，并由 PowerShell 脚本直接调用 Android Platform 36、Build Tools 36.0.0 和 JDK。仓库不依赖 Gradle、AndroidX、Google Play Services 或第三方 SDK。

### Decision

在有独立决策证明迁移收益前，继续以 `android-app/build-apk.ps1` 作为唯一受支持的构建入口，并优先使用 Android 平台 API 和纯 Java 核心逻辑。

### Consequences

- 构建链较小、依赖和供应链面较窄。
- UI、测试、依赖管理和现代 Android 工具支持需要更多手工维护。
- 引入 Gradle、AndroidX 或第三方 SDK 属于架构变更，必须先评估维护成本、体积、许可证、隐私、供应链、迁移和回滚方案。

## ADR-0003：采用 Gradle/AGP 作为 Android 唯一构建系统

- Status: Accepted
- Recorded: 2026-08-26
- Supersedes: ADR-0001

### Context

手工 PowerShell 构建只能产出调试签名 APK，不能提供 Google Play 新应用需要的 AAB、标准 Release 变体、R8、资源压缩、Android Lint 或受控上传签名。继续扩展手工链会重复维护 AGP 已提供的发布能力，并增加签名、版本和工具漂移风险。

### Decision

`android-app` 采用固定版本的 Gradle Wrapper 和 Android Gradle Plugin，作为 Debug、测试、Lint、APK 与 AAB 的唯一构建系统。首轮迁移通过 Gradle `sourceSets` 接入现有源码目录，避免同时移动业务代码；`build-apk.ps1` 只保留为调用 Wrapper 的兼容入口。

正式 Application ID 为 `com.navobell.arrivalalarm`；现有 Java 命名空间暂时保持 `com.example.arrivalalarm`，避免在发布工程任务中混入机械改名。Release 使用 R8 和资源压缩。上传密钥只通过本机或 CI 的安全环境变量提供，不写入仓库；首次上传后由 Play App Signing 管理面向用户的应用签名密钥。

### Alternatives

- 继续扩展手工 SDK 脚本：供应链较小，但需要自行实现和长期维护 AAB、Lint、优化、变体与签名集成，因此拒绝。
- 同时迁移到标准 `app/src/main` 目录并重命名全部 Java 包：最终结构更常见，但会扩大当前 P0 的 Diff 和回归面，留待独立任务。

### Consequences

- 构建会下载固定版本的 AGP、Gradle、JUnit 和 bundletool，供应链面增加；通过固定版本、Wrapper 发行包校验、依赖锁和依赖校验元数据控制。
- Windows 工作区位于含中文字符的用户目录，因此显式启用 AGP 的 `android.overridePathCheck`，并由本地构建入口把 Gradle 缓存和构建产物放到系统临时目录中的项目专用 ASCII 路径；源码不复制，CI 使用其原生 ASCII 工作目录和标准 `build/`。
- 未提供上传密钥时仍可生成并验证未签名 Release AAB；`verifyPlayBundle` 会明确失败，避免误把未签名包当作可上传产物。
- `com.navobell.arrivalalarm.debug` 与原调试包是不同应用，已有原型安装不会原地升级，测试设备需要并存或手动迁移验证。

### Verification

- `gradlew clean testDebugUnitTest lintRelease bundleRelease validateReleaseBundle` 成功。
- 使用临时生成、不会用于发布的非 Debug 测试密钥配置四个 `ARRIVAL_UPLOAD_*` 环境变量后，`gradlew verifyPlayBundle` 成功，并确认签名证书不是 Android Debug；正式上传密钥仍需在首次上传前单独创建、备份并放入 CI Secret。
- 待正式上传密钥和 Play 内部测试包就绪后，使用 bundletool/Play 生成测试 APK，并在最低和当前 Android 版本真机完成提醒、前台服务、Receiver 和通知冒烟测试；当前尚未完成。

## ADR-0002：用户数据本地优先且不建设业务后端

- Status: Accepted
- Recorded: 2026-08-26
- Original decision date: Unknown

### Context

当前产品没有账号、自建业务后端、广告或分析 SDK。任务、最近地点、固定日程和设置保存在应用私有 `SharedPreferences`。地点关键词和可选附近坐标会直接发送给地点服务。

### Decision

提醒和设置数据继续保存在本机。应用只为明确的地点查询调用外部服务，并在产品和隐私文档中披露数据边界。

### Consequences

- 减少账号、同步、后端安全和用户画像风险。
- 换机同步、多设备共享和服务端恢复不在当前能力内。
- 新增后端、遥测、云同步或新的数据接收方前，必须进行产品、隐私、安全和迁移评审。

## ADR-0004：首个 Play 版本采用用户主动启动的最小权限提醒

- Status: Superseded in product scope by ADR-0006; retained as current implementation baseline
- Recorded: 2026-08-27

### Context

原型同时声明后台位置、使用情况访问、通知读取、精确闹钟和全屏通知，并提供长期出行检测与固定日程自动启动。产品评审确认：自动识别出行当前可靠性不足，固定日程属于后续优化；首发不可替代的用户价值是用户主动选择目的地、启动持续守候并在到站时收到响铃和通知。

这些增强功能不足以证明后台位置和其他特殊访问对首发核心功能不可替代。继续保留会扩大隐私、平台兼容、耗电和 Google Play 审核风险。

### Decision

首个 Play 版本只支持用户在可见 Activity 中主动启动到站提醒。启动后由 location 类型前台服务持续定位，任务停止或触发后停止位置更新；到站使用声音、振动和高优先级可点击通知，不使用全屏通知。

首发 Manifest 不声明后台位置、使用情况访问、精确闹钟、开机完成或全屏通知，不注册通知读取服务、固定日程 Receiver 或开机 Receiver。长期车站识别、其他 App 信号识别和固定日程自动启动保留为后续独立产品与架构评审事项，不在首发 UI 或运行调用链中启用。

升级安装时保留已有固定日程数据，但取消其系统调度；旧版出行检测设置被禁用并清除其运行状态，避免升级后恢复已撤回的后台行为。

### Alternatives

- 为全部原型能力准备后台位置和特殊权限材料：功能并非首发核心且可靠性证据不足，因此拒绝。
- 继续全天运行 Location FGS 识别车站：耗电和平台政策风险高，并且 Google Play 已公布后续不再接受以 FGS 承担 geofencing 的方向，因此拒绝作为首发架构。
- 立即引入 Geofence API 和全国车站数据：需要新依赖、数据源、无 GMS 兼容和独立可靠性验证，超出本次 P0 范围。

### Consequences

- 首发权限面和审核材料显著缩小，运行行为与用户主动操作一致。
- 用户必须主动设置并启动每次到站提醒；首发不会自动发现车站或按日程启动。
- 既有固定日程记录暂留本机但不可编辑或触发，后续重新引入功能时可制定迁移策略。
- 后续恢复任一敏感能力必须新增或修订 ADR，并提供可靠性、耗电、隐私和 Play 审核证据。

### Verification

- 构建门禁拒绝首发 Manifest 中重新出现上述权限和组件。
- 单元测试、Release Lint、Release AAB 与 Bundletool 验证通过。
- 真机验证用户主动启动、切后台持续守候、到站响铃通知、停止任务和权限拒绝路径。

## ADR-0005：监控中断后必须由用户手动恢复

- Status: Accepted
- Recorded: 2026-08-30
- Supersedes: None

### Context

Android 设备重启、系统终止进程或厂商省电策略都可能使 `TrackingService` 不再运行。`AppPrefs` 中保留的 `active` 任务只能证明用户曾经启动过提醒，不能证明当前仍有定位服务。`ADR-0004` 又明确禁止首发版本注册开机 Receiver 或通过延期权限实现后台自动恢复。

### Decision

将任务数据状态与服务运行态分开判断。应用打开时，若存在 `active` 任务但当前进程没有运行中的 `TrackingService`，立即将任务转为 `interrupted`，首页显示“需要恢复”和一个按钮；用户点击后才重新启动 location 前台服务。恢复路径保留现有定位/通知权限门禁。`TrackingService` 返回 `START_NOT_STICKY`，系统不会因为任务仍保存在本机而自动重建监控。

实现上，运行态由不持久化的 `ReminderRuntime.Snapshot` 管理，并由 `ReminderRuntimeCoordinator` 统一协调 Activity、恢复通知和服务。状态必须经过前台启动、定位监听注册和首个定位三个事实阶段，心跳使用 `SystemClock.elapsedRealtime()`；Provider 使用集合而不是可被乱序回调破坏的计数器。恢复启动请求带运行代次，服务不会让旧实例的延迟销毁或定位回调覆盖新请求；恢复路径复用同一个服务实例的幂等启动动作，避免异步停止再启动的竞态。

### Alternatives

- 注册开机 Receiver 自动启动：违反 ADR-0004 的首发权限和后台策略，拒绝。
- 继续仅用持久化 `active` 或延迟心跳判断：无法在应用刚打开时区分“任务存在”和“服务正在运行”，会继续产生误导，拒绝。

### Consequences

- 重启或进程终止后的恢复动作明确、可见且由用户控制。
- 用户需要在恢复态点击一次按钮；权限被撤销时需要先重新授权。
- 应用未打开时不会收到开机即时恢复通知，这是不注册开机 Receiver 的直接结果；打开应用后会显示恢复状态并补发恢复提示通知。

### Verification

- 纯 Java 状态测试覆盖无服务立即恢复、心跳过期、恢复后监控和到站已触发状态。
- 真机验证：启动提醒、强制停止/重启设备、打开应用首页出现“需要恢复”、点击一次恢复后前台服务和定位通知重新出现。
- 纯 Java 回归测试还覆盖 Provider 注册、重复注册、未知 Provider 禁用、逐个禁用和清空；全量 Java 源码结合 Android API 编译检查通过。
- 2026-09-01 重装固定的 Build Tools `36.0.0` 后，Debug 全链与 Release 全链均通过：55 个核心测试、Debug/Release Lint、R8、权限门禁、APK/AAB 和 Bundletool 均有完成证据。
- 内部状态广播使用按 Application ID 派生的 `signature` 权限；API 33+ 额外使用 `RECEIVER_NOT_EXPORTED`，避免外部伪造状态刷新并允许不同签名的 Debug/Release 并存。
- 当前仍无真机或有效 AVD；真实进程终止、重启、厂商省电、FGS 限制、权限撤销和恢复通知路径仍需设备验证。

## ADR-0006：自动车站提示属于核心目标，优先面向国产 Android

- Status: Accepted for product direction; implementation architecture pending
- Recorded: 2026-09-07
- Supersedes: ADR-0004 中将自动车站提示视为非核心增强功能的产品前提

### Context

项目负责人明确，用户忘记设置提醒正是产品要解决的问题：接近地铁站或高铁站时收到设置提示，点击后选择目的地并主动开始守候。首批用户使用中国大陆常见国产 Android 手机。此前仅以自动提示非核心为由维持手动启动范围，已经不符合该产品目标。

### Decision

将自动车站提示纳入核心目标重新设计，现有代码作为可回滚的手动启动基线。Google Play 服务不能作为首批设备的必备依赖；国内定位或地理围栏 SDK 仅为候选，尚未选型或引入。

项目负责人接受开启自动提示后显示“车站提示已开启，可随时暂停”一类持续运行通知。用户明确开启后默认全天运行，并可选择暂停、关闭或设置运行时间段。该选择仅确定用户体验边界，不代表后台执行、耗电或权限审核已通过。

产品必要性、系统允许执行和商店允许发布分别需要证据。用户将功能定义为核心，不等于系统会授予权限或商店会批准用途。只增加披露文案不能完成本次目标。

继续暂缓真机操作、Play 内部测试和商店材料提交。恢复旧服务、增加权限或 SDK、选择商店、提交审核都不是本次文档变更的内容。ADR-0005 的现有到站任务恢复行为仍作为实现基线；自动提示在重启或进程终止后的恢复方式需要单独设计。

### Consequences

- P0-2 的原最小权限证据仍对应现有构建，不能用于宣称自动车站提示已经验证。
- P0-3 需要分别考虑自动检测和用户已确认的到站任务，不能用同一个 active 状态代表两者运行正常。
- 车站数据授权、目标机型、SDK 数据处理、耗电、重复提示和重启恢复仍为待决项，不填入未经确认的默认阈值。
- 旧 Geocoder 地址名称匹配并非可靠的车站进入检测，不能作为恢复功能的完成标准。

### Verification

产品流程、已知证据、候选方案、待决项和可在无 Console 环境推进的验收工作见 [自动车站提示设计](STATION_PROMPT_DESIGN.md)。具体 SDK、权限和后台架构确定后另行记录实施决策；本条不宣称运行时或上架验收通过。

## ADR-0007：提醒任务启动必须经过持久化握手

- Status: Accepted
- Recorded: 2026-09-09
- Supersedes: ADR-0005 中关于任务状态与服务运行态分离的实现约束

### Context

`startForegroundService()` 成功返回只代表启动请求已提交，不能证明服务已经进入前台，更不能证明定位监听已经建立。此前提醒任务在启动服务前就保存为 `active`，因此 Android 12+ 后台启动限制、Android 14+ location 前台服务限制、权限变化或定位 Provider 初始化失败时，磁盘上的任务可能仍显示为正在守候。

### Decision

提醒任务增加持久化的 `starting` 状态。创建或恢复任务时先保存为 `starting`；`TrackingService` 成功调用 `startForeground()` 并注册至少一个定位 Provider 后，才将任务提交为 `active`。服务启动调用、前台化或定位监听建立失败，以及启动握手超时，统一将任务转为 `interrupted`，发送带“恢复”动作的通知，并保留用户手动恢复入口。

所有提醒启动入口复用 `ReminderRuntimeCoordinator` 的异常处理；通知操作与固定闹钟入口不再各自维护启动失败逻辑。自动车站提示的前台服务启动也捕获同步异常并回滚开关状态。运行态仍由进程内 `ReminderRuntime.Snapshot` 表达，不能用持久化状态替代服务事实。

### Alternatives

- 继续在请求提交后保存 `active`：无法区分请求成功与服务实际可用，拒绝。
- 只依赖进程内运行快照：进程终止后无法向用户留下可恢复的持久事实，拒绝。
- 在进程死亡后后台反复自行拉起整个 FGS 或使用开机恢复：超出当前首发后台策略，且会重新引入 Android 版本和厂商限制，拒绝。服务仍存活时的定位监听重注册不属于这一层，必须有次数和退避上限。

### Consequences

- UI 和持久化数据不会在服务建立前声称监控已经 active。
- 启动中进程被杀或超时后，任务会显示为需要恢复，用户可以从应用或通知重新启动。
- 真机仍需验证 Android 12-16 的 FGS 限制、通知动作、定位权限和精确闹钟场景；本机当前缺少 Android SDK，自动构建尚未执行。

### Verification

- 纯 Java 状态测试覆盖 `starting`、无服务恢复、定位监听建立后转 `active` 和失败恢复。
- Android 构建、Lint、通知动作、精确闹钟和 Android 12-16 真机/模拟器验证待 SDK 与设备环境具备后执行。

### Follow-up hardening

服务存活和业务监控有效不是同一个事实。即使服务心跳仍在更新，定位 Provider 也可能长期不再提供新位置。因此 `ReminderRuntime` 使用 5 分钟的定位新鲜度窗口；精确提醒的同时使用零距离过滤，使静止设备仍按时间收到定位，不以“位置没有变化”冒充挂机。服务心跳在后台主动检查该窗口，超过后先进入 `RECOVERING`，以 10 秒和 30 秒退避重新注册 Provider，最多尝试两次。恢复探针同样使用零距离过滤；权限被撤销、全部 Provider 被关闭、服务启动限制或重试耗尽时才转为 `interrupted` 并发送恢复通知。状态判断始终拒绝把 `RECOVERING` 或过期定位显示为 `MONITORING`。5 分钟是当前定位间隔、弱信号和系统批处理之间的工程折中，后续应通过目标设备测试重新校准。

## ADR-0008：导入坐标必须按来源转换坐标系

- Status: Accepted
- Recorded: 2026-09-17

### Context

目的地可以从地图链接、系统分享或粘贴文本导入，解析出的经纬度会直接进入距离计算，不再经过名称搜索。中国大陆的第三方地图使用的坐标系与设备定位不同：高德和腾讯使用 GCJ-02，百度使用 BD-09，而设备 GNSS 与系统 `Location` 使用 WGS-84。在成都，两套坐标系之间的偏移可达数百米。

此前的实现只对高德搜索结果做了 GCJ-02 到 WGS-84 的转换，导入路径完全不做转换。结果是本该“最准”的坐标直用路径反而系统性地偏移数百米，而且偏移量无法从确认框里的地址文本直接看出来。

### Decision

坐标转换统一放在解析边界完成，让 `confirmCoordinateDestination` 之后拿到的坐标一律是 WGS-84：

- 文本或链接包含 `amap.com`、`amapuri://`、`androidamap://`、`map.qq.com`、`qqmap://` 时按 GCJ-02 转换成 WGS-84。
- 文本或链接包含 `baidu.com`、`baidumap://` 时按 BD-09 转换成 WGS-84。
- `arrivalalarm://set` 由本应用定义，坐标一律视为 WGS-84，不做转换。
- 来源无法识别时不转换，并在目的地确认框里明确标注“坐标系未能识别、按原值使用”，由用户核对地址。

转换全部在本地完成，不引入地图 SDK、在线坐标转换服务或新的数据接收方。

### Alternatives

- 一律按 GCJ-02 转换：会把 Google Maps 等本来就是 WGS-84 的链接改错，拒绝。
- 一律不转换、仅在界面提示：偏移达数百米，用户无法从地址文本判断，等于把错误留给用户，拒绝。
- 调用第三方坐标转换 API：引入新的数据接收方、网络依赖和隐私披露，拒绝。

### Consequences

- 从高德、百度、腾讯导入的目的地与设备定位处于同一坐标系，提醒半径不会被系统性偏移吃掉。
- 来源无法识别的坐标仍然保留原值，但界面会明确说明，不会静默当作 WGS-84。
- 12306 与滴滴的分享文本没有携带坐标的样本证据；若将来确认它们会带坐标，需要单独核验坐标系并补充映射。
- 目的地反查地址使用的是转换后的 WGS-84 坐标，反查结果比转换前更可信。

### Verification

- 纯 Java 测试覆盖：高德链接 GCJ-02 往返、腾讯链接 GCJ-02 往返、百度链接 BD-09 转换偏移、`bd09ToGcj02` 往返、以及未知来源保持原值。
- `gradlew clean testDebugUnitTest lintRelease bundleRelease validateReleaseBundle --no-build-cache --no-daemon`：`BUILD SUCCESSFUL`，Lint 阻断错误 0 个。

## ADR-0009：异步结果必须同时校验组件所有权和请求代次

- Status: Accepted
- Recorded: 2026-09-21

### Context

P0-5 的旧 Activity 回调、P0-3 的旧服务回调和 P0-4 的启动失败回滚属于同一类一致性问题：任务发出后，接收结果的对象或业务状态可能已经失效。`Future.cancel()` 只能尽力取消，不能阻止已经运行或已经排队的回调继续提交结果；仅调用 `isDestroyed()` 也不能区分同一页面上的旧请求与新请求。

### Decision

在不引入 AndroidX、`ViewModel`、协程或 WorkManager 的前提下，使用同一套所有权和请求代次语义：

- `AsyncResultGate` 为每次异步请求生成单调递增 token；新请求或取消操作会使旧 token 失效，结果提交前必须再次确认 token 仍当前。
- `MainActivity` 的联想、坐标确认和固定地点解析都使用结果 gate；`onStop`/`onDestroy` 取消失效 Future，并把 Activity 代次、STARTED 状态、`isFinishing` 和 `isDestroyed` 与 gate 一起检查后才能更新 UI 或显示 Dialog。
- `TrackingService` 的独立异步查询同时检查服务实例代次和结果 gate；命令切换、停止和销毁都会取消查询并使旧结果失效。
- P0-3/P0-4 继续使用“持久化任务状态 + 进程内运行快照 + 服务确认握手 + 有界恢复”，结果 gate 只补充异步提交边界，不替代提醒状态机。

只有当前页面或当前服务实例、当前请求三者都有效时，结果才可以提交。

### Alternatives

- 引入 AndroidX `Lifecycle`/`ViewModel` 和协程：能获得官方生命周期支持，但会改变当前无 AndroidX 的单模块架构并扩大依赖、迁移和测试范围；当前短时查询可以先用等价的手工 gate，拒绝本次引入。
- 只调用 `Future.cancel()`：无法覆盖已经进入主线程队列的结果，拒绝。
- 只在显示 Dialog 前检查 `isDestroyed()`：无法处理同页面上的旧搜索结果覆盖新操作，拒绝。
- 把所有查询升级为 WorkManager：这些查询只服务于当前页面或当前服务会话，持久化会扩大语义和生命周期，拒绝。

### Consequences

- 旧 Activity、旧服务实例和旧请求都不能再提交 UI 或业务结果。
- 取消、代次失效和生命周期检查成为必须同时维护的约束；没有编译器强制，新增异步入口必须复用 gate 并增加回归测试。
- 仍无法保证底层网络或系统 `Geocoder` 立即停止，只能保证其迟到结果不具备提交资格。

### Verification

- `AsyncResultGate` 的当前、替换和取消语义有纯 Java 回归测试。
- Activity 和 Service 的结果回调同时检查组件代次与请求 token。
- `build-apk.ps1` 与 `build-release.ps1` 已通过测试、Debug/Release Lint、R8、权限门禁和 Bundle 校验；真实 Activity 旋转、进程终止和厂商后台限制仍需真机或 instrumentation 验收。

## 新决策模板

```markdown
## ADR-NNNN：决策标题

- Status: Proposed | Accepted | Superseded | Rejected
- Recorded: YYYY-MM-DD
- Supersedes: ADR-NNNN（如适用）

### Context

需要解决的问题、约束和当前证据。

### Decision

选择什么，以及明确不选择什么。

### Alternatives

考虑过的更简单或不同方案及未选择原因。

### Consequences

收益、成本、风险、迁移、兼容和回滚边界。

### Verification

如何证明决策按预期工作。
```

## ADR-0010：目的地通知默认隐藏并由用户主动选择显示

- Status: Accepted
- Recorded: 2026-10-06
- Scope: P0-6

目的地显示偏好使用应用私有 SharedPreferences，缺省值为 false。守候、定位恢复、手动恢复和到站通知默认不包含目的地，并统一设置 PRIVATE 和不含地点的 publicVersion；用户主动开启后才在普通通知中加入目的地，实际锁屏展示仍由系统设置决定。到站详情 Activity 不覆盖锁屏。保存偏好时更新已有通知，不重新启动定位或声音。保留现有渠道 ID、声音和振动配置，不删除重建渠道。清空设置恢复默认隐藏。

## ADR-0011：地点搜索失败必须与无匹配分开

- Status: Accepted
- Recorded: 2026-10-12
- Scope: P0-7

搜索结果是用户确认目的地的候选，不是用户意图本身。单个候选也必须展示可核对信息并等待确认；服务失败不能被转换成“没有地点”，也不能作为无匹配缓存。

客户端使用有界的顺序 fallback pipeline：服务返回候选才是 `SUCCESS`，所有提供方正常返回空列表才是 `NO_MATCH`；超时、网络、限流、配置和响应错误为 `UNAVAILABLE`，取消为 `CANCELLED`。输入、搜索词和响应分别设置本地资源上限。

生产地点服务的授权、SLA、配额和 Key 管理不由客户端默认 Photon 或 APK 中的 Web Key 证明，必须另行确认后才能关闭 P0-7。

## ADR-0012：在 Debug 包中对照高德 Android 搜索

- 状态：已接受用于内部测试
- 日期：2026-11-09
- 范围：P0-7 地点服务选型

同一 Debug 包提供现有搜索与高德搜索两个选项。用户在搜索页主动选择高德并同意数据说明后，应用才调用高德 SDK。高德模式独立查询，结果和失败均保留真实来源，便于按同一批站名、同名地点和出入口做对照。退出高德模式后回到现有搜索。

Debug 固定使用 Maven Central 提供的独立搜索 SDK `com.amap.api:search:9.7.1`，由 Git 忽略的 `android-app/local.properties` 提供测试 Key。应用不主动把附近坐标作为高德查询条件。确认框继续使用现有目的地模型，高德坐标按现有转换逻辑归一化为 WGS-84。Release 构建保持原有搜索路径。内部测试须核对授权弹窗、数据和网络行为、地点命中及落点，再决定生产授权及 Release 集成。

## ADR-0013：到站声振由系统通知渠道统一控制

- 日期：2026-11-10
- 范围：到站通知、声振与服务结束
- 状态：由 ADR-0016 取代

到站时停止定位，将守候通知切换为到站通知。声振只由原有 `arrival_alarm` 渠道播放，沿用已安装渠道的用户设置。通知首次触发后按原有 30 秒间隔更新，最多发出 10 次声振请求，实际声振由渠道设置和系统决定。达到 5 分钟后停止前台服务，留下普通到站通知和待确认任务。隐私偏好更新使用静默通知更新。

「我已到达」和系统允许的通知划除只处理该通知关联的任务。用户确认、主动停止或清空数据时取消通知和剩余声振。前台通知切换失败时，结束服务并尝试发布一次普通到站通知。

## ADR-0014：首页状态与持久化任务分开管理

- 日期：2026-11-10
- 范围：首页重建、定位质量与本机数据

首页使用 Android 实例状态保存草稿、确认界面和权限上下文。活动销毁会失效旧请求的提交资格；重建仅重新请求仍属于当前输入的工作。打开系统权限设置时不自动启动提醒，返回后更新授权说明。目的地搜索提供方仍遵循原有首发与 Debug 对照边界。

本机记录继续使用应用私有 SharedPreferences，沿用备份禁用设置。无版本字段的旧记录按 v0 读取，写入时按 v1 标记，保留原有字段布局和有效旧日程。损坏或版本不受支持时保留每类记录的首份原件并提示，列表中其余可读条目继续可用。最近地点保留上限为 10 条，用户可从设置清除记录和副本。首次保存任务与更新最近地点共用一次写入。实时距离只留在内存，以任务 ID 隔离，定位回调仅在状态变化时持久化任务。

到站只消费坐标有效、达到原有精度门槛、位于现有 5 分钟新鲜度窗口且单调时间递增的定位。速度不是首发手动到站条件。自动车站检测、固定日程和旧权限继续留在批准的延期范围。

## ADR-0015：Release 使用独立 Key 启用高德 Android 搜索

- 日期：2026-11-10
- 范围：P0-7 地点搜索与 Release 构建

沿用 ADR-0012 验证过的独立搜索 SDK、用户同意入口和 GCJ-02 坐标转换。Debug 和 Release 共用适配器；Release 的 `ARRIVAL_AMAP_ANDROID_RELEASE_KEY` 绑定正式包名和实际分发签名，独立于本机 Debug Key。未配置 Key 的普通构建保留原搜索来源，签名 Play 工作流要求发布 Key。正式验收需使用真实签名包验证联想、POI、授权、实际网络字段和配额。

## ADR-0016：到站时由前台服务播放闹钟铃声

- 日期：2026-11-11
- 范围：手动启动的到站提醒

真机试用中出现到站通知已显示、铃声未听到的情况，具体设备原因待复测。到站时停止定位，同一个前台服务切换到 `mediaPlayback`，用 `USAGE_ALARM` 循环播放系统默认闹钟铃声，受设备闹钟音量和勿扰规则约束。振动沿用 30 秒间隔，最多十次。播放期间使用部分唤醒锁保障熄屏后的播放和定时结束。

「到站确认」使用独立的默认无声通知类别。旧类别保留在已安装设备的系统设置中。用户可在通知或应用里确认到达，确认、停止、清空或替换任务会释放播放器。五分钟到期后保留待确认任务和通知。媒体播放前台服务声明纳入发布申报，闹钟详情仍通过通知打开。
