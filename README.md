# duck

> 一个用 Kotlin + Jetpack Compose 写的 Android 视频/音频下载器：输入链接，选格式，下到本地。

duck 是 **GPL-3.0** 开源项目，面向 Android 7.0（API 24）及以上，底层调用 [yt-dlp](https://github.com/yt-dlp/yt-dlp)
完成实际解析与下载，界面、主题、图标和产品形态由本项目维护。

<p align="center">
  <img src="artwork/icon-512.png" width="128" alt="duck icon">
</p>

---

## 功能

- **一键下载**：粘贴链接即可解析，支持视频、纯音频、封面、字幕等多形态输出
- **格式选择**：视频容器/编码/分辨率、音频编码/码率、字幕语言与嵌入方式全部可选
- **下载队列**：多任务排队、暂停、重试、查看实时日志
- **自定义命令**：把常用 yt-dlp 参数保存成模板，像终端一样直接执行
- **Cookie 管理**：内置 WebView 抓取 Cookie，支持多站点配置，登录态内容可解析
- **播放列表**：解析整张播放列表后按条目勾选
- **Material You**：跟随系统取色的动态主题，支持深色模式与自定义配色
- **多语言**：内置 60+ 语言的界面翻译资源

## 下载安装

所有 APK 均由 GitHub Actions 在云端构建并发布到 [Releases](../../releases)，**不提供本地构建产物**。

每个版本会同时产出 5 个 APK：

| 文件 | 适用设备 | 体积 |
| --- | --- | --- |
| `duck-<版本>-arm64-v8a-release.apk` | 绝大多数手机（2017 年后） | 约 41 MB |
| `duck-<版本>-armeabi-v7a-release.apk` | 老设备（32 位 ARM） | 约 40 MB |
| `duck-<版本>-x86_64-release.apk` | 模拟器 / x86 平板 | 约 42 MB |
| `duck-<版本>-x86-release.apk` | 老模拟器 | 约 41 MB |
| `duck-<版本>-universal-release.apk` | 不确定架构时用 | 约 110 MB |

> 拿不准就下 `universal`，一个包装所有架构，只是体积大。

### 版本说明

每个 Release 的正文会自动从 [CHANGELOG.md](CHANGELOG.md) 里提取对应版本号的
「新增 / 变更 / 修复」小节，所以每个版本的改动都有记录可查。

## 构建

### 推荐：云端构建

本仓库的主构建路径是 GitHub Actions —— 推送即编译，本地不需要 Android SDK：

- **push 到 `main` / PR / 手动触发**：构建全部 ABI 的 release APK，作为 Actions artifact 保留
- **推送 `v*` 标签**：额外发布到 Releases 页面
- 产物：`duck-<version>-<abi>-release.apk`（5 个）
- 流水线会校验签名，未签名的 APK 直接判定失败

### 本地构建（可选，仅用于调试）

仅在你需要本地调试时使用，需自备环境：

| 组件 | 版本 |
| --- | --- |
| JDK | 21 |
| Android SDK | compileSdk 35 / minSdk 24 |
| Gradle | 8.10.2（随 wrapper 提供） |

```bash
git clone <this-repo> duck
cd duck

echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleGenericDebug
```

如需签名，在根目录放 `keystore.properties`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=******
keyAlias=duck
keyPassword=******
```

### 变体

| Flavor | applicationId | 说明 |
| --- | --- | --- |
| `generic` | `com.duck.app` | 默认渠道 |
| `githubPreview` | `com.duck.app.preview` | 可与正式版共存 |
| `fdroid` | `com.duck.app` | 关闭应用内更新入口 |

## 项目结构

```
duck/
├── .github/workflows/       # 云端构建流水线
├── app/                     # 主应用模块
│   ├── src/main/java/com/duck/app/
│   │   ├── ui/              # Compose 界面：下载页、设置、主题、组件
│   │   ├── download/        # 下载执行器
│   │   ├── database/        # Room 数据库与备份
│   │   └── util/            # 偏好设置、文件、通知、yt-dlp 封装
│   ├── src/main/res/        # 图标、字符串（60+ 语言）、主题
│   └── schemas/             # Room 导出的数据库 schema
├── color/                   # Material You 取色库（Monet / HCT）
├── buildSrc/                # 版本号生成
└── artwork/                 # 图标等设计源文件
```

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Room · Koin · OkHttp · MMKV · Coil ·
kotlinx.serialization · youtubedl-android

## 开源说明

- 本项目以 **GNU General Public License v3.0** 发布，完整条款见 [LICENSE](LICENSE)。
- 你可以自由使用、修改、分发本项目，但分发衍生作品时**必须同样以 GPL-3.0 开源**，
  并提供对应的完整源代码。
- 第三方组件的版权与许可证归各自作者所有，完整清单见 [NOTICE.md](NOTICE.md)
  以及应用内「设置 → 关于 → Credits」。
- `duck` 是本项目的名称与图标，代表本项目的构建与发布。

## 致谢

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — 实际的下载/解析引擎，Unlicense
- [youtubedl-android](https://github.com/yausername/youtubedl-android) — yt-dlp 的 Android 封装，GPL-3.0
- [FFmpeg](https://ffmpeg.org/) / [aria2](https://github.com/aria2/aria2) — 转码与外链下载
- [AndroidX](https://github.com/androidx/androidx)、[Kotlin](https://kotlinlang.org/) — 基础框架
- [Material color utilities](https://github.com/material-foundation/material-color-utilities)、[Monet](https://github.com/Kyant0/Monet) — 动态取色
- [Termux](https://github.com/termux/termux-app) — 打包脚本参考

## 许可证

GPL-3.0 © duck contributors
