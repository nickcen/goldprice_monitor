# 桌面应用

Tauri 2 + Vite + TypeScript 基础工程，目标平台为 macOS 和 Windows。当前仅提供启动界面与通用 JSON API 客户端；业务页面待 API 契约确定后实现。

## 本地开发

需要 Node.js 20.19+ 或 22.12+、npm、Rust stable、Tauri 2 对应的系统依赖。macOS 需要 Xcode Command Line Tools；Windows 需要 Microsoft C++ Build Tools 和 WebView2。

```sh
cd desktop
npm install
cp .env.example .env
npm run tauri dev
```

`VITE_API_BASE_URL` 是 API 服务根地址，可在 `.env` 中按环境设置。`VITE_` 变量会被编入前端资源，不能放令牌或服务端密钥。API 客户端在 `src/api/client.ts`，只接受相对路径，并可逐次传入内存中的访问令牌；应用尚不持久化令牌。API 服务需要允许桌面 WebView 的请求来源及跨域预检。

## 检查与构建

```sh
npm run build
npm run tauri info
npm run tauri build
```

构建产物在 `src-tauri/target/release/bundle/`。当前未配置自动更新。发布 macOS/Windows 安装包前，需分别配置平台签名、证书和发布流程；签名密钥只放 CI 密钥库或本机安全存储，绝不提交。`desktop/.env`、`target/`、`dist/` 均被忽略。

`docs/DESKTOP_FUNCTIONS.md` 当前仓库不存在，后续功能实现应以该文档和 API 契约为准。
