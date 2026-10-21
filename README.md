# 随声 / Tune Roam

Android 上的全球网络电台。先把 Android 做完，共享的 Kotlin 核心不依赖 Android，以后可以加一个 iOS 的 SwiftUI 壳。

显示名集中在三处，改名只动这三处：

- 英文：`app/androidApp/src/main/res/values/strings.xml` 的 `app_name`（Tune Roam）
- 中文：`app/androidApp/src/main/res/values-zh/strings.xml` 的 `app_name`（随声）
- 代码常量：`app/sharedLogic/.../core/AppIdentity.kt` 的 `NAME_EN` / `NAME_ZH`（User-Agent 从这里生成）

包名是 `app.tuneroam.radio`，写在 `app/androidApp/build.gradle.kts` 的 `applicationId` 和 `namespace`。

界面上的名字只用字符串资源 `app_name`，不在 Compose 里写死。

## 这一版有什么

- 首页：热门电台，以及按名称、标签、国家搜索。默认隐藏最近检查失败的台（`lastcheckok`）。
- 国家：国家列表和电台数量。首页和国家页有一层 Compose Canvas 星点背景。
- 正在播放：台名、图标、国家、标签、码率 / 编码、健康状态、播放暂停、收藏。有 ICY 元数据时显示当前曲目。
- 收藏存在本机 SQLDelight 里。
- 各页面底部有迷你播放条。
- 后台播放：Media3 `MediaLibraryService`，通知、锁屏、耳机键、蓝牙 / 车机的 AVRCP 信息。流标记为直播，不提供进度条。
- 浏览树：收藏、热门、按国家。`automotive_app_desc.xml` 声明了 `media`。Android Auto 不是这一版的重点（中国大陆不可用）。
- 流失败或在超时内起不来时，先试 `url_resolved`，再试 `url`，并给出说明；可以再点「播放一座相近的电台」。

数据来自 [Radio Browser](https://api.radio-browser.info/) 的公开 API。客户端会先向 `/json/servers` 发现一台服务器，请求带说明性的 User-Agent，播放时调用 `/json/url/{stationuuid}` 记一次点击。

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
- **播放**：共享的 `PlaybackCoordinator` 决定先试哪个地址、超时后换哪一个；`PlaybackCommands` 是壳要提供的播放 / 暂停。这对应 ListenUp 里 `PlayerEngine` 与会话层的分工。Android 的引擎是 `RadioLibraryService`（Media3 `MediaLibraryService` + ExoPlayer）。以后的 iOS 壳用 AVPlayer 实现同一个 `PlaybackCommands`，并把缓冲、错误、曲目名报回 `PlaybackCoordinator`。ListenUp 在 iOS 上是 `NativePlayerEngine` 把指令交给 Swift；这里把这条缝留在接口上，现在不包含 iOS 目标。
- **车机浏览树**：库会话的根下面是收藏、热门、国家，叶子是可播放的电台。ListenUp 也是从 `MediaLibrarySession` 提供一棵浏览树给车机。
- **本地库**：ListenUp 的客户端用 Room（KMP）。随声的收藏和目录缓存很小，用了 SQLDelight，这样收藏测试可以用 JDBC 驱动在本机 JVM 上跑，不必装模拟器。

## 播放和备用地址

`StreamFallback` 生成候选地址：先 `url_resolved`，若与 `url` 不同再试 `url`。空地址会丢掉，两个地址相同只试一次。

`PlaybackCoordinator` 记下当前尝试。引擎报错，或缓冲超过 12 秒还没开始，就换下一个地址。都失败时状态是 `UNAVAILABLE`，界面说明这条流可能在当前网络被阻断，并可以请求一座标签（否则国家）相近的台。

服务去掉了 seek 相关的 player 命令，并把 `MediaItem` 标成直播，通知和锁屏不显示进度条。

## 构建和运行

需要 JDK 21 和 Android SDK（compileSdk 35）。设置 `ANDROID_HOME`，或在项目根写 `local.properties`：

```
sdk.dir=/path/to/android-sdk
```

```bash
# 调试包
./gradlew :app:androidApp:assembleDebug

# 共享模块单元测试（API 解析、备用流、收藏）
./gradlew :app:sharedLogic:testDebugUnitTest
```

调试 APK：`app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`。

用 Android Studio 打开仓库，运行 `androidApp` 即可。首次播放会请求通知权限，否则 Android 13 及以上可能没有媒体通知。

很多电台只提供 `http://` 流，因此应用允许明文流量。

## 下一步

- **iOS**：在 `sharedLogic` 增加 Apple 目标，用 SwiftUI 做壳。播放实现 `PlaybackCommands`，底层用 AVPlayer，把缓冲、失败和 ICY/元数据交给 `PlaybackCoordinator`。Swift 侧用一个很小的 Flow 桥接（ListenUp 叫 FlowBridge）把共享 ViewModel 的 `StateFlow` 收成 SwiftUI 状态。`startDependencyInjection()` 作为不暴露 Koin 类型的入口。CarPlay 复用同一播放核心和浏览结构（收藏、热门、国家）。
- **国内车机**：HiCar、CarLife 没有纳入这一版。以后如果要接，优先复用现在的库会话浏览树和播放状态，而不是再写一套电台列表。Android Auto 只放了 `automotive_app_desc.xml` 和浏览树，没有在车机上验证。

## 已知限制

- 构建环境里没有安装 Android 模拟器，所以没有界面截图。`/dev/kvm` 存在，但 SDK 里没有 emulator 和系统镜像。
- 海外流在中国大陆经常连不上。备用地址能解决一部分「解析地址和原始地址不一致」的情况，解决不了地址本身不可达。
- 标签和国家搜索按 Radio Browser 的规则做精确匹配；名称是部分匹配。国家也可以填两位代码。
- ICY 曲目名取决于电台是否在流里发送，而且有的台会把台名写进标题。
- 收藏和目录缓存只在本机，没有账号同步。
- Android Auto / 车机浏览树没有在实车上验证。

界面字体是 [Outfit](https://fonts.google.com/specimen/Outfit) 和 [Newsreader](https://fonts.google.com/specimen/Newsreader)，许可证为 SIL Open Font License，文本在 `third_party/fonts/`。
