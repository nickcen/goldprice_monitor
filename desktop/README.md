# 桌面应用

Tauri 2 + Vite + TypeScript，目标平台 macOS 与 Windows。实现注册、登录、配置、金价轮询、越界通知与系统托盘。自动更新暂不实现。

## 本地开发

需要 Node.js 20.19+ 或 22.12+、npm、Rust stable 及 Tauri 2 系统依赖。macOS 需要 Xcode Command Line Tools；Windows 需要 Microsoft C++ Build Tools 和 WebView2。API 需按 `api/README.md` 配置并启动。

```sh
cd desktop
npm ci
cp .env.example .env
npm run tauri dev
```

`VITE_API_BASE_URL` 为 API 根地址，不能包含令牌。该地址在构建时写入应用；未配置时应用会显示配置提示，无法登录。桌面端从 API 获取用户配置后，直接请求配置中的 `goldPriceUrl`，并解析金价。Tauri 桌面应用通过原生网络请求获取金价，以免受到 WebView 跨域限制；浏览器开发模式仍使用浏览器 `fetch`，外部数据源需要允许开发来源跨域。API 需允许桌面 WebView 来源的跨域请求以完成登录和配置操作。

JWT 存入 macOS 钥匙串或 Windows 凭据库。登出及会话失效时清除。关闭主窗口后应用留在托盘中继续监控；点击托盘图标切换窗口。macOS 菜单栏显示价格文字，Windows 托盘悬停提示显示价格。通知需要系统权限。

## 检查与构建

```sh
npm run build
npm run tauri info
npm run tauri build
```

构建产物在 `src-tauri/target/release/bundle/`。Windows 与 macOS 均需实机验证托盘、通知和凭据库。自动更新的签名及发布配置留待后续任务。
