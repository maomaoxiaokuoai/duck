# duck

抖音、小红书、快手无水印下载工具。粘贴分享链接，解析后一键保存视频、图集和动图。

## 功能

- **三平台解析**：抖音、小红书、快手，视频 / 图集 / 动图全覆盖
- **无水印直链**：优先取平台自己提供的干净编码
- **动图合成**：静态图 + 短视频合成标准 Motion Photo，系统相册直接播放
- **批量选择下载**：图集点选要哪几张，不选就是全部
- **自动更新**：启动时优选 CDN 节点检查新版本
- **纯中文界面**
- **无痕实现**：不登录、不保存任何账号凭据

## 支持平台

| 平台 | 视频 | 图集 | LivePhoto |
| --- | --- | --- | --- |
| 抖音 | ✅ | ✅ | ✅ |
| 小红书 | ✅ | ✅ | ✅ |
| 快手 | ✅ | ✅ | ❌ |

短链接同样支持：`v.douyin.com` / `xhslink.com` / `xhslink.cn` / `v.kuaishou.com`

### 各平台最高画质

| 平台 | 视频最高 | 说明 |
| --- | --- | --- |
| 抖音 | 1920×1080 | 无 Cookie 天花板 |
| 小红书 | 1080p | 源文件，优先 h265 无水印 |
| 快手 | 源画质 | 多为 1080p |

## 工作原理

三个平台的数据获取方式各不相同，共同点是**都不需要 Cookie**。

### 抖音

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

视频走高画质接口：`api-play.amemv.com/aweme/v1/play/?video_id=<id>&ratio=1080p`，
服务端会按作品实际能力返回最高档。

### 小红书

小红书比抖音宽松得多，**不需要任何签名**。

```
1. GET 分享链接（桌面 UA 会被 302 到登录页，必须移动端 UA）
2. 取页面里的 window.__INITIAL_STATE__
3. 读 noteData.data.noteData
4. 视频取 video.media.stream.h265[0].masterUrl（h264 有水印）
   图集取 imageList[].urlDefault
```

页面返回的是 `http://` 链接，必须升级为 https，否则会被混合内容策略拦掉。

### 快手

快手有两个通道，用的是免签名的那个。

```
1. GET 短链（不跟随重定向）→ 从 Location 取作品 ID
2. POST /rest/wd/ugH5App/photo/simple/info?kpn=KUAISHOU&subBiz=PHOTO
   body: {"photoId":"<id>"}
3. 返回完整 photo 对象（34 个字段）
4. 视频取 mainMvUrls[0].url，图集取 ext_params.atlas（cdn + list 拼接）
```

这个接口免签名、免 Cookie、免登录，但**必须用 POST**，GET 会被风控拒绝。

### 动图（LivePhoto）

动图 = 一张静态图 + 一小段视频。各平台的字段位置不同：

| 平台 | 静态图 | 视频 | 标记 |
| --- | --- | --- | --- |
| 抖音 | `images[].urlList` | `images[].video.playAddr[0].src` | `livePhotoType=1` |
| 小红书 | `imageList[].url` | `imageList[].stream.h264[0].masterUrl` | `livePhoto: true` |

拿到两者后合成**标准 Android Motion Photo**：

```
FFD8                     JPEG 起始
FFE1 <len> <XMP>         APP1 段，描述 Motion Photo
...原始 JPEG...           静态图
<mp4>                    追加的视频
```

XMP 里声明两个 Item（Primary + MotionPhoto），系统相册和 Google Photos
读到这段就知道怎么播放。存下来仍是一个 `.jpg`，不会留下两个文件。

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
| 视频 | `Movies/duck` |
| 图集 / 动图 | `Pictures/duck` |
| 音频 | `Music/duck` |

都在系统相册索引的标准目录下，下载完刷新即可看到。

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

- **抖音最高 1920×1080**：超过 1080p 的画质只存在于需要 UIFID 的接口里，
  而 UIFID 由服务端签发并绑定请求上下文，无法离线生成
- **快手不支持 LivePhoto**：快手 H5 接口不返回实况视频分量，
  即使签名全部攻破响应里也没有这个字段
- 抖音若改动签名算法，需要更新 `acrawler.js`
- 小红书桌面 UA 会被 302 到登录页，必须用移动端 UA
- 私有作品需要作者公开

## 许可

见 [LICENSE-MIT](LICENSE-MIT)。

## 作者

**maomao**

- GitHub: [@maomaoxiaokuoai](https://github.com/maomaoxiaokuoai)
- Telegram: [@MAOMAOXIAOKUOAI](https://t.me/MAOMAOXIAOKUOAI)
