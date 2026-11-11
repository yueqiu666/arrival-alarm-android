# 别坐过站

一个 Android 到站提醒 App。坐公交、地铁或火车时，选好目的地和提醒半径，手动开始提醒。手机锁屏或切到其他 App 后，前台定位服务会继续守候，进入提醒范围时通过通知、声音和振动提醒。

## 真机试用

- **本轮小范围测试**使用维护者提供的 `arrival-alarm-0.4.3-debug-amap.apk`，最低支持 Android 8.0。安装后允许精确位置和通知。搜索目的地、确认位置并开始提醒，再锁屏或切到其他 App，观察接近目的地时的表现。
- 测试包可在首页通过「搜索来源」切换到高德。首次切换需要阅读并同意说明。可用「城市+地点」缩小搜索范围，例如「成都市+天府广场站」。
- 反馈时请带上手机型号、Android 版本、App 版本、使用的搜索来源、是否锁屏及实际表现。截图请先遮住详细地址和行程。
- [旧公开试用包 v0.4.2](https://github.com/yueqiu666/arrival-alarm-android/releases/tag/v0.4.2) 仍可在 Releases 中下载。本轮测试请使用上面的 `0.4.3-debug` 包。

`0.4.3-debug` 用于收集不同手机上的使用反馈。正式包的签名、安装升级、高德鉴权和发行渠道验收见 [发布就绪状态](docs/RELEASE_READINESS.md)。

## 项目文档

- [产品目标与边界](docs/PRODUCT.md)
- [当前架构与调用流](docs/ARCHITECTURE.md)
- [工程工作流](docs/WORKFLOW.md)
- [技术决策记录](docs/DECISIONS.md)
- [当前发布就绪状态](docs/RELEASE_READINESS.md)
- [工程复盘与经验](docs/RETROSPECTIVE.md)
- [变更记录](CHANGELOG.md)

主要实现位于 `android-app/`：源码在 `android-app/src/`，纯 Java 核心测试在 `android-app/test/`，构建系统入口是 `android-app/gradlew.bat`；`android-app/build-apk.ps1` 是兼容旧 Debug APK 文件名的快捷入口。

## 构建

仓库只提交可复现构建的源码，不将调试签名或本机生成的 APK 纳入版本控制。最低支持 Android 8.0；构建步骤见 [android-app/README.md](android-app/README.md)。

首次打开后授予精确位置和通知权限。应用只申请位置、前台位置服务、通知、振动和网络权限；不申请后台位置、使用情况访问、通知使用权、精确闹钟或全屏通知。拒绝精确位置或通知后无法开始守候。

## 产品边界与隐私

- 无账号、自建后端、广告或分析 SDK。默认使用 Photon/OpenStreetMap 等现有搜索服务。配置了高德 Android Key 的包可在用户同意后切换到高德搜索，测试者无需填写 Key。
- 提醒、最近地点和设置只保存在本机，可一键清空。
- 输入简称或不完整地点时会显示即时地点候选；已有定位权限时优先尝试附近结果，再显示名称、城市和短地址供确认。
- 地点搜索时，关键词会发送给所选服务。默认搜索可能使用附近坐标，高德 Android 搜索由 App 提供关键词和可选城市。导入经纬度时，设备的系统地理编码服务可能反查地址。提醒任务、最近地点、提醒半径和设置保存在本机。
- 地点可通过系统“分享”、`arrivalalarm://set` 深链或直接粘贴导入；不再注册第三方网站链接入口。
- 从地图链接或分享文本导入坐标时，高德与腾讯的 GCJ-02、百度的 BD-09 会先在本机转换到 WGS-84 再参与距离计算；来源无法识别时不转换，并在确认框里明确提示。
- 不读取其他 App 的页面或剪贴板，不保存通知正文和完整使用记录。
- 厂商省电、强制结束应用、关闭定位、静音/勿扰或地下无定位信号都可能影响提醒。

源码、构建方法和完整限制见 [android-app/README.md](android-app/README.md)。iOS 尚未开发。

## 许可与反馈

- 代码采用 [MIT License](LICENSE)。
- 数据处理方式见 [PRIVACY.md](PRIVACY.md)。
- 请不要在公开 Issue 中提交真实位置、行程、通知正文、API Key 或日志；安全问题见 [SECURITY.md](SECURITY.md)。
- 本项目仍是研发原型，不能用于人身安全、应急或其他要求百分之百可靠的场景。
