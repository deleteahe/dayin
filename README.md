# dayin

最小 Android 示例工程，用来验证「推代码 → GitHub Actions 编译 → 下载 APK」这条链路。

- 语言：Java（无 Kotlin、无第三方依赖、无 AndroidX）
- 构建：AGP 8.7.3 + Gradle 8.9 + JDK 17
- 产物：`app-debug.apk`（debug 签名，可直接安装）

## 构建

每次推送到 `main`，`.github/workflows/build-apk.yml` 会自动：

1. 编译 `assembleDebug`
2. 把 APK 作为 workflow artifact 上传
3. 创建 tag 为 `build-<run_number>` 的 Release，并把 APK 挂上去

APK 下载地址：仓库 Releases 页面，或
`https://github.com/<owner>/<repo>/releases/latest`。

## 本地构建

```bash
gradle :app:assembleDebug
```
