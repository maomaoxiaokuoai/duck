# NOTICE

duck 以 **GNU General Public License v3.0** 分发，完整条款见仓库根目录的 [LICENSE](LICENSE)。

本文件列出项目使用或打包的第三方组件及其许可证。这些组件**不包含**在本仓库的源码中，
其中打包进 APK 的部分通过 Gradle 依赖（及 Maven 中央仓库中的 AAR）引入。

## 运行期内置的组件

| 组件 | 用途 | 许可证 | 上游 |
| --- | --- | --- | --- |
| yt-dlp | 视频解析与下载引擎 | The Unlicense | https://github.com/yt-dlp/yt-dlp |
| youtubedl-android | yt-dlp 的 Android JNI 封装 | GPL-3.0 | https://github.com/yausername/youtubedl-android |
| FFmpeg | 音视频转码与封装 | GPL-2.0（对应构建配置） | https://ffmpeg.org/ |
| aria2 | 多线程外链下载 | GPL-2.0 | https://github.com/aria2/aria2 |
| Termux 打包脚本 | 交叉编译工具链 | GPL-3.0 | https://github.com/termux/termux-app |

## 源码依赖

| 组件 | 用途 | 许可证 | 上游 |
| --- | --- | --- | --- |
| AndroidX / Jetpack Compose | UI 与生命周期 | Apache-2.0 | https://github.com/androidx/androidx |
| Kotlin / kotlinx.serialization | 语言与序列化 | Apache-2.0 | https://kotlinlang.org/ |
| Material Design 3 / Material Icons | 设计系统与图标 | Apache-2.0 | https://m3.material.io/ |
| Koin | 依赖注入 | Apache-2.0 | https://insert-koin.io/ |
| Room | 本地数据库 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room |
| OkHttp | HTTP 客户端 | Apache-2.0 | https://github.com/square/okhttp |
| Coil | 图片加载 | Apache-2.0 | https://github.com/coil-kt/coil |
| MMKV | 键值存储 | BSD-3-Clause | https://github.com/Tencent/MMKV |
| Accompanist | Compose 扩展 | Apache-2.0 | https://github.com/google/accompanist |
| material-motion-compose | 转场动效 | Apache-2.0 | https://github.com/fornewid/material-motion-compose |
| Monet / Material color utilities | 动态取色算法 | Apache-2.0 | https://github.com/Kyant0/Monet |
| ReadYou | 部分 Compose 组件形态参考 | GPL-3.0 | https://github.com/Ashinch/ReadYou |
| dvd | 部分工具实现参考 | GPL-3.0 | https://github.com/yausername/dvd |
| unDraw | 空状态插画 | unDraw License | https://undraw.co/ |

## 图标与素材

- 应用图标 `artwork/duck.svg` 为本项目自有素材，作为 duck 的品牌标识使用。
- 应用内使用的 Material 图标来自 Apache-2.0 授权的 Material Icons 集合。

## 分发义务提醒

如果你重新分发 duck 或它的修改版（无论是否改名、是否附带二进制），GPL-3.0 要求你：

1. 以同样许可证开放全部源代码，包括你对本项目的修改；
2. 保留本文件与 [LICENSE](LICENSE)；
3. 注明你所做的修改与分发日期；
4. 随二进制附带完整的对应源代码获取方式。

上述条款来自 GPL-3.0 本身，属于法律义务，与本项目是否在 README 中提及无关。
