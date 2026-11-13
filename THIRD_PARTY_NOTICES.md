# 第三方依赖与素材声明

下表列出当前主要运行时依赖。依赖版本以 [app/build.gradle.kts](app/build.gradle.kts) 和 Gradle 实际解析结果为准。

| 组件 | 用途 | 许可来源 |
| --- | --- | --- |
| Kyant Backdrop 1.0.6、Shapes 1.2.0 | 液态玻璃底栏及底层形状支持 | 发布物 POM 标记为 Apache-2.0，仓库保留[许可证副本](app/src/main/assets/licenses/kyant-apache-2.0.txt) |
| commonmark-java、commonmark-ext-gfm-tables 0.32.0 | 教学消息的 Markdown 和表格解析 | BSD-2-Clause，[版权与许可证原文](docs/licenses/commonmark-bsd-2-clause.txt)来自发布 JAR |
| AndroidX Activity、Compose、Material 3、Lifecycle、Room | 界面、状态和本地存储 | 发布物采用 Apache-2.0，具体声明随各 AndroidX 发布物提供 |
| PdfBox-Android | 读取 PDF 文字 | [Apache-2.0](https://github.com/TomRoush/PdfBox-Android/blob/master/LICENSE) |
| Google ML Kit 中文文字识别 | 在设备上识别附件图片中的文字 | [ML Kit 使用条款](https://developers.google.com/ml-kit/terms) |
| Kotlin 标准库、kotlinx.coroutines | Kotlin 运行时和协程 | 发布物采用 Apache-2.0，具体声明随发布物提供 |

以下命令显示发布版本的直接和传递依赖。

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

分发 APK 前，按实际打包的版本核对并保留适用的版权和许可声明。表中的第三方许可适用于对应组件。LifeSkill 源码及应用名称、应用图标、启动标记、12 款内置头像的授权范围待项目维护者在首次公开前确认。
