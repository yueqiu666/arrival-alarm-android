# Android 原生版

这是“别坐过站”的 Android 原生实现，正式 Application ID 为 `com.navobell.arrivalalarm`，最低 Android 8.0（API 26）。项目使用固定版本的 Gradle/AGP，无 AndroidX 或 Google Play Services 依赖。配置对应变体的高德 Key 后，用户可主动切换到高德搜索。

## 测试包与发布包

| 用途 | 产物 | 验证范围 |
| --- | --- | --- |
| 小范围真机试用 | `0.4.3-debug` APK，包名 `com.navobell.arrivalalarm.debug` | 安装、搜索、确认目的地、锁屏守候和到站提醒 |
| 正式签名验收 | 用目标渠道实际分发的签名包，包名 `com.navobell.arrivalalarm` | 正式签名和升级、R8 与资源压缩后的行为、高德 Release 鉴权 |

小范围测试可直接发送 APK 文件。Google Play 验收应通过 Play 测试渠道安装，以覆盖用户实际收到的包和签名。Debug 与 Release 的包名不同，可并存安装。

## 构建

需要 JDK 17 或更高版本、Android Platform 36 和 Build Tools 36.0.0。Debug 完整检查：

```powershell
.\android-app\build-apk.ps1 -SdkRoot "C:\path\to\android-sdk"
```

脚本会调用项目自带的 Gradle Wrapper，运行核心测试和 Debug Lint，再生成 Debug APK 并复制到已忽略的 `outputs/arrival-alarm-debug.apk`。

Release 工程检查：

```powershell
.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk"
```

Release 默认启用 R8 和资源压缩；脚本会自动规避中文 Windows 用户目录导致的 Gradle 测试进程问题。未设置上传密钥时，AAB 仅用于工程验证，不能上传 Google Play。正式签名从以下环境变量读取：

- `ARRIVAL_UPLOAD_KEYSTORE`
- `ARRIVAL_UPLOAD_STORE_PASSWORD`
- `ARRIVAL_UPLOAD_KEY_ALIAS`
- `ARRIVAL_UPLOAD_KEY_PASSWORD`

四项必须全部设置，且密钥文件和密码不得提交到 Git。设置后运行 `.\android-app\build-release.ps1 -SdkRoot "C:\path\to\android-sdk" -VerifyPlayBundle`，同时检查 AAB 格式和签名。

## 已实现

- 首页用带站台场景图的蓝色主卡显示目的地搜索与当前提醒状态，最近地点按纵向列表展示。支持跟随系统切换浅色和深色模式。
- 原生单页地点输入。输入简称时即时匹配最近地点并异步显示候选。默认查询按已配置的高德 Web 服务、Photon/OpenStreetMap 和系统 `Geocoder` 回退。用户同意切换后，高德 Android 模式独立查询。
- v0.4.3 的联想等待为 80–120 毫秒，旧请求结果不会覆盖新关键词。缓存本机候选并跳过重复候选或状态的布局重建，连续输入时无需反复解析日程与最近地点。
- 适配 Android 15+ 强制 edge-to-edge：标题、设置按钮和滚动内容会使用状态栏、刘海及底部导航栏安全区，不再与系统图标重叠；键盘弹出时使用可调整窗口。
- 高德返回的 GCJ-02 坐标会在本机转换后再参与到站距离计算，避免坐标系偏移直接影响提醒半径。
- 接收地图/打车软件的 `text/plain` 系统分享，以及 `arrivalalarm://set` 深链。
- 识别常见地图 URI、经纬度和被选中的地点文字；高德与腾讯链接的 GCJ-02、百度链接的 BD-09 会先在本机转换成 WGS-84，来源无法识别时保持原值并在确认框标注；外部坐标启动前反查地址并要求用户确认。
- `arrivalalarm://set?name=&lat=&lng=` 由本应用定义，坐标一律按 WGS-84 处理，不做坐标系转换。
- 不再注册第三方网站的 Android 链接入口。第三方域名无法由本应用验证，拦截只会让本 App 出现在系统“打开方式”里，无法做到直接启动；导入目的地统一走系统分享、深链和粘贴。
- Android 前台定位服务；任何一次可靠定位进入或落在预设半径内都会立即触发到站闹钟，不再等待第二次确认。
- 高优先级可点击通知、声音、振动和“我已到达”操作；每 30 秒重复，最多 5 分钟。声振受系统通知渠道设置影响。
- 守候中断后可从首页恢复同一任务。提醒通知默认隐藏目的地，可在设置中开启显示。
- 最近地点、提醒半径、本机清空和隐私说明。

## 隐私

应用声明 `INTERNET` 权限用于地点查询。默认搜索可能向服务发送关键词和附近坐标，高德 Android 搜索由 App 提供关键词和可选城市。导入坐标会调用系统 `Geocoder`。具体数据流见 [隐私政策](../PRIVACY.md)。首发版不申请后台位置、使用情况访问、通知使用权、精确闹钟或全屏通知。

## 限制

- 用户无需配置地图 Key。开发者若有高德 Web 服务 Key，可在构建前设置环境变量 `ARRIVAL_AMAP_WEB_KEY`；密钥会由构建脚本注入 APK，不出现在设置页。
- Photon 公共服务只适合合理用量并可能限流；正式大规模发行应部署自己的 Photon 实例，或使用由开发团队统一管理、绑定应用身份的商业地图服务。
- 长期车站识别、其他 App 信号识别和固定日程自动启动属于后续实验能力，不在首个 Play 发布范围内。
- 厂商省电、强制结束应用、关闭定位、静音/勿扰或地下无信号都可能影响提醒，不能宣传为百分之百触发。
## 高德 Android 搜索

Debug 使用 `android-app/local.properties` 中的 `amap.android.key`。Release 使用构建环境变量 `ARRIVAL_AMAP_ANDROID_RELEASE_KEY`，GitHub 签名工作流从 `play-release` 环境读取同名 Secret。按 [高德 Key 配置说明](https://lbs.amap.com/api/android-sdk/guide/create-project/get-key) 绑定实际安装包的 Application ID 和签名 SHA-1。经 Google Play 分发时使用 Play App Signing 证书。Debug 测试 Key 与 Release Key 分别配置。

安装配置了对应 Key 的 APK 后，在首页点「搜索来源」并同意说明，随后输入地点。输入时调用高德联想，点「搜索」调用高德 POI 搜索。输入 `成都市+天府广场站` 可限定城市。再次点「搜索来源」回到现有服务。高德模式的候选和错误均来自高德，确认流程与现有搜索共用。普通 CI 构建可不配置 Key；签名 Play 工作流要求配置 Release Key。

本机可在专用模拟器安装带测试 Key 的 Debug APK 和对应测试 APK，运行 `adb shell am instrument -w -e amapLiveSearch true com.navobell.arrivalalarm.debug.test/com.example.arrivalalarm.LifecycleInstrumentationRunner`，分别验证联想与 POI 搜索返回成都测试地点。

## 桌面图标素材

桌面图标使用 [Microsoft Fluent Emoji 的 Train 3D 位图](https://github.com/microsoft/fluentui-emoji/blob/1ffb34c752ecf5d402f04cfb4b392c77f57c54bc/assets/Train/3D/train_3d.png)。应用随包保留 [MIT 授权文本](src/main/assets/third_party/fluent_emoji_license.txt)，并使用透明留白适配系统桌面遮罩。
