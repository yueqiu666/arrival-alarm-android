# Changelog

本文件记录对用户、发布流程或工程维护有意义的变更。格式参考 Keep a Changelog；版本号以实际发布产物和 Release 标签为准。

## [Unreleased]

### Added

- 首页加入站台场景图和深色模式，最近地点可从首页快速选择。
- 支持在配置了对应 Key 的 Debug 和 Release 构建中，经用户同意后切换到高德 Android 地点联想和搜索。
- 建立项目级 Agent 指南以及产品、架构、工作流、技术决策和复盘文档。
- 为 Android 工程建立固定版本的 Gradle Wrapper、AGP、JUnit 接入、Bundletool AAB 校验、依赖锁和依赖校验元数据。
- 增加普通 Release 检查与受保护签名构建的 GitHub Actions 工作流。

### Changed

- 单结果、多结果和联想候选使用统一的地点确认界面，取消后可继续选择。
- 守候中断后可手动恢复同一任务，提醒通知默认隐藏目的地。
- 到站判定检查定位精度、新鲜度和顺序。提醒声振由系统通知渠道控制。
- 在根 README 中增加项目文档导航和源码入口说明。
- 正式 Application ID 确定为 `com.navobell.arrivalalarm`；Debug 变体使用独立的 `.debug` 后缀。
- Release 构建启用 R8、资源压缩、Lint 门禁和环境变量签名；高德 Web Key 改为构建时注入，不再保留源码占位密钥。
- 原手工 Debug APK 脚本改为调用项目 Gradle Wrapper，避免维护第二套编译链。

## 维护规则

- 在开发过程中把变更写入 `[Unreleased]`。
- 发布时把已发布条目移动到 `## [x.y.z] - YYYY-MM-DD`。
- 只记录已实现或已发布的事实，不把计划写成完成项。
- 安全修复避免披露可被直接利用的细节；需要时链接独立安全公告。
