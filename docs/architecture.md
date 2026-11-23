# 架构与代码导航

LifeSkill 是单模块原生 Android 应用。Kotlin 和 Compose 负责客户端，Room 保存本地记录。AI 功能由客户端直接访问用户选择的模型服务。本页描述长期结构；当前版本、发布提交、验证证据和临时限制见[Agent 交接基线](agent-handoff.md)。

## 启动与数据流

[MainActivity](../app/src/main/java/com/lifeskill/app/MainActivity.kt) 挂载 Compose。[LifeSkillApplication](../app/src/main/java/com/lifeskill/app/LifeSkillApplication.kt) 的 `AppContainer` 负责创建 Room、加密组件、模型网关和各个仓库。[LifeSkillApp](../app/src/main/java/com/lifeskill/app/ui/LifeSkillApp.kt) 根据路由显示页面和首页底栏。

```text
用户操作 → Compose 页面 → ViewModel → Repository → Room
                                               └→ AiGateway → 模型服务
页面刷新 ← Compose 观察状态 ← ViewModel ← Repository 读取结果
```

- `ui` 渲染页面、转发交互并管理页面状态。持久化操作经仓库执行。
- `model` 定义领域数据和可独立验证的规则，如前置图、有效点亮、考试结果和证书样式。
- `data` 管理 DAO、加密存储和写入事务。模型响应在事务外请求，写入时重新校验本地条件。
- `ai` 管理服务商设置、Chat Completions 请求、提示词和结构化响应校验。

## 从功能找代码

| 功能 | 入口文件 |
| --- | --- |
| 首页、导航和「学习 / 我的」 | [LifeSkillApp.kt](../app/src/main/java/com/lifeskill/app/ui/LifeSkillApp.kt)、[ViewModels.kt](../app/src/main/java/com/lifeskill/app/ui/ViewModels.kt) |
| 课程、先修关系和课程大纲 | [LibraryRepository.kt](../app/src/main/java/com/lifeskill/app/data/LibraryRepository.kt)、[Library.kt](../app/src/main/java/com/lifeskill/app/model/Library.kt)、[SkillMapLayout.kt](../app/src/main/java/com/lifeskill/app/model/SkillMapLayout.kt)、[SkillTreeScreen.kt](../app/src/main/java/com/lifeskill/app/ui/SkillTreeScreen.kt) |
| AI 生成、草稿确认、课时教学和重学遮罩 | [ConversationRepository.kt](../app/src/main/java/com/lifeskill/app/data/ConversationRepository.kt)、[AiProtocol.kt](../app/src/main/java/com/lifeskill/app/ai/AiProtocol.kt)、[ChatScreen.kt](../app/src/main/java/com/lifeskill/app/ui/ChatScreen.kt)、[RestartLoading.kt](../app/src/main/java/com/lifeskill/app/ui/RestartLoading.kt) |
| 交互教学与内置系列课 | [TeachingBlocks.kt](../app/src/main/java/com/lifeskill/app/model/TeachingBlocks.kt)、[TeachingBlockProtocol.kt](../app/src/main/java/com/lifeskill/app/ai/TeachingBlockProtocol.kt)、[TeachingContent.kt](../app/src/main/java/com/lifeskill/app/ui/TeachingContent.kt)、[TeachingActiveContent.kt](../app/src/main/java/com/lifeskill/app/ui/TeachingActiveContent.kt)、[BuiltInTeaching.kt](../app/src/main/java/com/lifeskill/app/model/BuiltInTeaching.kt) |
| 模型设置和网络请求 | [ModelSettings.kt](../app/src/main/java/com/lifeskill/app/ai/ModelSettings.kt)、[AiGateway.kt](../app/src/main/java/com/lifeskill/app/ai/AiGateway.kt)、[LocalSettings.kt](../app/src/main/java/com/lifeskill/app/data/LocalSettings.kt) |
| AI 用量与费用 | [ModelUsage.kt](../app/src/main/java/com/lifeskill/app/ai/ModelUsage.kt)、[AiUsageRepository.kt](../app/src/main/java/com/lifeskill/app/data/AiUsageRepository.kt) |
| 结业考试和课后复习 | [TreeAssessment.kt](../app/src/main/java/com/lifeskill/app/model/TreeAssessment.kt)、[TreeAssessmentProtocol.kt](../app/src/main/java/com/lifeskill/app/ai/TreeAssessmentProtocol.kt)、[TreeAssessmentRepository.kt](../app/src/main/java/com/lifeskill/app/data/TreeAssessmentRepository.kt)、[ExamScreen.kt](../app/src/main/java/com/lifeskill/app/ui/ExamScreen.kt) |
| 个人资料、学习统计和证书展柜 | [PersonalScreens.kt](../app/src/main/java/com/lifeskill/app/ui/PersonalScreens.kt)、[PersonalViewModel.kt](../app/src/main/java/com/lifeskill/app/ui/PersonalViewModel.kt)、[ProfileRepository.kt](../app/src/main/java/com/lifeskill/app/data/ProfileRepository.kt)、[CertificateRepository.kt](../app/src/main/java/com/lifeskill/app/data/CertificateRepository.kt) |

参数实验台（杠杆模型）的纯计算与参数校验见[TeachingExperiments.kt](../app/src/main/java/com/lifeskill/app/model/TeachingExperiments.kt)，原生预览、拖动与滑块见[实验台UI](../app/src/main/java/com/lifeskill/app/ui/TeachingExperiments.kt)。

