# Changelog

本文件记录 duck 每个版本的变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

每次发布新版本时：

1. 在 `## [Unreleased]` 下方补充本次改动
2. 发布时把 `[Unreleased]` 改成新版本号并加上日期
3. 同步修改 `buildSrc/src/main/kotlin/Version.kt` 里的版本号
4. 打 tag 推送，CI 会自动构建全部架构并写入 Release 说明

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
