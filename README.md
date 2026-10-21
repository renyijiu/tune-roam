# 随声 / Tune Roam

Android 上的全球网络电台。先把 Android 做完，共享的 Kotlin 核心不依赖 Android，以后可以加一个 iOS 的 SwiftUI 壳。

显示名集中在三处，改名只动这三处：

- 英文：`app/androidApp/src/main/res/values/strings.xml` 的 `app_name`（Tune Roam）
- 中文：`app/androidApp/src/main/res/values-zh/strings.xml` 的 `app_name`（随声）
- 代码常量：`app/sharedLogic/.../core/AppIdentity.kt` 的 `NAME_EN` / `NAME_ZH`（User-Agent 从这里生成）

包名是 `app.tuneroam.radio`，写在 `app/androidApp/build.gradle.kts` 的 `applicationId` 和 `namespace`。

界面上的名字只用字符串资源 `app_name`，不在 Compose 里写死。

## 这一版有什么

- 漫游：Compose Canvas 的表盘。左右拨动，针下的国家会换一座可播的电台。「随便一座」就是「下一站」。表盘跟着正在播的台转；放的台所在国家不在表盘上时，表盘不动，台名下面标出实际国家。
- 首页：热门电台，以及按名称、标签、国家搜索。默认隐藏最近检查失败的台（`lastcheckok`）。可以只看这台手机连通过的台，也可以对当前列表做一次短超时探测。
- 国家：国家列表和电台数量。首页、漫游和国家页有一层 Compose Canvas 星点背景。
- 下一站 / 上一站：下一站从实时目录随机抽一批（连不上时用随包快照），后台先探测好后面三座能连通的，按下去通常马上就有声音；在这台手机上失败过的台排到后面。上一站沿本次播放过的台往回走，再按下一站回到原处。播放页、迷你播放条、通知、锁屏、耳机和蓝牙 / 车机的上一首、下一首都是这两个动作。
- 正在播放：台名、图标、国家、标签、码率 / 编码、健康状态、播放暂停、收藏、睡眠定时。有 ICY 元数据时，界面、通知和锁屏都显示当前曲目，台标用电台 favicon。
- 收藏和最近播放存在本机 SQLDelight 里。
- 各页面底部有迷你播放条。
- 后台播放：Media3 `MediaLibraryService`，通知、锁屏、耳机键、蓝牙 / 车机的 AVRCP 信息。流标记为直播，不提供进度条。来电或耳机拔出时暂停。暂停超过 30 秒再继续，会从直播点重新连接，而不是先放旧缓冲。
- 浏览树：收藏、热门、按国家。`automotive_app_desc.xml` 声明了 `media`。Android Auto 不是这一版的重点（中国大陆不可用）。
- 流失败或在超时内起不来时，先试 `url_resolved`，再试 `url`。都失败后自动换一座相近、并且短探测能连通的台（最多三次），也可以手动再点一次。

## 更新记录

### v4（下一站）

- 新增共享的 `RoamDeck`：下一站、上一站和预探测。后台保持三座已经探测连通的台，冷启动时当场最多试三座。离线时预探测最多跑 12 次就停，不会空转。
- 播放服务把 ExoPlayer 包在 `RoamingPlayer`（`ForwardingPlayer`）里交给媒体会话，对外提供 next / previous 命令。通知、锁屏、耳机、蓝牙 AVRCP、车机的上一首 / 下一首都会换台。
- `PlaybackCommands` 增加 `next()` / `previous()`。界面上的按钮也经过媒体会话，和外部控制走同一条路径。
- 自动换到相近台不会进入「上一站」历史，免得退回到坏台。

### v3（架构整理）

- `commonMain` 不再调用 JVM API：时间统一走 `core/Platform.kt` 的 `epochMillis()`，为以后加 Apple 目标做准备。
- 镜像选择：记住的镜像和内置镜像同时探测，**第一个应答的就用**，不再等最慢的那台超时；`/json/servers` 只在它们都不应答时才查。并发请求共用一次选择（`Mutex`）。被取消的请求不再被当成镜像故障。
- 电台连通探测只读响应头。原来的 `get()` 会读 body，而大多数流忽略 `Range`、不会结束，结果能播的台也被记成连不上。
- 起播超时只由共享的 `PlaybackCoordinator` 判断，服务每秒上报一次引擎状态；缓冲时用户暂停不再算超时。
- 换相近台的规则合并成共享的 `SimilarStations`：服务的自动换台和播放页的「换一座相近的」走同一套排序和短探测。
- 睡眠定时由播放服务倒计时，离开播放页或关掉界面后照样会停；重新设定会取消旧的倒计时。
- 去掉 `onVolumeChanged` 里的暂停逻辑：车机或其他控制端调音量时，不会再把播放暂停。
- 「随便一座」改用 Radio Browser 的 `order=random`，一次取一批作为牌堆，用完再取。
- 搜索结果不再写入本机缓存（迁移 `2.sqm` 会删掉旧的搜索缓存）。热门、国家列表、各国电台照常缓存。
- 首页「探测当前列表」改为并行探测；接口返回的电台按 uuid 去重。

