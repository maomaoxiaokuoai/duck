# 贡献指南

感谢你考虑为 duck 做贡献。

## 提交问题

提交 issue 前请先搜索是否已有相同问题。描述里请包含：

- 设备型号与 Android 版本
- 应用版本号（设置 → 开发者）
- 复现步骤
- 相关日志（首页终端面板可查看）

## 提交代码

1. Fork 本仓库
2. 从 `main` 切出分支：

   ```bash
   git checkout -b fix/your-change
   ```

3. 提交前确认能构建：

   ```bash
   ./gradlew :app:assembleRelease
   ```

4. 发起 Pull Request，说明改动动机和行为变化

## 代码风格

- 遵循 Kotlin 官方风格
- 注释解释**为什么**，不要复述代码在做什么
- UI 文案统一用中文

## 注意事项

- 不要重新引入 yt-dlp / gallery-dl / cobalt / NewPipe 等第三方解析引擎
- 不要提交任何形式的用户凭据或 Cookie 持久化代码
- 抖音签名算法若失效，请更新 `assets/douyin/acrawler.js`
