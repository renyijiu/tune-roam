# 随声 / Tune Roam

Android 上的全球网络电台。共享的 Kotlin 核心不依赖 Android，以后可以加一个 SwiftUI 的 iOS 壳。还没有正式发布。

数据来自 [Radio Browser](https://api.radio-browser.info/) 的公开 API。灵感来自网页电台 [RoamDial](https://www.v2ex.com/t/1249321)：按世界各地浏览电台。随声不是 RoamDial，名字、包名和代码都与它无关。

## 功能

- **漫游**：Compose Canvas 的表盘。左右拨动，针下的国家会换一座可播的电台。表盘跟着正在播的台转；台所在的国家不在表盘上时，表盘不动，台名下面标出实际国家。
- **首页**：热门电台，按名称、标签、国家搜索。默认隐藏最近检查失败的台（`lastcheckok`）；可以只看这台手机连通过的台，也可以对当前列表做一次短超时探测。
- **国家**：国家列表和电台数量，点进去是该国电台。
- **下一站 / 上一站**：下一站从实时目录随机抽一批（连不上时用随包快照），后台先探测好后面三座能连通的，按下去通常马上就有声音。上一站沿本次播放过的台往回走。播放页、迷你播放条、通知、锁屏、耳机、蓝牙 / 车机的上一首、下一首都是这两个动作。「随便一座」就是下一站。
- **续听**：打开应用时，迷你播放条停在上次真正播出过的台，点播放就接着听。应用没运行时（包括重启手机之后），系统媒体控件、耳机或车机按播放也会接着放这座台。
- **正在播放**：台名、图标、国家、标签、码率 / 编码、健康状态、收藏、睡眠定时、分享。电台发了 ICY 曲名时，界面、通知和锁屏都显示当前曲目。
- **曲目收藏**：曲名下面有心形和搜索两个按钮。心形把这首歌收进「收藏」页的「曲目」栏；搜索可以选网易云音乐、QQ 音乐或 Spotify。
- **分享**：`tuneroam://station/{stationuuid}` 链接。装了应用的手机点开就会播这座台。
- **设置**（漫游页和首页右上角的齿轮）：下一站筛选（码率下限、语言、只听 / 不要哪些类型）、音量统一（默认打开）、崩溃日志（分享或清除）、版本号。
- **后台播放**：Media3 `MediaLibraryService`，通知、锁屏、耳机键、蓝牙 / 车机的 AVRCP 信息。来电或耳机拔出时暂停。
- **浏览树**：收藏、热门、按国家，供车机和系统媒体控件使用。

## 目录

```
app/sharedLogic/     Kotlin Multiplatform 核心（目前只有 Android 目标）
  core/              应用标识、时间、JSON 配置
  domain/            电台模型、目录接口、筛选、播放策略（PlaybackCoordinator、RoamDeck 等）
  data/              Ktor 客户端、随包快照、SQLDelight 存储
  presentation/      ViewModel（界面状态，不含 Compose）
  di/                Koin 模块；平台只提供 HTTP 引擎和数据库驱动
app/androidApp/      Compose 界面、Media3 服务、音量统一、崩溃日志
scripts/             随包电台快照的生成脚本
```

`sharedLogic` 的 `commonMain` 不引用 `android.*` 和 JVM API（时间走 `core/Platform.kt` 的 `epochMillis()`）。Android 专用代码在 `androidMain`（OkHttp、SQLDelight 的 Android 驱动）和 `androidApp`（ExoPlayer、Compose）。

### 分层

分层方式参考了 [ListenUp](https://github.com/ListenUpApp/ListenUp)（AGPL-3.0 的有声书应用），只沿用结构，没有复制源码。

- **模块**：核心在 `app/sharedLogic`，Android 应用是薄的 `app/androidApp`。界面不放进共享模块，因为 iOS 计划用 SwiftUI。
- **分层**：领域模型与仓库接口、数据实现、共享 ViewModel。界面读仓库和播放状态，不自己拼 HTTP。
- **依赖注入**：Koin。共享模块加一个平台模块（HTTP 引擎、SQL 驱动）。`startDependencyInjection()` 的签名里没有 Koin 类型，留给以后的 Swift 壳；Android 的 `Application` 自己 `startKoin` 并带上 `androidContext`。
- **播放**：共享的 `PlaybackCoordinator` 决定先试哪个地址、什么时候换；`PlaybackCommands` 是壳要提供的播放 / 暂停 / 上一站 / 下一站。Android 的实现是 `RadioLibraryService`（`MediaLibraryService` + ExoPlayer）。换相近台（`SimilarStations`）、下一站（`RoamDeck`）和睡眠定时（`SleepTimer`）都在共享核心，壳只负责调用。
- **本地库**：SQLDelight。收藏和缓存都很小；测试可以用 JDBC 驱动直接在 JVM 上跑，不必装模拟器。

## 实现要点

### 目录和镜像

- `ServerDirectory` 同时探测记住的镜像和内置镜像（`de1`、`de2`），**第一个应答的就用**并记下来；都不应答时才查 `/json/servers`。并发请求共用一次选择。一个请求最多换三台镜像：网络错误或 5xx 换下一台，4xx 直接抛出。被取消的请求不算镜像故障。
- 请求带说明性的 User-Agent（`AppIdentity.USER_AGENT`），播放时调用 `/json/url/{stationuuid}` 记一次点击。
- `OfflineFirstCatalog`：先给出本机缓存（没有缓存时用随包快照），再请求实时目录，成功后写回缓存。热门、国家列表、各国电台会缓存；自由搜索不缓存，离线时在随包快照里搜。
- 随包快照（`BundledSnapshotJson.kt`）含大陆、台湾、香港、新加坡的中文台和一部分全球热门，生成方式见「随包电台快照」。
- 接口返回的电台按 uuid 去重。

### 连通探测

- `KtorStationConnectivity` 只读响应头（`prepareGet { }.execute`）。大多数流忽略 `Range`、永远不结束，读 body 会让能播的台也被判成超时。
- 每座台的连通结果记在本机（`reachability` 表）。首页「这台手机连通过」、下一站排序、坏台时的换台都会参考它。

### 播放和备用地址

- `StreamFallback` 生成候选地址：先 `url_resolved`，与 `url` 不同时再试 `url`。空地址丢掉，相同的只试一次。
- `PlaybackCoordinator` 记下当前尝试。引擎报错，或缓冲 12 秒还没开始，就换下一个地址。服务每秒把引擎状态报给 `onClock`，超时只由协调器判断，暂停的时间不计入。都失败时状态是 `UNAVAILABLE`。
- 之后 `SimilarStations` 按标签或国家找相近的台，跳过这一轮试过的，优先这台手机连通过的，并做一次短探测。服务自动这样换台（最多三次）；播放页的「换一座相近的」也走它。自动换到的台不进「上一站」历史。
- 服务去掉了 seek 命令，`MediaItem` 标成直播，不显示进度条。暂停超过 30 秒再继续，会从直播点重新连接，而不是先放旧缓冲。调音量不会暂停播放。
- 睡眠定时由播放服务倒计时，离开播放页也照样会停；重新设定会取消旧的倒计时。

### 下一站

- `RoamDeck` 管下一站、上一站和预探测。后台保持三座探测连通的台；冷启动时当场最多试三座。离线时预探测最多跑 12 次就停。在这台手机上失败过的台排到后面。
- 牌堆来自 Radio Browser 的 `order=random`，一次取一批，用完再取。
- 播放服务把 ExoPlayer 包在 `RoamingPlayer`（`ForwardingPlayer`）里交给媒体会话，对外提供 next / previous。界面按钮也经过媒体会话，和外部控制走同一条路径。
- 每次取台前对照当前筛选（`RoamFilter`），筛选变了就丢掉预探测好的台（历史保留）；设置页改完筛选会立刻按新条件预探测。只选了一个语言或一个类型时，筛选直接写进随机查询；多选时在手机上筛。码率未知（多为 HLS）的台不会被码率下限挡掉。

### 音量统一

`LoudnessLeveler` 是插进 ExoPlayer 音频输出链的 `AudioProcessor`：

- 按 50 ms 一段测 RMS，用约 3 秒的慢速估计把增益推向约 −18 dBFS，范围 ±12 dB。
- 调低快（6 dB/s）、调高慢（2 dB/s）；提升时按峰值限制，不会削波。
- 约 −50 dBFS 以下当作静音，不参与估计。每个新流从增益 1 开始。
- 只处理 16 位 PCM（ExoPlayer 默认的解码输出），其他格式原样通过。

### 续听、分享、曲目

- `HistoryRepository.latest()` 取最后一座真正播出的台。冷启动时 `PlaybackCoordinator.restore()` 把它显示出来但不连接；已经有台在播时不生效。播放服务实现了 Media3 的 `onPlaybackResumption`，清单里声明了 `android.media.browse.MediaBrowserService`，系统媒体控件才能在应用不在运行时恢复播放。
- `StationLink` 生成和解析分享链接（只接受合法 uuid）。打开链接时先查收藏，再查 `/json/stations/byuuid/{uuid}`，最后查随包快照。
- 迷你播放条、播放页和打开链接共用一个 Activity 范围的 `PlayerViewModel`，一处的提示另一处也能看到。
- `TrackTitle` 按 ` - ` 拆出歌手和歌名；`titleKey` 是去掉多余空格、转小写后的曲名，表里唯一，所以同一首歌换个台也只存一次。`MusicService` 生成三家的网页搜索地址；装了对应应用并认领了域名时系统直接用应用打开（网易云的地址带 `#` 路由，通常在浏览器打开）。

### 本地数据

- SQLDelight 的表都在 `Catalog.sq`：收藏、目录缓存、国家缓存、连通记录、最近播放（最多 40 条）、镜像记忆、曲目收藏、设置。
- 设置（`AppSettings`）作为一行 JSON 存在 `setting` 表里，加字段只要给默认值；这一行解析不了时按默认设置处理。
- **还没发布，所以没有数据库迁移**：表结构直接改 `Catalog.sq`，开发机上清数据或重装即可。第一次发布之后，每次改表都要加 `.sqm` 迁移和对应测试。

### 崩溃日志

`CrashLog` 把未捕获异常写到应用私有目录 `files/crashes/`，最多保留 5 份，可在设置页分享。不接任何第三方服务。

## 构建和运行

需要 JDK 21 和 Android SDK（compileSdk 35）。设置 `ANDROID_HOME`，或在项目根写 `local.properties`：

```
sdk.dir=/path/to/android-sdk
```

调试包：

```bash
./gradlew :app:androidApp:assembleDebug
```

单元测试：

```bash
./gradlew :app:sharedLogic:testDebugUnitTest :app:androidApp:testDebugUnitTest
```

调试 APK 在 `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`。也可以用 Android Studio 打开仓库直接运行 `androidApp`。首次播放会请求通知权限，否则 Android 13 及以上可能没有媒体通知。很多电台只提供 `http://` 流，因此应用允许明文流量。

CI（`.github/workflows/ci.yml`）在每个 PR 和推到 `main` 时跑单元测试、`assembleDebug` 和未签名的 `assembleRelease`（检查 R8 和资源压缩）。调试 APK 作为构建产物保留 14 天；测试失败时附上测试报告。

### 改名

- 界面上的名字只用字符串资源 `app_name`：`values/strings.xml`（Tune Roam）和 `values-zh/strings.xml`（随声），不在 Compose 里写死。
- User-Agent 和版本号在 `app/sharedLogic/.../core/AppIdentity.kt`；`VERSION` 要和 `app/androidApp/build.gradle.kts` 的 `versionName` 保持一致。
- 包名 `app.tuneroam.radio` 写在 `app/androidApp/build.gradle.kts` 的 `applicationId` 和 `namespace`。

### 发布

release 构建打开 R8 混淆和资源压缩（release APK 约 3.6 MB，debug 约 27 MB）。签名信息从仓库根目录的 `keystore.properties` 读取（这个文件和 `*.jks` 已加入 `.gitignore`）：

```
storeFile=release.jks
storePassword=…
keyAlias=tuneroam
keyPassword=…
```

没有这个文件时改从环境变量读取：`TUNEROAM_KEYSTORE`、`TUNEROAM_KEYSTORE_PASSWORD`、`TUNEROAM_KEY_ALIAS`、`TUNEROAM_KEY_PASSWORD`。两处都没有时，`assembleRelease` 照样构建，产出未签名的 APK。第一次生成密钥：

```bash
keytool -genkeypair -v -keystore release.jks -alias tuneroam -keyalg RSA -keysize 4096 -validity 10000
```

密钥丢了就无法再给已安装的用户推送更新，请另外备份。

### 随包电台快照

只看会取到哪些台：

```bash
python3 scripts/refresh_bundled_snapshot.py --check
```

重写 `BundledSnapshotJson.kt`：

```bash
python3 scripts/refresh_bundled_snapshot.py
```

快照的组成：大陆 50、台湾 25、香港 20、新加坡（只要中文台）15，再加 40 座全球热门。只收上次检查正常、有流地址的台；不足 100 座时脚本拒绝覆盖。`.github/workflows/refresh-snapshot.yml` 每月 1 日运行一次（也能手动触发），快照有变化时开 PR；这需要在仓库的 Settings → Actions → General 里允许 GitHub Actions 创建 PR。

## 下一步

- **iOS**：在 `sharedLogic` 增加 Apple 目标，用 SwiftUI 做壳。播放实现 `PlaybackCommands`，底层用 AVPlayer，把缓冲、失败和 ICY 元数据交给 `PlaybackCoordinator`。Swift 侧用一个很小的 Flow 桥接把共享 ViewModel 的 `StateFlow` 收成 SwiftUI 状态。CarPlay 复用同一播放核心和浏览结构。
- **国内车机**：HiCar、CarLife 还没接。以后要接时，优先复用现在的库会话浏览树和播放状态。

## 已知限制

- 海外流在中国大陆经常连不上。备用地址、短探测和自动换台能避开一部分死链，解决不了目标服务器本身不可达。随包快照是某一时刻的结果，会过期。
- 「只看这台手机连通过的」在第一次成功播放或探测之前是空的。探测只检查当前列表的前 8 座，每次最多几秒。
- 目前公开可解析的 Radio Browser 镜像主要是 `de1` 和 `de2`（旧的 `nl1`、`at1`、`fi1` 已经不存在）。
- 标签和国家搜索按 Radio Browser 的规则精确匹配，名称是部分匹配；国家也可以填两位代码。漫游表盘用的是目录里的国家全名。
- ICY 曲目名取决于电台是否发送，有的台会把台名写进标题。没有 favicon 的台，通知里没有封面。
- 收藏、最近播放、连通记录和目录缓存只在本机，没有账号同步。
- 音量统一只能边播边测，刚换台的头一两秒仍会偏大或偏小；也不会把一首歌内部的强弱压平。
- 筛选只作用于下一站和随便一座，不作用于表盘选国家和坏台时的自动换台。
- 分享链接是自定义 scheme：没装应用的人点开没有反应，有的聊天应用也不识别成链接。换成 https App Link 需要一个应用自己的域名。
- Android Auto 只声明了 `automotive_app_desc.xml` 并提供浏览树，没有在实车上验证（中国大陆也不可用）。睡眠定时按墙钟倒计时，服务进程被系统杀掉之后不会再响。

## 许可

界面字体是 [Outfit](https://fonts.google.com/specimen/Outfit) 和 [Newsreader](https://fonts.google.com/specimen/Newsreader)，许可证为 SIL Open Font License，文本在 `third_party/fonts/`。
