# Nhviwer — Material 3 漫画阅读器（Android）

<img src="docs/icon.png" width="112" align="right" alt="Nhviwer 图标">

一个 **nhentai 风格的漫画客户端**，功能对齐 [nhentai.net](https://nhentai.net/) 的浏览 / 搜索 / 收藏 / 评论 / 下载，
交互与信息架构参考成熟 Android 客户端 [EhViewer_CN_SXJ](https://github.com/xiaojieonly/Ehviewer_CN_SXJ)，
UI 使用 Google **Material Design 3（Expressive：大圆角、动态取色、弹簧动效）** 构建。

原生 Android：**Kotlin + Jetpack Compose**，单 Activity + Compose Navigation。

应用名 **Nhviwer**（包名 `com.mangaviewer.app`），图标沿用 EhViewer 的视觉风格：teal `#009688` 圆角方块 + 白色衬线字母「Nh」+ 45° 向右下的长投影，同时提供圆形图标与 Android 8+ 自适应图标（前景/背景分层）。

> ⚠️ **免责与合规声明**
> 1. 本项目仅用于**技术学习与研究**（Android / Compose / M3 设计语言的工程实践），不提供、不托管、不分发任何漫画内容。
> 2. 所有图片与数据均来自公开的第三方 API，版权归原作者与平台所有；请遵守所在地区法律法规与目标站点服务条款。
> 3. 应用启动有 18+ 门控提示，用户须自行确认已成年。
> 4. 项目**不绕过任何验证码**（Turnstile 交回用户手动完成），不提供批量爬取能力。
> 5. 请勿用于商业用途，尊重站点速率限制（客户端内置 12 请求/分钟的限流闸）。

---

## 1. 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 语言 / 构建 | Kotlin 2.4.21、AGP 9.4.1、Gradle 9.6 | JDK 17，compileSdk/targetSdk 37，minSdk 26 |
| UI | Jetpack Compose + Material 3（BOM 2026.09.00） | 单 Activity，Compose Navigation 2.10.2 |
| 状态 | StateFlow + Compose `collectAsState` | 手写 DI（`AppContainer`），无 Hilt |
| 本地库 | Room 2.8.5 | `manga.db`：favorites / downloads / history / comment_drafts |
| 偏好 | DataStore Preferences 1.1.7 | `manga-settings` |
| 网络 | OkHttp 5.2.1 + 手写 v2 API 客户端 | 内置限流闸；连接方式可切「直连 / 本地反代」 |
| 图片 | Coil 3.6.3 | 自定义 Interceptor：直连或走本地反代，多图床后备 |
| 后台 | 前台服务（DATA_SYNC 类型） | 下载进度常驻通知 |

规模：**39 个 Kotlin 文件 / 5206 行**，debug APK 21.3 MB（22,380,740 字节）。

## 2. 架构总览

```
app/src/main/java/com/mangaviewer/app/
├── MainActivity.kt              # 入口，申请 POST_NOTIFICATIONS
├── MangaApp.kt                  # Application，持有 AppContainer
├── data/
│   ├── AppContainer.kt          # 手写 DI：DB / 设置 / OkHttp / 各仓库
│   ├── db/                      # Room entity + DAO
│   ├── local/SettingsRepository.kt   # DataStore 偏好（阅读器布局、画质、并发数…）
│   ├── remote/
│   │   ├── NhentaiApi.kt        # v2 API 客户端（限流 12 req/min + Mutex）
│   │   ├── ImageProxyInterceptor.kt  # 图片改写为反代 /img?url=…，多图床后备
│   │   ├── AuthStore.kt         # access_token / api_key
│   │   └── pow/Sha256Pow.kt     # 登录 CoW 工作量证明
│   ├── repo/                    # galleries / favorites / downloads / history / comments / tagIndex / account
│   └── model/                   # UiModels + Mappers
├── download/DownloadService.kt  # 前台服务：把下载进度投影到常驻通知
└── ui/
    ├── MangaRoot.kt             # 底部导航 + NavHost + 18+ 门控
    ├── screens/                 # Home / Browse / Search / Detail / Reader / Favorites / Downloads / Settings / Login …
    ├── components/              # GalleryCard / GalleryThumb / ErrorBox …
    └── theme/                   # M3 主题、动态取色
```

## 3. 功能清单

**浏览**
- 首页：最新 / 最多收藏 双榜、关键词搜索、热门标签快链；**列表顶部下拉可直接展开搜索框**
- 分类页：按首字母浏览标签（`#` + A–Z 27 格字母栏），类型可切 标签 / 作者 / 原作 / 角色 / 社团
- 搜索：`tag:xxx`、`artist:xxx`、`language:chinese` 等 nhentai 查询语法，支持分页

**详情**
- 封面、日文/英文标题、页数、收藏数、评论数
- 标签分组（分类 / 语言 / 原作 / 作者 / 角色 / 社团），点击标签直接搜索
- 一键收藏 / 取消收藏，相关推荐

**阅读器**
- 四种布局：横向翻页 / 竖向翻页 / 竖向连续 / 双页（顶栏 ⟳ 按钮循环切换）
- RTL（日漫从右往左）、预加载页数可调、页码显示（双页显示为 `1-2 / 8`）
- 缩放：双击放大 2.5 倍 / 再次双击还原；顶栏「放大 / 缩小」按钮同样是 1× ↔ 2.5×；放大后可拖动平移
- 手势：单击左 1/3 上一页、右 1/3 下一页（RTL 时左右互换）、中间切换工具栏；手势层独立于缩放层，缩放时翻页仍然可用
- 阅读进度实时写入历史（翻页后 400 ms 防抖落库），重进从上次位置继续

**下载**
- 下载队列（并发数可配置 1–8），断点续传（已存在的页不重下）
- 前台服务 + 常驻通知显示总进度，支持「全部暂停」
- 下载完成页可直接离线阅读（读本地文件，不联网）

**收藏 / 历史**
- 本地收藏（Room），收藏夹分组
- 浏览历史记录与清理

**账号（登录 / 云端收藏）**
- 登录只支持 **API Key** 一条通道（官方给第三方客户端的长期凭证）：网页端 头像 → 设置 → API Keys → 新建，粘贴即可
- 登录后收藏双向同步：云端收藏补到本地（保留本地文件夹与加入时间），本地多出的收藏推送云端
- 详情页收藏 / 取消收藏、收藏页删除都会同步到账号（失败只提示，不回滚本地）
- 登录态可查看用户名 / UID，支持为当前账号直接生成新 API Key（只显示一次）

**设置**
- 动态取色（Material You）、深色模式、种子色
- 图片画质（原图 / 1600 / 1080 / 720）
- 连接方式：**直连**（设备自己挂了代理，默认走 `https://nhentai.net`）/ **本地反代**（默认 `http://127.0.0.1:8787`，可切 `http://10.0.2.2:8787`），一键「检测连接」
- 下载并发数、阅读器布局与预加载

## 4. 登录与账号同步

### 4.1 为什么不做「网页端 WebView 登录」

这是本机网络条件下实测出来的结论，不是偷懒：

| 障碍 | 证据 |
| --- | --- |
| DNS 被污染 | `adb shell ping nhentai.net` 解析到 `157.240.6.35`（Facebook 段）；宿主机同样污染 |
| DoT 也走不通 | 给设备开 Private DNS（`one.one.one.one`）后 `ping nhentai.net` 变 `unknown host`（853 端口被拦），已复原为 `off` |
| Turnstile 与域名绑定 | 站点 sitekey 校验来源域名；走本地反代（`127.0.0.1`）必然校验失败 |
| WebView 无法代发请求 | `shouldInterceptRequest` 拿不到 `fetch`/`XHR` 的 POST body，没法替用户登录 |

所以「拉一个网页让用户在里面登录」在本机**必然白屏**，属于假功能。

### 4.2 为什么最终只保留 API Key

v2 API 的 securitySchemes 有两种 header：`Key <api_key>` 与 `User <access_token>`。三条可能的登录通道都试过，只有一条真的能用：

| 通道 | 实测结果 | 结论 |
| --- | --- | --- |
| **API Key** | 真机登录成功（`GET /api/v2/user` 返回用户名 / UID），收藏同步实测拉取 30 条 | ✅ 保留 |
| 访问令牌 | 需要网页登录才能拿到 access_token，而网页登录在本机不可达；且令牌会过期 | ❌ 删除 |
| 账号密码 | 真实求解 PoW 后 `POST /api/v2/auth/login`，服务端返回 `400 {"error":"CAPTCHA solution is required"}`（Turnstile 强制） | ❌ 删除（不绕过验证码） |

因此 `LoginScreen` 现在**只有一个 API Key 输入框**，`AuthStore` 也只存 `api_key` 一个字段。

> 合规边界：**不实现任何验证码绕过**。Turnstile 无法自动化就如实告诉用户走 API Key，不假装能做。

### 4.3 PoW 的正确解法（评论发布仍在用）

登录不再走账号密码，但**发评论**仍要过服务端的 PoW（`GET /api/v2/pow?action=comment`），所以这段实现保留。

`difficulty` 的语义是 **sha256 摘要的前导零比特数**，不是十六进制 0 的个数。`difficulty = 17` 时约 50 万次尝试可解（实测 763 ms）。把它当 nibble 处理会导致 `difficulty > 15` 时永远解不出来。

```kotlin
// data/remote/pow/Sha256Pow.kt
fun solve(challenge: String, difficulty: Int, maxAttempts: Long = 200_000_000L): String
fun verify(challenge: String, nonce: String, difficulty: Int): Boolean
private fun leadingZeroBits(hash: ByteArray): Int   // 逐字节数前导 0，再逐位数剩余位
```

### 4.4 收藏同步语义

- **拉取**：`GET /api/v2/favorites?page=N` 逐页拉到底（最多 40 页保护），upsert 进 Room；已存在的条目保留原 `folder` 与 `addedAt`。
- **推送**：本地有、云端没有的收藏逐条 `POST /api/v2/galleries/{id}/favorite`。
- **实时**：详情页心形、收藏页删除都 fire-and-forget 推送；失败只提示，不回滚本地，避免弱网下反复抖动。
- 服务端对该类接口限流 **15 次/分钟/用户**，客户端 12 次/分钟的闸门优先生效。

### 4.5 认证头与凭证存储

```kotlin
// AuthStore.authHeader()
"Key <api_key>"
```

凭证只存本机 `manga-auth`（SharedPreferences，App 私有目录）。登录时先 `meWithApiKey()` 用**未保存**的 Key 校验身份，成功了才落盘，避免把打错的 Key 写进去。
## 5. 两种连接方式：直连（推荐）/ 本地反代

**结论先说：反代不是必需的。** 只要设备本身能打开 `nhentai.net`（手机/平板挂着代理、TUN 模式，或分应用代理），把「设置 → 网络连接」切到 **直连** 就行，电脑什么都不用开。反代只在「设备自己连不上、但电脑能连」时才需要——比如没装代理的模拟器，或者你只想让电脑走代理。

| 模式 | 什么时候用 | 请求走向 |
| --- | --- | --- |
| 直连（已挂代理） | 设备自己能访问 nhentai.net | API → `https://nhentai.net`；图片 → `i1-i4` / `t1-t4.nhentai.net` 直连 |
| 本地反代 | 设备自己连不上（DNS 被污染 / 没代理） | API 与图片都发给电脑上的 `127.0.0.1:8787`，由脚本转发 |

两种模式只差一个开关：直连时**图片地址完全不做改写**（不套 `/img?url=`），阅读器与下载都直接请求图床域名；两种模式共用同一套「多图床候选 + 失败自动切换」。

真机实测（Galaxy Tab S7，经宿主代理出网）：直连模式「检测连接」提示「直连正常」，首页 4 列封面全部加载，阅读器正常出图，下载 4/4 页完成（2.2 MB）。

### 本地反代是什么（只在需要时看）

**一句话**：它不是一个联网服务，也不是服务器，就是你电脑上跑的一个 `node` 小脚本（`tools/nhentai-proxy.mjs`，零依赖）。
App 把请求发给 `127.0.0.1:8787`，这个脚本替你转发到 `nhentai.net`，再把响应原样带回给 App。相当于一个「本机中转站」。

**它解决三个现实问题**：

| 问题 | 不中转会怎样 | 中转怎么做 |
| --- | --- | --- |
| 本机 DNS 被污染 | `nhentai.net` 解析到 157.240.x.x（Facebook 段），连不上 | 直连已知可用的 Cloudflare IP，同时把 `Host`/SNI 写成真域名，证书校验照常 |
| v2 API 不回 CORS 头 | 浏览器/WebView 里的 fetch 会被挡 | 由脚本在服务端加 CORS 头（App 用 OkHttp 其实不需要，但 Web 端需要） |
| 图床域名分流 | `i*.nhentai.net` 有时整体 520 | `/img?url=…` 统一入口 + 图床白名单（`i1-i4` / `t1-t4`），失败可换图床重试 |

**它不做什么**：不缓存内容、不代理登录页、不绕过任何验证码、不上传任何数据；只转发 `/api/v2/*` 与白名单图床，`/health` 用来自检。

细节：

- 反代脚本：`../tools/nhentai-proxy.mjs`（Node ESM，零依赖）
- 路由：`/api/v2/*` → `https://nhentai.net/api/v2/*`；`/img?url=…` → 白名单图床；`/health`
- 走静态 Cloudflare IP（本机 DNS 被污染），可用环境变量 `NHENTAI_IPS` 覆盖
- 监听 `0.0.0.0:8787`，模拟器里用 `10.0.2.2:8787` 或宿主 IP 访问

启动反代：

```bash
cd "D:/harness-deepseek workspace/漫画自动下载脚本/tools"
HOST=0.0.0.0 node nhentai-proxy.mjs
curl http://127.0.0.1:8787/health   # {"ok":true,...}
```

## 6. 构建与安装

```bash
cd manga-app/android

# 1) 生成 wrapper（首次）
gradle wrapper --gradle-version 9.6.0

# 2) 编译 debug APK
JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot' \
  ./gradlew.bat :app:assembleDebug --console=plain

# 3) 产出分发包（可选，改名为 Nhviwer-1.0.0.apk）
mkdir -p dist && cp app/build/outputs/apk/debug/app-debug.apk dist/Nhviwer-1.0.0.apk

# 4) 安装到真机/平板
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5) 只有「本地反代」模式才需要：把宿主反代端口映射进设备
#    （真机的 127.0.0.1 是它自己；重插 USB 后要重设。用「直连」模式可整步跳过）
adb reverse tcp:8787 tcp:8787
adb shell am start -n com.mangaviewer.app/.MainActivity
```

本机可用现成 Gradle 时也可直接：`/c/tools/gradle-9.6.0/bin/gradle.bat :app:assembleDebug`。

`local.properties` 需指向 SDK：`sdk.dir=D:/android-sdk`。
仓库路径含中文时，`android/gradle.properties` 已设 `android.overridePathCheck=true`。

## 7. 通知与后台下载

targetSdk 37 下 Android 13+ 必须**运行时申请** `POST_NOTIFICATIONS`，否则通知会被静默丢弃（下载照跑，但看不到进度）。

当前实现：
- `MainActivity` 启动即申请该权限
- 点「下载」→ 入队 → 启动 `DownloadService`（`FOREGROUND_SERVICE_TYPE_DATA_SYNC`）
- 服务在 `onStartCommand` 里**先** `startForeground` 再读数据库，避免 5 秒超时被系统杀
- 观察下载表：连续 3 次空队列且数据库确认无活跃任务才 `stopSelf()`，避免 enqueue 与置 RUNNING 之间的竞态导致服务秒退

## 8. 数据与隐私

- 所有收藏、历史、下载任务、评论草稿都只存**本机**（Room / DataStore / App 私有目录）
- 不上传任何数据；无统计、无广告、无第三方 SDK
- 登录凭证（API Key）仅存本地 App 私有目录（`manga-auth`），不写日志、不外发
- 账号收藏同步是**用户主动触发**的：只在你点「同步收藏」或在登录状态下收藏/取消收藏时请求远端

## 9. 开发记录

| 里程碑 | 状态 |
| --- | --- |
| 浏览 / 搜索 / 分类（A–Z 首字母） | ✅ 实机验证 |
| 详情 / 收藏 / 相关推荐 | ✅ 实机验证 |
| 阅读器（四布局 / RTL / 缩放 / 进度） | ✅ 实机验证 |
| 下载队列 / 断点续传 / 离线阅读 | ✅ 实机验证（31/31 页、16/16 页） |
| 前台服务 + 进度通知 | ✅ 实机验证 |
| 设置（动态取色 / 画质 / 并发） | ✅ 实机验证 |
| 评论（浏览 / 发表 / 删除） | 🟡 浏览已通；发表/删除需登录态，未实机走通 |
| 下拉手势唤起搜索 | ✅ 实机验证（顶部下拉 72dp 阈值，列表未滚动时生效） |
| 阅读器双页跨页布局 | ✅ 实机验证（1-2 / 3-4 并排，RTL 顺序正确） |
| 阅读进度实时写入历史 | ✅ 实机验证（翻页后历史显示「看到第 2/8 页」） |
| 登录：API Key（唯一通道） | ✅ 真机验证：真实 Key 登录成功（设置页显示「已登录：estelle233（API Key）」），无效 Key 正确提示「API Key 无效或已失效」 |
| 账号收藏双向同步 | ✅ 真机验证：真实账号点「同步收藏」→「同步完成：拉取 30 条，推送 0 条」 |
| 账号密码 / 访问令牌通道 | 已按要求**移除**（实测账号密码被 Turnstile 拦、访问令牌拿不到），登录页只剩 API Key |
| 真机（Galaxy Tab S7，1600x2560 / 340dpi / Android 12） | ✅ 首页 / 详情 / 阅读器 / 设置 / 登录页均实机出图正常 |
| 直连模式（设备已挂代理，无需电脑反代） | ✅ 真机验证：检测连接「直连正常」、首页封面全加载、阅读器出图、下载 4/4 页完成 |

## 10. 踩坑记录（供后来者少走弯路）

| 现象 | 根因 | 修法 |
| --- | --- | --- |
| 在线漫画阅读器整屏黑，页码正常 | 图片拦截器 `HOST_RE` 只匹配 `t*/tt*`，**漏了 `i*`**；阅读页大图走 `i1..i4`，地址没被改写成 `/img?url=`，直连被污染的域名 → 10s 超时 | 正则改为 `^([it]t?[0-9]{1,2})\.nhentai\.net$`；保留多图床候选 + `onError` 依次切换 |
| 阅读器点击分区翻页完全无反应 | ① `detectTapGestures` 挂在 `HorizontalPager` 的 modifier 上，点击检测器抢走了横向拖拽；② 改为放进缩放 `graphicsLayer` 内部后，命中测试又完全失效 | 手势层放在**缩放 wrapper 之外**的根 Box 里；各布局通过 `stepRef.handler` 注册翻页动作 |
| 下载完成但没有通知 | ① `POST_NOTIFICATIONS` 从未运行时申请；② 服务启动瞬间队列还空（enqueue 与置 RUNNING 之间几十毫秒）就 `stopSelf()` | ① 启动即申请；② 连续 3 次空 tick 且数据库确认无活跃任务才停服务 |
| 设置页 4 个 chip 被压成竖排单字 | 窄屏 `Row` 溢出 | 改用 `FlowRow` |
| 双击缩放无法用 adb 验证 | 两次 `input tap` 间隔大于 Compose `doubleTapTimeout`（300 ms） | 用顶栏放大/缩小按钮验证同一条渲染路径 |
| 模拟器上 `127.0.0.1:8787` 不通 | 模拟器的 127.0.0.1 指向自身 | `adb reverse tcp:8787 tcp:8787`，或直接把设置里的反代地址改成 `10.0.2.2:8787` |
| PoW 求解永远超时 | `difficulty` 被当成「十六进制 0 的个数」（nibble）；`difficulty = 17` 要求 17 个 0 = 68 bit，不可能解出 | 改为按**前导零比特**计数，diff=17 约 50 万次、763 ms |
| `POST /api/v2/login` 返回 404 | v2 的正确路径是 `/api/v2/auth/login` | 以 `/api/v2/openapi.json` 为准，别猜路径 |
| 想用 WebView 拉起网页登录 | 设备 DNS 污染（`nhentai.net` → 157.240.6.35）、DoT 853 被拦、Turnstile 与域名绑定、`shouldInterceptRequest` 拿不到 fetch/XHR 的 POST body | 放弃网页登录，改 API Key / 访问令牌 / 账号密码三通道，并如实暴露 Turnstile 限制 |
| 真机上 `127.0.0.1:8787` 不通 | 真机的 127.0.0.1 是它自己，模拟器的才是宿主 | `adb reverse tcp:8787 tcp:8787`（每次重插 USB 都要重设），或改设置里的反代地址为宿主局域网 IP；设备自己有代理则直接用「直连」模式 |
| 编译报「Primary constructor of data class must only have property parameters」等一串语法错 | KDoc 里写了 `i*/t*`，其中的 `*/` 提前结束了块注释，后面的类体被当成代码 | 块注释里不要出现 `*/`；改写成「原图床域名」或 `i1-i4、t1-t4` |

