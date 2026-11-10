# LifeSkill

LifeSkill 是一款原生 Android 技能树应用。按学习方向组织技能树和技能点，用地图查看前置关系及点亮进度。可以手动整理技能库，也可以请 AI 生成结构或在单个技能点内进行文字学习。

## 安装与使用

1. 安装 `release/LifeSkill-0.2.0-debug.apk`。支持 Android 8.0（API 26）及以上。
2. 从首页选择方向，新增技能树与技能点。新增或编辑时可以手选图标，也可以请 AI 从图标库推荐。前置连线用于提示学习顺序，点圆形节点可查看完整名称和技能说明。
3. 使用 AI 前，打开「模型设置」，选择 DeepSeek 或 MiniMax，填入对应的 API Key。也可选「自定义」，填写 OpenAI 兼容服务的 HTTPS Base URL、模型名和 Key。「测试连接」验证当前表单，「保存」将其设为当前模型。
4. 从首页、方向或技能树进入 AI 生成。AI 会在目标明确后直接新增内容，结果消息可跳转到对应项目。
5. 在技能详情选择「学习技能」进入 AI 教学，或选择「手动点亮」。讲解支持 Markdown、表格和简易流程图。可以主动「检验掌握」，也可由老师适时发起练习。选择题直接点击选项，通过后自动点亮。

技能库、学习进度和聊天记录保存在应用本地。AI 对话将当前任务所需的内容发送给所选模型服务，使用该服务商账户的额度。卸载或清除应用数据会删除本地记录。

## 本地构建

使用 Android Studio 打开仓库，安装 Android SDK Platform 37、Build Tools 36.0.0，并选用 Studio 内置 JDK。命令行在已配置 `ANDROID_HOME` 和 `JAVA_HOME` 的环境中运行。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

连接设备或启动模拟器后，可运行端到端测试。测试使用可控 AI 响应，无需 API Key。

```sh
./gradlew :app:connectedDebugAndroidTest
```

## 项目结构

- `model` 定义技能层级、依赖图和掌握检查规则。
- `data` 负责 Room、事务、加密与仓库。
- `ai` 负责模型接入和结构化回复校验。
- `ui` 包含 Compose 页面和 ViewModel。

持久化和 AI 写入决策见 [MVP 技术设计](docs/mvp.md)。
