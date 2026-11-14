# duck

抖音无水印下载工具。粘贴分享链接，解析后一键保存视频和图集。

## 功能

- **抖音解析**：视频和图集，无水印直链
- **图集批量下载**：一次保存全部图片
- **自动更新**：启动时优选 CDN 节点检查新版本
- **纯中文界面**
- **无痕实现**：不登录、不保存任何账号凭据

## 支持的内容

| 类型 | 状态 |
| --- | --- |
| 抖音视频 | ✅ |
| 抖音图集 | ✅ |
| 短链接 v.douyin.com | ✅ |
| 其他站点 | ❌ 已移除 |

## 工作原理

抖音只在**签名请求**时返回完整页面数据，未签名的请求只能拿到一个 72KB 的空壳。

```
1. GET 分享页            → 拿到 Set-Cookie: __ac_nonce
2. 用 JSVMP 计算签名     → __ac_signature（约 3ms）
3. 带 nonce + sig 再请求 → 拿到 900KB+ 完整页面
4. 解析 __pace_f         → 提取视频直链 / 图集直链
5. 走 CDN 下载           → 写入相册
```

## 技术栈

- Kotlin Multiplatform + Compose Multiplatform
- Material 3 Expressive
- Ktor（网络）
- Room（下载历史）
- WebView（执行抖音的签名 JS）

## 作者

**maomao**

- GitHub: [@maomaoxiaokuoai](https://github.com/maomaoxiaokuoai)
- Telegram: [@MAOMAOXIAOKUOAI](https://t.me/MAOMAOXIAOKUOAI)
