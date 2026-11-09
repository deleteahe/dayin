# 错题特训（WebView 套壳）

把 `https://ks.hxda.cc` 装成一个 Android App。站点用的是 nginx 的 HTTP Basic 认证，
WebView 默认会直接取消这种请求，所以客户端接管了认证流程。

## 特性

- WebView 加载站点，开启 JS / DOM Storage / Cookie 持久化
- HTTP Basic 认证：`onReceivedHttpAuthRequest` 接管，首次打开弹窗输入账号
- 凭据存本机 `SharedPreferences`（应用私有目录），**不写进代码、不进仓库**
- 返回键优先回退网页历史
- 顶部细进度条、断网重试页
- 站内链接留在 WebView，站外链接 / `mailto:` / `tel:` 交给系统
- 菜单：清除已保存的账号、重新加载
- 禁用混合内容、禁用文件访问（`usesCleartextTraffic=false`，只允许 HTTPS）

## 构建

推送到 `main` 会自动触发 `.github/workflows/build-apk.yml`：

1. `gradle :app:assembleDebug`
2. 上传 workflow artifact
3. 创建 `build-<run_number>` 的 Release，挂上 `app-debug.apk`

## 安全提示

APK 是 debug 签名，只适合自己安装使用，不要上传到任何应用市场。
账号密码保存在手机本地，App 本身不联网做任何额外上报。
