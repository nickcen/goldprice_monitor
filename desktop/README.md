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

`VITE_API_BASE_URL` 为 API 根地址，不能包含令牌。该地址在构建时写入应用；未配置时应用会显示配置提示，无法登录。桌面端从 API 获取用户配置后，使用用户令牌请求 `/api/v1/gold-price/at` 获取当前分钟金价。API 需允许桌面 WebView 来源的跨域请求以完成登录和配置操作。

JWT 存入 macOS 钥匙串或 Windows 凭据库。登出及会话失效时清除。关闭主窗口后应用留在托盘中继续监控；点击托盘图标切换窗口。macOS 菜单栏显示价格文字，Windows 托盘悬停提示显示价格。通知需要系统权限。

首页显示当天金价折线图。成功获取的价格按设备本地日期与分钟保存在应用数据目录的 SQLite 数据库，同一分钟以最后一次成功价格为准。启动、每小时及从休眠恢复时清理非当天记录。浏览器开发模式使用 localStorage 模拟存储；正式桌面应用使用 SQLite。

## 检查与构建

```sh
npm run build
npm run tauri info
npm run tauri build
```

构建产物在 `src-tauri/target/release/bundle/`。Windows 与 macOS 均需实机验证托盘、通知和凭据库。自动更新的签名及发布配置留待后续任务。
