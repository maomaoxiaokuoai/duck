# Changelog

本文件记录 duck 每个版本的变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

每次发布新版本时：

1. 在 `## [Unreleased]` 下方补充本次改动
2. 发布时把 `[Unreleased]` 改成新版本号并加上日期
3. 同步修改 `app/build.gradle.kts` 里的 `currentVersion` 和 `versionCode`
4. 打 tag 推送，CI 会自动构建全部架构并写入 Release 说明

---

## [2.0.2] - 2026-11-10

### 修复
- 启动画面与自适应图标仍显示海豹：矢量文件 ic_launcher_foreground / ic_launcher_monochrome / 通知图标未更新，之前只替换了 PNG

### 变更
- 关于页移除「GitHub 议题」「赞助」「Project repository」三项
- 移除「界面与交互」设置入口及其页面

---

## [2.0.1] - 2026-11-10

### 修复
- 关于页的 Telegram / Matrix 条目名不副实（标题写着社交频道，链接却指向本项目仓库），合并为一个「项目仓库」入口
- release 构建改为从 CI 环境变量读取签名，修复 1.13.1 只认 keystore.properties 导致 APK 未签名的问题

---

## [2.0.0] - 2026-11-10

### 变更
- 基线切换为上游 Seal 1.13.1（此前基于 2.0.0-alpha.5 的测试版界面）
- 首页恢复为「标题 + 视频链接输入框」的经典布局
- 设置页、导航抽屉、关于页与 1.13.1 完全一致
- 版本号重置为 2.0.0 作为新基线（versionCode 20000000）
- APK 文件名改为 duck-<版本>-<架构>-release.apk，去掉 flavor 段

### 保持
- 包名 com.duck.app、应用名 duck、鸭子图标
- 所有作者信息与链接指向 maomaoxiaokuoai/duck
- 云端构建、签名、全 ABI 分发

---

## [1.1.2] - 2026-11-10

### 新增
- 恢复完整的上游 UI 与功能：赞助页、应用内自更新、Telegram / Matrix 入口、翻译入口、问题反馈页

### 变更
- 作者信息与所有外链改为 maomaoxiaokuoai/duck
- 应用内自更新指向本项目 Releases
- Release 说明不再附带下载说明表格，只保留版本改动

---

## [1.1.1] - 2026-11-10

### 变更
- APK 文件名去掉内部的 generic 渠道标识，改为 duck-<版本>-<架构>-release.apk

---

## [1.1.0] - 2026-11-10

### 新增
- 每个版本同时提供 5 个 APK：arm64-v8a / armeabi-v7a / x86 / x86_64 / universal
- Release 说明自动从本文件提取对应版本的改动
- CI 增加签名校验，未签名的包直接构建失败

### 变更
- 取消 debug 构建，所有产物均为签名 release 版
- 下载页按 CPU 架构分列，标明适用设备

### 修复
- 修复此前 release 只产出单一 universal 包、无法按架构选择的问题

---

## [1.0.0] - 2026-11-10

### 新增
- 粘贴链接一键解析，支持视频 / 纯音频 / 封面 / 字幕
- 格式选择：容器、编码、分辨率、音频码率、字幕语言与嵌入方式
- 下载队列：排队、暂停、重试、实时日志
- 自定义命令模板，可保存并直接执行 yt-dlp 参数
- 内置 WebView 抓取 Cookie，支持多站点配置
- 播放列表解析与按条目勾选
- Material You 动态取色，深色模式
- 60+ 语言界面

### 变更
- 版本号定为 1.0.0（versionCode 100000402）

### 构建
- 由 GitHub Actions 云端构建，不提供本地产物
- 全 ABI 拆分：arm64-v8a / armeabi-v7a / x86 / x86_64 / universal
- 签名密钥通过 GitHub Secrets 注入
