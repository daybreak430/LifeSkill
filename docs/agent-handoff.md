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
| 最新产品与发布提交 | `22fec957e9f10ceeab349821e873961c89264700` |
| 最新版本 | `0.15.14` |
| `versionCode` | `101` |
| 标签 | `v0.15.14` |
| Release | [LifeSkill 0.15.14](https://github.com/daybreak430/LifeSkill/releases/tag/v0.15.14) |
| APK 文件名 | `LifeSkill-0.15.14-release.apk` |
| APK 大小 | `18,250,215` bytes |
| APK SHA-256 | `23275733a26ead43a85fdff0530be9ecac94e268d2569976aac5c78f00750c45` |
| SDK | `minSdk 26`、`targetSdk 36`、`compileSdk 37` |

本交接文档可能位于多个仅文档提交之后。查询 `git log v0.15.14..HEAD --oneline` 可确认发布后是否只有文档维护，还是已有新的代码变更。

`0.15.14` 已发布：标签指向 `22fec95`，Release 附件 SHA-256 与本地 APK 一致。本次覆盖升级在同一模拟器上验证，`firstInstallTime` 保持不变。真机与 Android 8–9 的 WebView 仍未验收。

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
| 结构与过程 `structure` | 时间轴、流程图、一个对比卡示例 |
| 数据图表 `data` | 需求预测模板、折线图、柱状图、面积图 |
| 互动练习 `practice` | 随堂答题、知识翻卡（均支持 1–3 级提示阶梯）、拖放排序、可拖拽的短小游戏 |
| 图像与综合 `visual` | 植物细胞图片热点，杠杆实验，以及数据观察、文字、对比和判断题组合 |

模型教学协议当前支持 `text`、`timeline`、`flow`、`compare`、`chart`、`quiz`、`flip`、`sort`、`hotspot`、`experiment`、`game`。实时回复最多 3 个组件；提示词要求通常使用 0–2 个并避免重复表达。组件正文始终保留为降级内容，未知或无效组件不能执行代码或发起业务操作；game.code仅进入受限Worker，无原生宿主接口。

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
| `v0.15.12` | 新增原生杠杆实验台：力与力臂调节、拖动重物、实时平衡结果、共享状态放大、AI配置与实际参数摘要；标记1021追加到visual会话。 |
| `v0.15.13` | 新增短代码小游戏与放大运行器；只保留一个对比演示；重新学习改为居中遮罩loading；强化旧课重学组件选择并做真实模型验证。 |
| `v0.15.14` | 答题与翻卡支持1–3级提示阶梯；任意数据图表可先预测后揭晓；小游戏画布铺满、支持拖拽与自绘样式，修复遮挡与空缺；生成约束禁止换皮选择题。 |

对应提交如下：

| 版本 | 提交 |
| --- | --- |
| `v0.15.4` | `841d06f` |
| `v0.15.5` | `f9569f0` |
| `v0.15.6` | `3587331` |
| `v0.15.7` | `3a72517` |
| `v0.15.8` | `3d9a6cf` |
| `v0.15.9` | `4b1ee9c` |
| `v0.15.10` | `80d39ad` |
| `v0.15.11` | `e480eb8` |
| `v0.15.12` | `71c800e` |
| `v0.15.13` | `bb29bb6` |
| `v0.15.14` | `22fec95` |

## 0.15.14 的验证证据

`./scripts/build-release.sh` 完成构建、zipalign、签名与校验。Android 16 / Pixel 9 Pro AVD 上：

- 构建与 `lintDebug`、单元测试通过；教学相关仪器测试 47 项通过：`TeachingUiTest` 14、`TeachingStorageTest` 11、`AiProtocolTest` 7、`TeachingGameUiTest` 5（含拖拽手势用例）、`TeachingGameStorageTest` 4、`HintLadderTest` 6。
- 人工验证：小游戏说明文字不再被画布遮挡、画布铺满、按钮在底部、点击加分正常；单选三级提示逐级展开且末级按钮消失；翻卡提示在卡片外侧逐级显示。
- 覆盖升级：同一模拟器从 0.15.13 升级到 0.15.14，`firstInstallTime` 保持不变；签名证书 SHA-256 与上一发布版一致（`AD:01:E1:…:0A`）。
- GitHub Release 附件与本地 APK 逐字节一致，SHA-256 为 `23275733…0c45`。

本次未做真机、Android 8–9 WebView 验收，也未用真实模型重新抽样「是否会生成拖拽型而非点击型」的游戏。既有内置「凑出一整个」仍是按钮型演示，未改写。

## 0.15.13 的验证证据

本次发布使用 Android Studio JBR、Android SDK Platform 37 和 Build-Tools 36.0.0，已验证：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.lifeskill.app.TeachingUiTest,com.lifeskill.app.TeachingStorageTest,com.lifeskill.app.AiProtocolTest,com.lifeskill.app.TeachingGameUiTest,com.lifeskill.app.TeachingGameStorageTest com.lifeskill.app.test/com.lifeskill.app.LifeSkillTestRunner
```

- Debug 与 Release 构建、48项单元测试和 lint 通过。
- Android 16 / Pixel 9 Pro AVD 上手动仪器运行 `TeachingUiTest` 14项、`TeachingStorageTest` 11项、`AiProtocolTest` 7项、`TeachingGameUiTest` 4项、`TeachingGameStorageTest` 4项，共40项分组通过（39项批次＋1项杠杆旋转单独复测）。使用手动adb instrumentation保留测试APK，便于取回仅含公开测试内容的截图与提示词；不在用户主设备运行。
- 新增游戏真实按钮触控、完成、重开、返回、错误脚本、无限循环超时、网络和HTML隔离测试；验证真实模型自主选择的530字符游戏能够完成。
- 验证旧配色示例精确清理、用户改写保留；旧纯文字课程走同一提示词并接收原生组件；遮罩loading成功、失败重试、系统返回通过。
- 初次游戏测试有WebView线程访问与窗口移除时序问题，修正测试线程及等待后通过；首次全量39项有1项因重学测试改写了共享演示课失败，改用独立测试课程隔离后复测。随后杠杆旋转用例在组合批次等待，改为在测试线程取Activity、避免UI线程等待生命周期后，单独13.2秒通过。
- DeepSeek Flash四个真实请求正常结束：旧课compare/flow分别4.26秒/3.58秒，明确游戏请求570字符3.19秒，自主选择游戏530字符3.99秒；仅为样本观测。详见[小游戏与旧课程实测](teaching-game-audit-2026-11-21.md)。
- 正式包额外检查已安装旧演示的覆盖升级，原生界面中主对比示例存在、methods/travel两张额外示例不再出现；小游戏只在放大后创建并可操作，加载遮罩已检查真实截图。
- 正式 APK 通过 APK Signature Scheme v2、v3 和 zipalign 校验，签名与上一发布版一致。
- 已从 `0.15.12` 覆盖升级到 `0.15.13`，`firstInstallTime` 保持不变。
- GitHub Release 附件已重新下载并与本地 APK 逐字节比较，SHA-256 一致。

本次没有运行整个 `connectedDebugAndroidTest` 套件，也没有在真机或其他 Android 版本验收。仪器测试可能重置测试设备上的应用包状态，使用已有学习数据的主设备时应避免直接跑全量测试。

发布和验证细节见 [贡献指南](../CONTRIBUTING.md)、[交互教学验证基线](interactive-teaching.md#当前验证基线)。

## 已知边界与限制

- 小游戏为点击与拖动驱动的短回合游戏，单个code最多4000 UTF-16单元，每轮最多一个game；放大才运行，后台/退出/旋转结束当前局，局内状态不持久化。启动/输入超时、消息预算与90秒局长限制由运行器执行。错误脚本可重开或关闭，不把结果当作正式评估。运行器不提供键盘、音频、网络与实时动画帧；`100vh` 在部分系统 WebView 会被解析为 0，页面改用实测视口高度，改动布局时不要退回百分比高度。
- 小游戏不允许生成"按钮选答案/文字归类"的换皮选择题，此类内容应使用随堂题或排序；提示词约束无法在运行时强制判定，仍需抽样检查模型输出。
- 游戏运行测试使用Android 16系统WebView；Android 8–9保留资源/网络限制和Worker超时，原生渲染器无响应终止回调仅Android 10+可用。没有在真机或所有WebView版本验收。
- 老课程不存在创建时间门槛；已加强适配组件的提示词，但不能据四个样本保证任意课程、模型、轮次都生成组件。

- 变量实验台目前仅有理想轻杆模板：力为1–10 N整数，水平力臂1–4 m、步长0.5 m。倾角只作方向示意，不是真实运动仿真；不计杆重、摩擦和振荡。AI配置由原生协议校验。

- 分类与分步推演已按用户要求停用，旧加密快照保留兼容。旧课时名称、目标和消息正文不批量改写，因此旧目标中可能仍有历史组件文字，但卡片不再显示。

- 本次平滑性验证覆盖布局尺寸、实际交互和动画推进，不是帧率基准；模拟器 Debug 渲染统计不能代表真机表现，尚未完成真机帧耗时测量。本次对旧课与小游戏进行了四个真实模型请求；停用组件仍有本机过滤兜底。
- 组件选择只对 DeepSeek Flash 的五个主题做了针对性实测，不能据此宣称所有模型、所有学科和所有课程长度都能稳定选对组件。
- 图片热点目前只支持内置 `plant_cell` 植物细胞模板，其他空间主题不能传入模型图片 URL。
- 演示课和组件遵循用户删除优先。覆盖安装不会自动恢复，必须由用户在「新领域」主动恢复。
- 当前没有 GitHub Actions 等自动发布流水线，构建、签名、覆盖升级、标签和 Release 附件一致性仍需人工验证。
- 正式 APK 沿用本机调试证书（`~/.android/debug.keystore`，证书 SHA-256 `AD:01:E1:…:0A`）。这是历史发布一直使用的证书，换成新的正式证书会让老用户无法覆盖升级，必须由用户明确决定；该文件丢失后也无法再产出可覆盖安装的包。签名密钥、密码、模型 Key 和用户数据不得写入仓库或交接文档。
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
| `docs/teaching-game-audit-2026-11-21.md` | 游戏生成、旧课程选择与运行验证样本 | 全模型或全课程保证 |
| `docs/component-selection-audit-2026-11-21.md` | 有日期的模型实测样本与结论 | 长期稳定的协议规范 |
| `CONTRIBUTING.md` | 构建、测试、Release 和 PR 操作命令 | 产品规则和当前版本快照 |
| `docs/agent-handoff.md` | 当前版本、发布提交、验证证据、已知限制、接手步骤 | 密钥、用户数据、未经验证的长期规则 |
