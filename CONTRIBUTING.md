# 贡献指南

先按 [README](README.md#从源码运行) 构建并安装 Debug 版。想了解功能对应的文件，可以从[架构与代码导航](docs/architecture.md)开始。

## 提报问题与提交改动

- Issue 请附上应用版本、Android 版本、设备或模拟器型号、复现步骤、预期和实际结果。AI 相关问题可补充服务商和模型名。截图或日志请先移除 API Key、个人资料和真实对话。
- PR 请说明问题、涉及的用户流程和验证结果。界面改动可以附截图或录屏。涉及数据库或持久化格式时，说明旧版本数据如何读取或迁移。
- 改动保持在同一目的内。提交信息使用中文，格式如「修复：保留重考后的证书状态」。AI 协作要求见 [AGENTS.md](AGENTS.md)。

## 选择测试

在仓库根目录运行以下命令。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

在专用模拟器或测试设备上运行仪器测试。可以按类选择与变更相关的用例。以下命令运行模型请求与响应的测试。

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lifeskill.app.AiGatewayTest
```

去掉类筛选参数可运行整套仪器测试。测试运行器使用 [FixtureApplication](app/src/androidTest/java/com/lifeskill/app/Fixtures.kt) 注入内存数据库和脚本化 AI 响应。迁移测试使用独立的临时数据库。测试无需真实 API Key。

| 改动范围 | 相关测试 |
| --- | --- |
| 技能前置、有效点亮、地图和续学 | `RulesTest`、`SkillMapLayoutTest`、`LessonFlowTest`、`RepositoryTest` |
| 模型接入和结构化回复 | `AiGatewayTest`、`AiProtocolTest`、`RepositoryTest` |
| 考试、挑战和发证 | `TreeAssessmentTest`、`MvpFlowTest` |
| 资料、证书档案和数据库迁移 | `PersonalStorageTest`、`MvpFlowTest` |

Compose 改动按影响范围验证触控、滚动、键盘和系统返回。Room 结构变更时，随提交保留 [导出的 schema](app/schemas/com.lifeskill.app.data.LifeSkillDatabase)，并用旧版本数据库样本验证迁移和数据保留。

## 构建发布包

```sh
./gradlew :app:assembleRelease
```

当前仓库的 Release 构建输出 `app/build/outputs/apk/release/app-release-unsigned.apk`。用 Android SDK 的 `apksigner sign` 生成签名包，再用 `apksigner verify --verbose --print-certs` 验证签名并与上一发布包比对证书 SHA-256。覆盖升级需沿用上一发布版的签名证书，并递增 `versionCode`。验证旧包覆盖升级后，将签名包保存为 `release/LifeSkill-<版本>-release.apk`。签名密钥留在本机。

首次公开仓库前，确认项目授权范围，核对[第三方声明](THIRD_PARTY_NOTICES.md)，并检查当前提交和 Git 历史中的密钥、签名文件、真实用户数据及仅供内部使用的内容。发布时也检查 README 中的版本和安装入口。
