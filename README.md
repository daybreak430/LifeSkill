# LifeSkill

LifeSkill 是一款原生 Android 学习应用。你可以按学习方向整理课程，也可以让 AI 根据学习目标和已有基础规划课程。沿课程大纲逐节学习、完成练习，通过结业考试后领取证书。

## 安装

LifeSkill 支持 Android 8.0（API 26）及以上。当前源码版本为 0.14.23，本地签名安装包路径为 `release/LifeSkill-0.14.23-release.apk`。已发布的安装包可在 [GitHub Releases](https://github.com/daybreak430/LifeSkill/releases) 下载，克隆仓库后也可以自行构建 Debug 安装包。

手动管理课程无需模型账户。要使用 AI 功能，在「我的 → 设置 → 模型设置」填入自己的服务商 API Key。自定义服务使用 HTTPS Base URL、模型名和 API Key。详见 [使用指南](docs/user-guide.md)。

## 从源码运行

用 Android Studio 打开仓库根目录。通过 SDK Manager 安装 Android SDK Platform 37.0、Build-Tools 36.0.0 和 Platform-Tools。使用 Android Studio 内置 JDK 作为 Gradle JDK。当前构建已在 JBR 25.0.3 上验证，源码兼容级别设为 Java 17。Gradle 由仓库中的 Wrapper 提供。

在命令行构建前，将 `ANDROID_HOME` 指向本机 Android SDK，`JAVA_HOME` 指向 Gradle 使用的 JDK，并将 SDK 的 `platform-tools` 加入 `PATH`。连接 Android 设备或启动模拟器后，在仓库根目录运行以下命令。Windows 上用 `gradlew.bat`。

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug 构建使用本机调试签名。如果设备已装同包名、不同签名的版本，可用独立模拟器运行，或按[二开指南](docs/customization.md)为 Fork 设置独立应用 ID。

## 验证改动

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条命令需要已连接的测试设备或模拟器。仪器测试使用本地数据库和可控的 AI 响应，无需 API Key。建议使用专门的测试设备或模拟器。构建、选测和提交说明见[贡献指南](CONTRIBUTING.md)。

## 开发文档

- [架构与代码导航](docs/architecture.md)覆盖页面入口、数据流、关键文件和持久化约束。
- [二次开发指南](docs/customization.md)包含模型接入、证书主题、验收规则和 Fork 配置。
- [MVP 技术设计](docs/mvp.md)记录完整业务规则和实现决策。
- [第三方依赖与素材声明](THIRD_PARTY_NOTICES.md)列出主要依赖及其许可来源。

课程、学习进度、聊天记录、个人资料和证书保存在设备上。调用 AI 时，当前任务所需内容会发送给所选模型服务，并使用该服务商账户的额度。卸载或清除应用数据会删除本地记录。