### v2

- Radio Browser 会短超时探测已知镜像（目前是 `de1` 和 `de2`，再加上 `/json/servers` 发现的地址），记住最快的一台，请求失败就换下一台。
- 应用内带了一份电台快照（含大陆、香港、台湾、新加坡的中文台，以及一部分全球热门）。实时目录连不上时用这份快照，连上之后写入本机缓存。
- 每座台的连通结果记在本机。首页可以按「这台手机连通过」过滤，播放失败时优先换曾经连通过的相近台。
- 新增漫游表盘、随便一座、最近播放、睡眠定时。ICY 曲目名和电台图标进媒体会话。

### v1

- 首页、国家、收藏、正在播放，Media3 后台播放，流地址备用。
- 修复启动崩溃：`PlaybackCommands` 必须按接口注册进 Koin，否则 `HomeViewModel` 创建失败，进程会立刻退出。播放服务在 `onStartCommand` 里先 `startForeground`，避免慢启动被系统杀掉。

数据来自 [Radio Browser](https://api.radio-browser.info/) 的公开 API。客户端同时短超时探测记住的镜像和内置镜像，用第一个应答的那台并记下来。请求带说明性的 User-Agent，播放时调用 `/json/url/{stationuuid}` 记一次点击。镜像都连不上时，界面改用随包的电台快照，并说明实时目录不可达。

灵感来自网页电台 [RoamDial](https://www.v2ex.com/t/1249321)：按世界各地浏览电台。随声不是 RoamDial，名字、包名和代码都与它无关。

## 目录

```
app/sharedLogic/     Kotlin Multiplatform 核心（目前只有 Android 目标）
  domain/            电台模型、目录接口、备用流策略、PlaybackCoordinator
  data/              Ktor 客户端、JSON 解析、SQLDelight 收藏与缓存
  presentation/      ViewModel（界面状态，不含 Compose）
  di/                Koin 模块；平台只提供 HTTP 引擎和数据库驱动
app/androidApp/      Compose 界面、Media3 服务、平台绑定
```

`sharedLogic` 的 `commonMain` 不引用 `android.*`。Android 专用代码在 `androidMain`（OkHttp、SQLDelight 的 Android 驱动）和 `androidApp`（ExoPlayer、Compose）。

## 从 ListenUp 借来的结构

[ListenUp](https://github.com/ListenUpApp/ListenUp) 是有声书应用，许可证是 AGPL-3.0。这里只沿用它的分层方式，没有复制它的源码。

- **模块**：核心放在 `app/sharedLogic`，Android 应用是薄的 `app/androidApp`。ListenUp 还有一份 Compose Multiplatform 的 `sharedUI`；随声的界面留在 `androidApp`，因为 iOS 计划用 SwiftUI，和 ListenUp 的 iOS 壳一样，不和 Android 共用 Compose。
- **分层**：领域模型与仓库接口、数据实现、共享 ViewModel。界面读仓库和播放状态，不自己拼 HTTP。
- **依赖注入**：Koin。共享模块加一个平台模块（HTTP 引擎、SQL 驱动）。`startDependencyInjection()` 的签名里没有 Koin 的 `Module` 类型，避免以后做 Swift 导出时把 DI 框架带进桥接。Android 的 `Application` 自己 `startKoin`，并带上 `androidContext`，和 ListenUp 的分法一样。
- **播放**：共享的 `PlaybackCoordinator` 决定先试哪个地址、超时后换哪一个；`PlaybackCommands` 是壳要提供的播放 / 暂停。这对应 ListenUp 里 `PlayerEngine` 与会话层的分工。Android 的引擎是 `RadioLibraryService`（Media3 `MediaLibraryService` + ExoPlayer）。以后的 iOS 壳用 AVPlayer 实现同一个 `PlaybackCommands`，并把缓冲、错误、曲目名报回 `PlaybackCoordinator`；换相近台（`SimilarStations`）和睡眠定时（`SleepTimer`）也是共享的，壳只负责调用。ListenUp 在 iOS 上是 `NativePlayerEngine` 把指令交给 Swift；这里把这条缝留在接口上，现在不包含 iOS 目标。
- **车机浏览树**：库会话的根下面是收藏、热门、国家，叶子是可播放的电台。ListenUp 也是从 `MediaLibrarySession` 提供一棵浏览树给车机。
- **本地库**：ListenUp 的客户端用 Room（KMP）。随声的收藏和目录缓存很小，用了 SQLDelight，这样收藏测试可以用 JDBC 驱动在本机 JVM 上跑，不必装模拟器。

## 播放和备用地址

`StreamFallback` 生成候选地址：先 `url_resolved`，若与 `url` 不同再试 `url`。空地址会丢掉，两个地址相同只试一次。

`PlaybackCoordinator` 记下当前尝试。引擎报错，或缓冲超过 12 秒还没开始，就换下一个地址。超时由协调器判断：服务每秒把引擎状态（缓冲、播放、暂停）报给 `onClock`，暂停的时间不计入。都失败时状态是 `UNAVAILABLE`。

之后由 `SimilarStations` 按标签或国家找相近的台，跳过这一轮已经试过的，优先选这台手机连通过的，并用一次短连接探测确认流有应答。服务自动这样换台（最多三次）；播放页的「换一座相近的」也调用它。

服务去掉了 seek 相关的 player 命令，并把 `MediaItem` 标成直播，通知和锁屏不显示进度条。

## 构建和运行

需要 JDK 21 和 Android SDK（compileSdk 35）。设置 `ANDROID_HOME`，或在项目根写 `local.properties`：

```
sdk.dir=/path/to/android-sdk
```

```bash
# 调试包
./gradlew :app:androidApp:assembleDebug

# 单元测试（API 解析、备用流、收藏与迁移、镜像选择、相近台、漫游随机、附带目录、睡眠定时）
./gradlew :app:sharedLogic:testDebugUnitTest :app:androidApp:testDebugUnitTest
```

调试 APK：`app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`。

用 Android Studio 打开仓库，运行 `androidApp` 即可。首次播放会请求通知权限，否则 Android 13 及以上可能没有媒体通知。

很多电台只提供 `http://` 流，因此应用允许明文流量。

## 下一步

- **iOS**：在 `sharedLogic` 增加 Apple 目标，用 SwiftUI 做壳。播放实现 `PlaybackCommands`，底层用 AVPlayer，把缓冲、失败和 ICY/元数据交给 `PlaybackCoordinator`。Swift 侧用一个很小的 Flow 桥接（ListenUp 叫 FlowBridge）把共享 ViewModel 的 `StateFlow` 收成 SwiftUI 状态。`startDependencyInjection()` 作为不暴露 Koin 类型的入口。CarPlay 复用同一播放核心和浏览结构（收藏、热门、国家）。
- **国内车机**：HiCar、CarLife 没有纳入这一版。以后如果要接，优先复用现在的库会话浏览树和播放状态，而不是再写一套电台列表。Android Auto 只放了 `automotive_app_desc.xml` 和浏览树，没有在车机上验证。

## 已知限制

- 海外流在中国大陆经常连不上。备用地址、短探测和自动换台能避开一部分死链，解决不了目标服务器本身不可达。附带快照是某一时刻的 Radio Browser 结果，会过期。
- 「只看这台手机连通过的」在第一次成功播放或探测之前是空的。探测只检查当前列表的前 8 座，每次最多几秒。
- 目前公开可解析的 Radio Browser 镜像主要是 `de1` 和 `de2`。旧的 `nl1`、`at1`、`fi1` 主机名已经不存在。
- 标签和国家搜索按 Radio Browser 的规则做精确匹配；名称是部分匹配。国家也可以填两位代码。漫游表盘用的是目录里的国家全名。
- ICY 曲目名取决于电台是否在流里发送，而且有的台会把台名写进标题。没有 favicon 的台，通知里就没有封面。
- 收藏、最近播放、连通记录和目录缓存只在本机，没有账号同步。
- Android Auto / 车机浏览树没有在实车上验证。睡眠定时由播放服务按墙钟倒计时，服务进程被系统杀掉之后不会再响。

界面字体是 [Outfit](https://fonts.google.com/specimen/Outfit) 和 [Newsreader](https://fonts.google.com/specimen/Newsreader)，许可证为 SIL Open Font License，文本在 `third_party/fonts/`。
