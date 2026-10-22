# 参与贡献

欢迎提 Issue 和 PR。动手写大一点的改动之前，先开个 Issue 说一下想法，免得白做。

## 报告问题

- **某个台放不了**：附上台名和 `stationuuid`（分享链接 `tuneroam://station/…` 里就有），以及你所在的国家或地区、用的是 Wi-Fi 还是移动网络。很多台只是在某些地区连不上，这条信息最重要。
- **崩溃**：设置页「崩溃日志」可以直接分享，贴进 Issue 即可。日志里有时间、版本、Android 版本、机型和异常堆栈，没有账号或其他个人信息，发之前也可以自己看一眼。
- 写上版本号（设置页底部）、手机型号和 Android 版本。

## 开发

构建和测试命令见 [README](README.md#构建和运行)。提交 PR 之前请在本地跑通单元测试：

```bash
./gradlew :app:sharedLogic:testDebugUnitTest :app:androidApp:testDebugUnitTest
```

CI 会再跑一遍测试、`assembleDebug` 和 `assembleRelease`（R8），都通过才会合并。

几条约定：

- `sharedLogic` 的 `commonMain` 不引用 `android.*` 和 JVM API，平台相关的代码放 `androidMain` 或 `androidApp`。
- 界面上的文字写进字符串资源，`values/strings.xml`（英文）和 `values-zh/strings.xml`（中文）一起改，不在 Compose 里写死。
- 改了行为就补测试；改了 README 里描述过的行为，README 也一起改。
- `scripts/refresh_bundled_snapshot.py` 和 `RadioBrowserSnapshot` 的规则要保持一致，两边一起改。
- 一个 PR 做一件事，提交信息用一句完整的话说清楚改了什么。

## 许可

提交的代码按本项目的 [MIT](LICENSE) 许可发布。
