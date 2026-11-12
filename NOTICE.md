# NOTICE

duck 以 **MIT** 分发，条款见 [LICENSE-MIT](LICENSE-MIT)。

本文件列出项目使用或打包的第三方组件及其许可证。这些组件不包含在本仓库源码中，
通过 Gradle 依赖引入。

## 打包进 APK 的资源

| 组件 | 用途 | 说明 |
| --- | --- | --- |
| `acrawler.js` | 抖音签名计算 | 抖音官方 JSVMP 字节码，见下方说明 |

### 关于 acrawler.js

该文件是从抖音公开网页中提取的 JavaScript（约 70 KB），用于计算抖音接口要求的
请求签名。它不包含任何用户数据，也不在本地持久化任何凭据。

若版权方要求移除，可删除 `app/src/androidMain/assets/douyin/acrawler.js`，
届时抖音解析功能将不可用。

## 源码依赖

| 组件 | 用途 | 许可证 | 上游 |
| --- | --- | --- | --- |
| Kotlin Multiplatform | 跨平台基础 | Apache-2.0 | https://kotlinlang.org/ |
| Compose Multiplatform | UI 框架 | Apache-2.0 | https://www.jetbrains.com/lp/compose-multiplatform/ |
| Material 3 | 设计系统 | Apache-2.0 | https://m3.material.io/ |
| Ktor | HTTP 客户端 | Apache-2.0 | https://ktor.io/ |
| Room | 本地数据库 | Apache-2.0 | https://developer.android.com/room |
| Coil | 图片加载 | Apache-2.0 | https://coil-kt.github.io/coil/ |
| kotlinx.serialization | JSON 解析 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Haze | 模糊效果 | Apache-2.0 | https://github.com/chrisbanes/haze |

## 致谢

界面设计参考了 [gabi](https://github.com/Hotaro26/gabi) 的 Material 3 Expressive 布局。
