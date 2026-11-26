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

`VITE_API_BASE_URL` 为 API 根地址，不能包含令牌。桌面端仅请求 API 的 `/api/v1/gold-price/latest`，不直接访问外部金价源。API 用 `APP_PRICE_SOURCE_URL` 连接外部数据源，返回统一的单位、币种、精度和十进制字符串价格。API 需允许桌面 WebView 来源的跨域请求。

JWT 存入 macOS 钥匙串或 Windows 凭据库。登出及会话失效时清除。关闭主窗口后应用留在托盘中继续监控；点击托盘图标切换窗口。macOS 菜单栏显示价格文字，Windows 托盘悬停提示显示价格。通知需要系统权限。

## 检查与构建

```sh
npm run build
npm run tauri info
npm run tauri build
```

构建产物在 `src-tauri/target/release/bundle/`。Windows 与 macOS 均需实机验证托盘、通知和凭据库。自动更新的签名及发布配置留待后续任务。
