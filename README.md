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

签名算法来自抖音官方的 `byted_acrawler`（一段混淆过的 JS 虚拟机），代码放在
`app/src/androidMain/assets/douyin/acrawler.js`，在隐藏的 WebView 中执行。

**不存任何 Cookie**：`__ac_nonce` 和 `__ac_signature` 都是服务端当场下发、
单次请求用完即弃的临时值。

## 自动更新

程序不直接连接 `github.com`（国内延迟高）。启动时会并发探测多个 CDN 节点，
选出最快的那个再拉取版本信息：

| 节点 | 说明 |
| --- | --- |
| `api.github.com` | 官方 API |
| `ghproxy.net` | 第三方加速 |
| `gitmirror.xyz` | 第三方加速 |
| `ghfast.top` | 第三方加速 |

优选结果会记住，下次启动直接使用。下载 APK 时同样走该节点。

## 下载位置

| 内容 | 路径 |
| --- | --- |
| 图集 | `Pictures/duck` |
| 视频 | `Movies/duck` |

两个目录都会被系统相册索引，下载完刷新即可看到。

## 构建

```bash
./gradlew :app:assembleRelease
```

需要 JDK 17 及以上。仓库路径不能含非 ASCII 字符，否则 AGP 会拒绝构建
（可用 `-Pandroid.overridePathCheck=true` 绕过）。

签名通过环境变量传入：

```
DUCK_KEYSTORE_PATH
DUCK_KEYSTORE_PASSWORD
DUCK_KEY_ALIAS
DUCK_KEY_PASSWORD
```

## 技术栈

- Kotlin Multiplatform + Compose Multiplatform
- Material 3 Expressive
- Ktor（网络）
- Room（下载历史）
- WebView（执行抖音的签名 JS）

## 已知限制

- 只支持抖音，其他站点已在 2.0.36 移除
- 抖音若改动签名算法，需要更新 `acrawler.js`
- 私有作品需要作者公开

## 许可

见 [LICENSE-MIT](LICENSE-MIT)。

## 作者

**maomao**

- GitHub: [@maomaoxiaokuoai](https://github.com/maomaoxiaokuoai)
- Telegram: [@MAOMAOXIAOKUOAI](https://t.me/MAOMAOXIAOKUOAI)
