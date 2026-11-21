# Agent 交接基线

本页是 Agent 接手项目时的当前状态快照，不是长期产品规范。长期协作规则以 [AGENTS.md](../AGENTS.md) 为准，架构、教学协议和业务规则分别链接到对应文档。若 `git log` 已超出本页记录，先审阅新增提交和相关文档，再更新本页后继续开发。

## 接手前先核对

在仓库根目录执行：

```sh
git status --short
git branch --show-current
git log -10 --oneline --decorate
git rev-parse HEAD
git tag --sort=-v:refname | head
gh release view --json tagName,isDraft,isPrerelease,url,assets
```

工作区必须干净。若本页的发布基线与 `HEAD` 不一致，以 GitHub Release、标签指向和构建产物为准，并检查 `git log <已记录发布提交>..HEAD`。

## 当前基线与发布

| 项目 | 当前值 |
| --- | --- |
| 快照日期 | 2026-11-21，Asia/Shanghai |
| 分支 | `main` |
| 最新产品与发布提交 | 见 `v0.15.11` 标签指向的源码提交 |
| 最新版本 | `0.15.11` |
| `versionCode` | `98` |
| 标签 | `v0.15.11` |
| Release | [LifeSkill 0.15.11](https://github.com/daybreak430/LifeSkill/releases/tag/v0.15.11) |
| APK 文件名 | `LifeSkill-0.15.11-release.apk` |
| APK 大小 | `18,196,888` bytes |
| APK SHA-256 | `e4af0b4804d01f1d1e2b3f75273cccb6f2920c20b23f4fd20a8ade1db827990f` |
| SDK | `minSdk 26`、`targetSdk 36`、`compileSdk 37` |

本交接文档可能位于多个仅文档提交之后。查询 `git log v0.15.11..HEAD --oneline` 可确认发布后是否只有文档维护，还是已有新的代码变更。

`release/*.apk` 被 Git 忽略，克隆仓库后本地不保证存在 APK。需要安装时从 GitHub Release 下载，或按 [贡献指南](../CONTRIBUTING.md#构建发布包) 从同一源码提交重建、签名并校验。

## 产品现状

LifeSkill 是单模块原生 Android 学习应用，使用 Kotlin、Jetpack Compose、ViewModel、Repository 和 Room。主要流程如下：

- 学习按「方向 → 课程 → 课时」组织。课时支持同树先修关系、分叉上限、解锁、学习、课后练习、重新学习和对话导出。
- AI 生成课程先进入对话草稿，用户确认方向后才写入课程库。DeepSeek、MiniMax 和自定义 OpenAI 兼容服务可填写用户自己的 Key；请求使用 HTTPS，拒绝重定向。图片识图使用内置多模态模型，文档与 PDF 在本机提取后发送必要正文。
- AI 老师支持 Markdown、表格、Mermaid 原生流程图、附件、联网搜索、深度思考和用量统计。迟到回复不能在记录删除后重建内容。
- 结业考试固定为 10 道题，60 分通过；通过后可领取冻结快照证书，并体验「查漏补缺」「费曼复述」。
- 用户内容、API Key、对话、附件、教学状态和证书档案通过 Android Keystore 加密。系统备份与设备迁移的数据提取已关闭。
- Room 当前版本为 v13，逐版本迁移和导出 schema 必须保留。

完整业务规则见 [MVP 技术设计](mvp.md)，代码入口和持久化约束见[架构与代码导航](architecture.md)。

## 交互教学现状

「新领域 → 交互教学体验」是四类离线示例课程，不是十节独立课时：

| 稳定分类 | 已有内容 |
| --- | --- |
| 结构与过程 `structure` | 时间轴、流程图、蓝金／紫青／玫瑰绿三种对比卡 |
| 数据图表 `data` | 需求预测模板、折线图、柱状图、面积图 |
| 互动练习 `practice` | 随堂答题、知识翻卡、拖放排序 |
| 图像与综合 `visual` | 植物细胞图片热点，以及数据观察、文字、对比和判断题组合 |

模型教学协议当前支持 `text`、`timeline`、`flow`、`compare`、`chart`、`quiz`、`flip`、`sort`、`hotspot`。实时回复最多 3 个组件；提示词要求通常使用 0–2 个并避免重复表达。组件正文始终保留为降级内容，未知或无效组件不能执行代码或发起业务操作。

新增同类组件时追加到现有分类会话并使用新的安装标记；只有新的交互类别才新增课时。不得为同类组件重复建节点，不得覆盖用户编辑，也不得自动复活已删除的课程或组件。用户可以从「新领域」主动恢复整门演示课。详细协议、状态机、稳定 ID 和扩展步骤见[交互教学与内置系列课](interactive-teaching.md)。

## 最近演进

| 版本 | 主要变化 |
| --- | --- |
| `v0.15.4` | 支持用户从「新领域」主动恢复已删除演示课，同时保留其他学习记录。 |
| `v0.15.5` | 图表近全屏沉浸展示、翻卡按钮与三维翻转、按住拖放排序、Lucide 图形按钮；组件分类收纳继续收敛。 |
| `v0.15.6` | 提示词按课程主题选择组件，避免统一模板化输出。 |
| `v0.15.7` | 完善分步推演推进和主题组件选择，并记录五主题 DeepSeek Flash 实测。 |
| `v0.15.8` | 分类题答对后仍可重新选择；修正胶囊选中态与宽度问题。 |
| `v0.15.9` | 分类胶囊文字垂直居中；提交错误或展开答案后仍可改选，改选会退出答案展开并重新启用提交；选中对勾不占内容宽度。 |
| `v0.15.10` | 热点小标记与独立触控框、热点适用性提示词、稳定翻卡高度、组件尺寸和选中颜色过渡。 |
| `v0.15.11` | 停用分类和分步推演；移除演示安装与渲染、过滤 AI 输入输出、拒绝迟到互动；旧快照保留。 |

以上至 `v0.15.9` 对应提交依次为 `841d06f`、`f9569f0`、`3587331`、`3a72517`、`3d9a6cf`、`4b1ee9c`。

## 0.15.11 的验证证据

本次发布使用 Android Studio JBR、Android SDK Platform 37 和 Build-Tools 36.0.0，已验证：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lifeskill.app.TeachingUiTest,com.lifeskill.app.TeachingStorageTest,com.lifeskill.app.AiProtocolTest
./gradlew :app:assembleRelease
```

- Debug 与 Release 构建、39项单元测试和 lint 通过。
- Android 16 / Pixel 9 Pro AVD 上 `TeachingUiTest` 12项、`TeachingStorageTest` 10项、`AiProtocolTest` 7项，共29项测试分组通过。最后一轮28项通过，新增旧独立课时用例因fixture复制了主键、短名未加密而失败；修正fixture后该用例单独复测通过。
- 新增旧消息隐藏停用卡片但保留其他组件的 UI 测试；验证旧快照加密存储不被重写、初始化不重建停用示例、仓库拒绝停用组件动作、新 AI 回复及历史上下文过滤。
- 正式 APK 通过 APK Signature Scheme v2、v3 和 zipalign 校验，签名与上一发布版一致。
- 已从 `0.15.10` 覆盖升级到 `0.15.11`，`firstInstallTime` 保持不变。
- GitHub Release 附件已重新下载并与本地 APK 逐字节比较，SHA-256 一致。

本次没有运行整个 `connectedDebugAndroidTest` 套件，也没有在真机或其他 Android 版本验收。仪器测试可能重置测试设备上的应用包状态，使用已有学习数据的主设备时应避免直接跑全量测试。

发布和验证细节见 [贡献指南](../CONTRIBUTING.md)、[交互教学验证基线](interactive-teaching.md#当前验证基线)。

## 已知边界与限制

- 分类与分步推演已按用户要求停用，旧加密快照保留兼容。旧课时名称、目标和消息正文不批量改写，因此旧目标中可能仍有历史组件文字，但卡片不再显示。

- 本次平滑性验证覆盖布局尺寸、实际交互和动画推进，不是帧率基准；模拟器 Debug 渲染统计不能代表真机表现，尚未完成真机帧耗时测量。本次没有重新请求真实模型做输出抽样；停用行为有本机协议过滤兜底。
- 组件选择只对 DeepSeek Flash 的五个主题做了针对性实测，不能据此宣称所有模型、所有学科和所有课程长度都能稳定选对组件。
- 图片热点目前只支持内置 `plant_cell` 植物细胞模板，其他空间主题不能传入模型图片 URL。
- 演示课和组件遵循用户删除优先。覆盖安装不会自动恢复，必须由用户在「新领域」主动恢复。
- 当前没有 GitHub Actions 等自动发布流水线，构建、签名、覆盖升级、标签和 Release 附件一致性仍需人工验证。
- 正式 APK 仍需沿用既有发布证书；签名密钥、密码、模型 Key 和用户数据不得写入仓库或交接文档。
- 用户要求的组件生成验证应使用 macOS 钥匙串中的 DeepSeek 或 MiniMax 凭据。不要擅自改用本地千问模型，也不要输出 Key 内容。

## 建议的接手顺序

1. 阅读 `AGENTS.md`，再阅读本页和当前任务涉及的产品文档。
2. 查看 `git status`、`git log`、标签、Release 和源码版本，确认没有待提交改动或未发布修复。
3. 按功能从 [架构与代码导航](architecture.md) 定位 UI、ViewModel、Repository、Room 和协议边界。
4. 先复现并记录当前行为；涉及 Compose 交互时在专用模拟器验证触控、滚动、键盘、系统返回和不同字号。
5. 小步实现，补充领域、协议、存储或仪器测试，并同步更新对应长期文档和本交接页。
6. 使用中文提交信息。新版本按贡献指南构建、签名、验证覆盖升级，发布 GitHub Release，并核对附件 SHA-256。
7. 完成或移交前重新检查工作区，确保本页的版本、提交、验证结果、已知限制和未完成项与仓库一致。

## 文档职责

| 文档 | 应放内容 | 不应放内容 |
| --- | --- | --- |
| `AGENTS.md` | 长期协作规则、代码与 UI 原则、测试门槛、发布规范、交接规则 | 频繁变化的 commit、APK 哈希和临时待办 |
| `README.md` | 产品入口、安装、源码运行、验证命令、文档导航 | 内部状态机细节和长验证日志 |
| `docs/architecture.md` | 模块边界、数据流、关键文件、持久化和迁移约束 | 当前任务清单和版本流水账 |
| `docs/interactive-teaching.md` | 教学协议、组件行为、配置分类、扩展步骤和验收边界 | 与组件无关的模型或发布细节 |
| `docs/mvp.md` | 已确认的业务规则和实现决策 | 每次发布的临时验证记录 |
| `docs/user-guide.md` | 面向用户可执行的入口、操作与结果 | 内部代码路径和 Agent 待办 |
| `docs/customization.md` | 模型、证书、Fork 和扩展的开发步骤 | 当前发布基线和未完成问题 |
| `docs/component-selection-audit-2026-11-21.md` | 有日期的模型实测样本与结论 | 长期稳定的协议规范 |
| `CONTRIBUTING.md` | 构建、测试、Release 和 PR 操作命令 | 产品规则和当前版本快照 |
| `docs/agent-handoff.md` | 当前版本、发布提交、验证证据、已知限制、接手步骤 | 密钥、用户数据、未经验证的长期规则 |
