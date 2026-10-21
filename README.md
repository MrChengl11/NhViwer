# Nhviwer — Material 3 漫画阅读器（Android）

<img src="docs/icon.png" width="112" align="right" alt="Nhviwer 图标">

一个 漫画客户端，功能对齐 [nhentai.net](https://nhentai.net/) 的浏览 / 搜索 / 收藏 / 评论 / 下载，
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

规模：**39 个 Kotlin 文件 / 5562 行**，debug APK 21.1 MB（22,169,656 字节，v1.0.3）。

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
- 首页：最新 / 最多收藏 双榜、关键词搜索、热门标签快链；**上滑自动加载下一页（滑到哪儿加载到哪儿，无「加载更多」按钮）**，搜索入口在右上角
- 分类页：按首字母浏览标签（`#` + A–Z 27 格字母栏），类型可切 标签 / 作者 / 原作 / 角色 / 社团
- 搜索：`tag:xxx`、`artist:xxx`、`language:chinese` 等 nhentai 查询语法，支持分页

**详情**
- 封面、日文/英文标题、页数、收藏数、评论数
- 标签分组（分类 / 语言 / 原作 / 作者 / 角色 / 社团），点击标签直接搜索
- 一键收藏 / 取消收藏，相关推荐

**阅读器**
- 四种布局：横向翻页 / 竖向翻页 / 竖向连续 / 双页（顶栏 ⟳ 按钮循环切换）
- RTL（日漫从右往左）、预加载页数可调、页码显示（双页显示为 `1-2 / 8`）
- 页数列表：点屏幕正中的**透明判定区**（72dp 正方形，无可见控件），从底部弹出九宫格页码，点哪页跳哪页（四种布局通用）
- 缩放：双击放大 2.5 倍 / 再次双击还原；顶栏「放大 / 缩小」按钮同样是 1× ↔ 2.5×；放大后可拖动平移
- 手势：单击左 1/3 上一页、右 1/3 下一页（RTL 时左右互换）、中间切换工具栏；中心透明判定区单击唤出页数列表；手势判定挂在根容器上，翻页 / 连续滚动的拖拽与点击互不干扰
- 阅读进度实时写入历史（翻页后 400 ms 防抖落库），重进从上次位置继续

**下载**
- 下载队列（并发数可配置 1–8），断点续传（已存在的页不重下）
- 前台服务 + 常驻通知显示总进度，支持「全部暂停」
- 下载完成页可直接离线阅读（读本地文件，不联网）

**收藏 / 历史**
- 本地收藏（Room），收藏夹分组
- 收藏页长按进入多选（封面勾选蒙层、标题显示「已选 N」），右上角按钮**批量下载 / 批量取消收藏**；卡片下方不再放删除按钮
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



## 4. 通知与后台下载

targetSdk 37 下 Android 13+ 必须**运行时申请** `POST_NOTIFICATIONS`，否则通知会被静默丢弃（下载照跑，但看不到进度）。

当前实现：
- `MainActivity` 启动即申请该权限
- 点「下载」→ 入队 → 启动 `DownloadService`（`FOREGROUND_SERVICE_TYPE_DATA_SYNC`）
- 服务在 `onStartCommand` 里**先** `startForeground` 再读数据库，避免 5 秒超时被系统杀
- 观察下载表：连续 3 次空队列且数据库确认无活跃任务才 `stopSelf()`，避免 enqueue 与置 RUNNING 之间的竞态导致服务秒退

## 5. 数据与隐私

- 所有收藏、历史、下载任务、评论草稿都只存**本机**（Room / DataStore / App 私有目录）
- 不上传任何数据；无统计、无广告、无第三方 SDK
- 登录凭证（API Key）仅存本地 App 私有目录（`manga-auth`），不写日志、不外发
- 账号收藏同步是**用户主动触发**的：只在你点「同步收藏」或在登录状态下收藏/取消收藏时请求远端

## 6. 下载与安装

最新版见 GitHub Releases：<https://github.com/MrChengl11/NhViwer/releases>

- `Nhviwer-1.0.3.apk` — v1.0.3（versionCode 3，debug 签名，22,169,656 字节，sha256 `9974f2d5f65919f4b4afe09a22c18353ca714efa4df1d9403ce3f831f9b64e0e`）
- 客户端不含任何内容、也不内置代理：请在「设置」里选择「直连」（设备已挂代理）或「本地反代」（`node tools/nhentai-proxy.mjs`，默认 `http://127.0.0.1:8787`）
- 从源码构建：`cd android && ./gradlew :app:assembleDebug`

## 7. 开发记录

| 里程碑 | 状态 |
| --- | --- |
| 浏览 / 搜索 / 分类（A–Z 首字母） | ✅ 实机验证 |
| 详情 / 收藏 / 相关推荐 | ✅ 实机验证 |
| 阅读器（四布局 / RTL / 缩放 / 进度 / 页数九宫格） | ✅ 实机验证 |
| 下载队列 / 断点续传 / 离线阅读 | ✅ 实机验证（31/31 页、16/16 页） |
| 前台服务 + 进度通知 | ✅ 实机验证 |
| 设置（动态取色 / 画质 / 并发） | ✅ 实机验证 |
| 评论（浏览 / 发表 / 删除） | 🟡 浏览已通；发表/删除需登录态，未实机走通 |
| 首页上滑直接滚动浏览 | ✅ 实机验证（顶部直接上滑即滚动，像素差 64.24%）；已移除原下拉唤起搜索手势（改右上角搜索按钮） |
| 首页无限滚动（无「加载更多」按钮） | ✅ 实机验证（滑到底自动翻页，最多收藏榜自动 4 页共 89 卡后显示「已经到底了」，90+ 次 fling 无崩溃） |
| 「加载更多」重复 key 崩溃修复 | ✅ 实机验证（列表并入时按 id 去重，logcat 无 "Key ... was already used"） |
| 阅读器页数列表（中心透明判定区 → 底部九宫格跳页） | ✅ 实机验证（四布局均弹出，横向 / 竖向 / 双页 / 连续滚动跳 7 / 10 / 20 页均精确） |
| 中心透明判定区（无可见控件）唤出页数列表 | ✅ 实机验证（横向点中心「10/43」；双页点选第 23 页落 23-24；中间非中心单击切工具栏、左右 1/3 翻页均正常） |
| 收藏页长按多选 + 批量下载 / 删除 | ✅ 实机验证（长按「已选 1」→ 再点第 2 张「已选 2」；下载选中 2 本全部完成；删除弹「取消收藏 N 本？」确认框；卡片下无删除按钮） |
| 阅读器拖拽手势修复 | ✅ 实机验证（旧版点击判定覆盖在内容上导致滑动全被当成单击；改为根容器判定后四布局拖拽全部正常） |
| 从详情页返回首页保持滚动位置 | ✅ 实机验证（第 2-3 屏进详情再返回，前后像素差 0.0000） |
| 设置页声明文案改为 Nhviwer | ✅ 实机验证（「关于」段落逐字一致） |
| 阅读器双页跨页布局 | ✅ 实机验证（1-2 / 3-4 并排，RTL 顺序正确） |
| 阅读进度实时写入历史 | ✅ 实机验证（翻页后历史显示「看到第 2/8 页」） |
| 登录：API Key（唯一通道） | ✅ 真机验证：真实 Key 登录成功（设置页显示「已登录：estelle233（API Key）」），无效 Key 正确提示「API Key 无效或已失效」 |
| 账号收藏双向同步 | ✅ 真机验证：真实账号点「同步收藏」→「同步完成：拉取 30 条，推送 0 条」 |
| 账号密码 / 访问令牌通道 | 已按要求**移除**（实测账号密码被 Turnstile 拦、访问令牌拿不到），登录页只剩 API Key |
| 真机（Galaxy Tab S7，1600x2560 / 340dpi / Android 12） | ✅ 首页 / 详情 / 阅读器 / 设置 / 登录页均实机出图正常 |
| 直连模式（设备已挂代理，无需电脑反代） | ✅ 真机验证：检测连接「直连正常」、首页封面全加载、阅读器出图、下载 4/4 页完成 |


