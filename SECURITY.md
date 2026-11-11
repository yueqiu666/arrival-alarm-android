# Security Policy

## Reporting a vulnerability

请不要在公开 Issue 中披露可被利用的漏洞、API Key、签名文件、真实位置、行程或通知正文。

在仓库尚未配置私密漏洞报告渠道前，请只提交不含敏感细节的公开 Issue，说明“需要安全联系渠道”。维护者提供私密渠道后再发送复现步骤。

## Secrets and signing

- 不要提交 `.env`、`local.properties`、Keystore、证书、私钥、APK 或 AAB。
- 高德 Android 测试 Key 通过 Git 忽略的 `android-app/local.properties` 配置。Release Key 从 `ARRIVAL_AMAP_ANDROID_RELEASE_KEY` 读取。可选的高德 Web 服务 Key 通过 `ARRIVAL_AMAP_WEB_KEY` 注入。具体配置见 [Android 构建说明](android-app/README.md#高德-android-搜索)。
- 正式发行必须使用与本项目调试证书完全分离的发行签名，并由发布者安全保管。

## Scope

当前版本是研发原型。后台定位和到站提醒可能受系统省电、权限、网络、定位信号、静音/勿扰或强制结束应用影响，不应被用于人身安全或应急用途。
