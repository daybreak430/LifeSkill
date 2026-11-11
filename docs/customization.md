# 二次开发指南

以下示例沿用现有的 Compose、ViewModel、Repository 和 Room 结构。验证命令及测试选择见[贡献指南](../CONTRIBUTING.md)。

## 接入 OpenAI 兼容的模型服务

1. 先在 App 的「我的 → 设置 → 模型设置 → 自定义」填写服务商的 HTTPS Base URL、模型名和 API Key。Base URL 保留服务所需的路径，例如 `https://example.com/v1`。客户端会拼接 `/chat/completions`，使用 Bearer Key 发送非流式请求。
2. 点「测试连接」，再分别验证 AI 生成课程和结业考试出题。连接成功可以验证基本请求，课程生成和考试还要求模型输出通过本地协议校验。
3. 要将服务做成内置选项，在 [ModelSettings.kt](../app/src/main/java/com/lifeskill/app/ai/ModelSettings.kt) 的 `Provider` 中增加名称、Base URL 和默认模型。[SettingsScreen.kt](../app/src/main/java/com/lifeskill/app/ui/SettingsScreen.kt) 从枚举读取选项，[LocalSettings.kt](../app/src/main/java/com/lifeskill/app/data/LocalSettings.kt) 按服务商保存在本地。
4. 在 [AiGateway.kt](../app/src/main/java/com/lifeskill/app/ai/AiGateway.kt) 的 `requestBody` 里处理新预设，只添加服务需要的参数。保留 HTTPS、禁止重定向和响应校验。需要修改业务输出字段时，同步检查 [AiProtocol.kt](../app/src/main/java/com/lifeskill/app/ai/AiProtocol.kt) 和 [TreeAssessmentProtocol.kt](../app/src/main/java/com/lifeskill/app/ai/TreeAssessmentProtocol.kt) 的提示词、解析及兼容规则。
5. 用 [AiGatewayTest.kt](../app/src/androidTest/java/com/lifeskill/app/AiGatewayTest.kt) 的假连接验证请求、错误映射和截断响应。用 [AiProtocolTest.kt](../app/src/androidTest/java/com/lifeskill/app/AiProtocolTest.kt) 与 [TreeAssessmentTest.kt](../app/src/androidTest/java/com/lifeskill/app/TreeAssessmentTest.kt) 验证业务响应。测试数据使用占位 Key。

## 增加证书主题

1. 在 [CertificateStyle.kt](../app/src/main/java/com/lifeskill/app/model/CertificateStyle.kt) 新增 `CertificateTheme`，再调整 `forTree` 中课程图标与主题的映射。主题名、徽记名和角纹编号共同组成已签发档案的样式 ID，已有标识和解码规则需要保留。
2. 在 [CertificateCard.kt](../app/src/main/java/com/lifeskill/app/ui/CertificateCard.kt) 为新主题增加配色和角纹绘制。证书大图和展柜缩略图共用配色及边框绘制，可分别检查两种尺寸下的标题、头像和装饰对比度。
3. 用一棵匹配图标的新树领取证书，再打开旧证书核对样式。改树名、换头像或重考后，已签发证书仍应读取 [CertificateRepository.kt](../app/src/main/java/com/lifeskill/app/data/CertificateRepository.kt) 中冻结的档案。可以扩展 `MvpFlowTest` 的主题用例。

## 修改结业考试通过门槛

[TreeAssessment.kt](../app/src/main/java/com/lifeskill/app/model/TreeAssessment.kt) 的 `EXAM_PASS_SCORE` 是当前 60 分门槛。考试结果页、结业考试通过统计、课后复习入口和证书领取都会读取它。改动后用新分数线的前一分、临界分和后一分验证这些行为，并同步检查 [AssessmentMedallions.kt](../app/src/main/java/com/lifeskill/app/ui/AssessmentMedallions.kt) 的分档以及[用户指南](user-guide.md)中的说明。

如果 Fork 要支持覆盖升级，先决定旧试卷和已签发证书怎样认定。当前实现用通过门槛计算现存试卷状态，旧版证书的首次导入也按通过分数查询。已有独立证书档案继续保留。希望保留旧门槛下的考试通过和领取资格时，需要相应的兼容规则与迁移测试。

## 让 Fork 版与原版并存

在 [app/build.gradle.kts](../app/build.gradle.kts) 给 Fork 设置独立的 `applicationId`，可继续使用现有源码 `namespace`。修改 [strings.xml](../app/src/main/res/values/strings.xml) 中的 `app_name` 区分桌面名称，按实际授权范围使用图标和头像素材。先构建 Debug 包，确认它可与原版同时安装。独立应用拥有自己的本地数据、Keystore 和模型设置。

发布 Fork 时用自己的签名密钥，并在后续版本保持应用 ID、签名和递增的 `versionCode`，便于用户覆盖升级。签名私钥及密码保存在本机或 CI 密钥配置中。