小游戏的模型与预算在 `model/TeachingGames.kt`，原生启动/销毁和WebView能力限制在 `ui/TeachingGames.kt`，共用画布与Worker运行器在 `assets/teaching/game-runtime.html`。代码不进入原生业务层，不注册JS宿主接口；后台与退出终止运行。

## 修改时需要保留的行为

**课程。** 产品按学习方向、课程、课时组织。存储继续使用 `DIRECTION`、`TREE`、`SKILL` 三级标识。Room v9 为方向增加可空的配色字段，升级时现有方向沿用自动配色。前置关系限制在同一棵树内，须保持无环。新连接需满足相邻逻辑层和分叉限制。有效点亮依赖整条前置链，改动前置后要重新计算进度。地图的视觉换行独立于逻辑层级。

**AI 与本地写入。** 模型生成的课程先作为草稿保存，用户确认后才写入课程库。课后练习的题号随消息保存，旧题没有题号时按第 1 题处理。模型判断作答质量，应用校验回复匹配当前题、至少答对两题且最多出三题，并在完成课时的同一事务中复核前置。清空会话或删除归属后，迟到的回复不能重建已删除记录。结业考试由应用核算选择题和总分，模型负责填空判断和反馈。

**用量。** DeepSeek 和 MiniMax 的官方回执转换为统一格式，写入请求用量表。表内保存模型、时间、Token 分项、归属及当次费用估算。Room v6 增加回执总数，兼容仅有总数的回执，旧记录继续按分项求和。先存用量再解析回复正文，避免漏计带回执的补试。联网搜索成功返回结果后保存独立用量记录，汇总时将模型费用与搜索费用相加，并沿用历史搜索记录的单次估算值。搜索证据与对应消息一同加密保存，回复重试复用已有结果。课程生成请求先关联草稿，确认添加时在同一事务内转入课程。用量与对话和证书快照分离，删除课程或清空会话后仍可统计支出。

**数据、加密与迁移。** [LifeSkillDatabase.kt](../app/src/main/java/com/lifeskill/app/data/LifeSkillDatabase.kt) 使用 Room v13，保留 v1 到 v13 的逐版本自动迁移和[导出 schema](../app/schemas/com.lifeskill.app.data.LifeSkillDatabase)。v12 在消息上记录发出时的深度思考选项，旧消息按关闭读取。v13 增加可空的加密教学组件和互动状态，旧消息按无组件读取。[SecretBox.kt](../app/src/main/java/com/lifeskill/app/data/SecretBox.kt) 通过 Android Keystore 加密用户内容和 API Key。索引需要的标识、关系和状态独立保存。系统备份与设备迁移的数据提取已关闭。

**交互教学。** 原生组件由模型的可选 `blocks` 驱动，正文始终作为独立可读的降级内容。纯领域状态转换与仓库事务负责校验选择、预测、固定计算模板和互动反馈，UI 不直接写数据库。随堂组件不能点亮课时或绕过正式练习。内置「交互教学体验」按四类学习任务组织，同类组件共用一个会话和稳定分类 ID，每个组件保留独立安装标记；升级只向对应会话追加缺少的组件，不覆盖编辑或复活删除。分类与分步推演已停用，统一可用性门槛过滤展示、AI输入输出和互动请求；旧快照保留兼容，不迁移删除。变量实验由 `TeachingExperiments.kt` 中的纯计算与参数校验驱动，UI只管理预览；单字段动作经仓库事务合并并写入已有加密JSON，不新增数据库列。协议、迁移和扩展步骤见[交互教学](interactive-teaching.md)。

演示课程被删除后保持删除状态。用户可在「新领域」主动选择「恢复演示课」；仓库只清除该内置课程自己的安装标记并重建四个分类会话，其他方向、课程、进度、对话和证书不参与删除或覆盖。

**证书。** 正式证书是领取时冻结的课程名、昵称、头像、主题和领取日期。证书档案独立于课程，删除课程后仍可查看。隐藏只影响展柜展示，销毁则替换档案内容并保留禁止重签的标记。示例证书带展示日期，可隐藏和销毁。课程列表的「已领证书」与已领取证书统计均基于未销毁的正式证书。

业务细节见 [MVP 技术设计](mvp.md)，扩展步骤见[二次开发指南](customization.md)。

**对话附件。** Room 保存加密的附件元数据和分块原件，附件外键随所属消息删除。预览在本机读取。内置 DeepSeek Flash 和 MiniMax M3.1 Flash 接收缩放后的图片，文档在本机提取有限长度的正文，PDF 使用 PDFBox。新课规划与确认生成复用本轮近期上传的附件，提取或请求预算导致正文不完整时标记为 `partial`。Room v11 为课时增加可空的加密 `sourceNotes`，保存生成时整理的章节与事实要点，供教学和课后练习使用。清空生成对话会删除原附件，课时要点随课程保留，旧课时无此字段时沿用原教学逻辑。

重新学习状态由ChatViewModel在请求排队前标记restarting（含重试），ChatScreen显示独立遮罩模态加载而隐藏消息流中的等待横条。成功/失败统一收敛状态；返回离开页面，不只隐藏工作中的遮罩。所有课程均调用同一tutorPrompt，无创建时间或课程版本门槛。

WebView渲染器异常处理参考[Android官方管理WebView对象](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview)与[WebViewRenderProcessClient](https://developer.android.com/reference/android/webkit/WebViewRenderProcessClient)。Worker隔离与网络/文件/宿主接口限制共同构成此处的运行边界。
