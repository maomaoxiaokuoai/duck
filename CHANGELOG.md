## v2.0.36 — 2026-11-12

首个基于 Material 3 Expressive 重写的版本。

### 新增

- **抖音解析**：支持视频和图集，走签名请求取无水印直链
- **自动更新**：启动时并发优选 CDN 节点，不直连 github.com
- **纯中文界面**：移除其他语言，首次启动固定中文
- **图集批量下载**：一次保存全部图片到 `Pictures/duck`

### 变更

- 界面基于 gabi 重写为 Material 3 Expressive 风格
- 视频默认保存到 `Movies/duck`，相册可直接看到
- 应用名、图标、包名统一为 `duck` / `com.duck.app`

### 移除

- yt-dlp、gallery-dl、cobalt、NewPipeExtractor 四个下载引擎
- Chaquopy 与打包的 Python 运行时（APK 从 48 MB 降至 5 MB）
- 首次启动引导页
- 播放器 tab 与语音搜索
- Cookies 设置页与 Support 页
- 引擎选择器、Material 表现形状、Home Search Style

### 修复

- 即时下载按钮同时触发预览和下载导致的状态竞争（闪退）
- 启动时主线程阻塞在 Python 初始化（冷启动从 3.9s 降至约 1s）
