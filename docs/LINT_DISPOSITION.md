# Release Lint 处置记录

- 基线日期：2026-09-17
- 验证命令：`android-app/build-release.ps1 -SdkRoot C:\temp\codex-android-sdk`
- 等价命令（关闭构建缓存，避免陈旧产物）：`gradlew clean testDebugUnitTest lintRelease bundleRelease validateReleaseBundle --no-build-cache --no-daemon`
- 结果：`BUILD SUCCESSFUL`；`verifyReleasePermissionScope` 通过；`testDebugUnitTest` 通过；`lintRelease` 阻断错误 0 个
- 警告数量：**35 → 5**（先处置 20 条，再删除第三方链接入口后减少 10 条）
- 报告位置：`%TEMP%\arrival-alarm-gradle-build\reports\lint-results-release.html` 与同名 `.sarif`

历史记录中的“34 条”与“35 条”不一致，属于旧报告未统一；以本文件重新生成的 35 条为处置基线。

## 已修复（30 条）

| 规则 | 数量 | 处置 |
| --- | ---: | --- |
| `ObsoleteSdkInt` | 6 | 删除 `minSdk = 26` 之下永远为假的 SDK 判断（`MainActivity`、`ReminderRuntimeCoordinator`、`TrackingService`）；`mipmap-anydpi-v26` 合并为 `mipmap-anydpi` |
| `UnusedResources` | 2 | 删除未被引用的 `deep_green_dark`、`white` |
| `MonochromeLauncherIcon` | 2 | 两个自适应图标补充 `<monochrome>`，支持 Android 13 主题图标 |
| `InlinedApi` | 1 | `HapticFeedbackConstants.CONFIRM` 增加 API 30 判断，低版本回退 `KEYBOARD_TAP` |
| `ClickableViewAccessibility` | 3 | 补充 `@SuppressLint` 并注明理由：该监听只做按压缩放且始终返回 `false`，点击仍由 View 自身处理，再调用 `performClick` 会造成重复点击 |
| `SetTextI18n` | 4 | 文案改为 `strings.xml` 占位符资源（`meta_awaiting_first_fix`、`meta_distance_remaining`、`meta_waiting_location`、`radius_label`） |
| `UnusedAttribute` | 2 | 删除 Manifest 中重复的 `showWhenLocked` / `turnScreenOn`；`AlarmActivity` 已在代码中按 API 27 与 API 26 分别处理 |
| `IntentFilterUniqueDataAttributes` + `AppLinkWarning` | 10 | 删除第三方网站链接入口，详见下节 |

## 第三方网站链接入口：已删除

`IntentFilterUniqueDataAttributes` 9 条、`AppLinkWarning` 1 条原本指向 `AndroidManifest.xml` 中声明 9 个 `https` 域名（`uri.amap.com`、`ditu.amap.com`、`surl.amap.com`、`map.baidu.com`、`map.qq.com`、`www.12306.cn`、`m.12306.cn`、`v.didi.cn`、`page.udache.com`）并带 `BROWSABLE` 的过滤器。

项目负责人确认后已整体删除，理由如下：

1. `autoVerify` 只对自有域名有效，需要在该域名下发布 `/.well-known/assetlinks.json`。这些第三方域名永远无法通过验证，因此该过滤器达不到“点击链接直接启动本应用”的目标，最好情况只是出现在系统“打开方式”候选里。
2. 目的地精确度来自分享文本或链接中携带的坐标，`ACTION_SEND`、`ACTION_PROCESS_TEXT`、`arrivalalarm://set` 和粘贴都能取得同样的坐标，链接拦截不增加精度。
3. 保留它会让本应用出现在高德、百度、腾讯地图、12306、滴滴链接的打开方式中，并且使任何应用都能用隐式 Intent 拉起 `MainActivity`。

同时删除了因此失效的 `ShareParser.isTravelWebsite` 与 `handleIntent` 中的“检测到出行网站”分支。

保留的入口：`MAIN`/`LAUNCHER`、`SEND`（`text/plain`）、`PROCESS_TEXT`（`text/plain`）、`arrivalalarm://set` 深链。合并后的 Release Manifest 已核对，不再包含任何第三方 `https` 域名。

## 保留（5 条）与理由

### 暂缓：工具链与 API 版本升级（4 条）

| 规则 | 内容 |
| --- | --- |
| `AndroidGradlePluginVersion` | AGP 9.2.1 → 9.4.0 |
| `AndroidGradlePluginVersion` | Gradle 9.4.1 → 9.7.1 |
| `GradleDependency` | `compileSdk` 36 → 37 |
| `OldTargetApi` | `targetSdk` 未指向最新版本 |

发布冻结阶段不升级构建工具链与 `targetSdk`。升级会改变 R8、资源合并和系统行为兼容性，必须作为独立任务完成，并重新执行完整构建、Lint、权限门禁和真机回归。

### 接受：备份规则（1 条）

`DataExtractionRules`：Manifest 已设置 `android:allowBackup="false"`，Android 12 及以上的云备份与设备迁移均已关闭，因此不额外声明 `dataExtractionRules`，避免引入仅 API 31+ 生效的属性。该项按已接受处理，理由如上。

## 环境说明

本次验证前，本机 Android SDK 处于残缺状态（`platforms\android-36` 缺少 `android.jar` 与 `package.xml`，`cmdline-tools\latest\bin` 为空，`platform-tools` 只剩 `adb.exe`），已重新安装 Build-Tools 36.0.0、Platform 36 (revision 2) 与 Platform-Tools 37.0.1，并重新接受 Android SDK 许可。残缺目录以 `.partial-backup` 后缀保留在 SDK 目录内，未删除。

同时发现 Gradle 构建缓存会复用陈旧的资源合并产物：把 `mipmap-anydpi-v26` 合并为 `mipmap-anydpi` 后，带缓存的构建报 `resource mipmap/ic_launcher not found`，使用 `--no-build-cache` 或 `--rerun-tasks` 即恢复正常。判断资源相关构建失败时不能只看一次带缓存的运行结果。
