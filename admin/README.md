# 金价监控管理后台

Vue 3 + TypeScript + Vite + Element Plus + Vue Router 的基础工程。Element Plus 按使用的组件与样式导入。依赖版本在 `package.json` 中固定，并由 `package-lock.json` 锁定依赖树。当前提供管理员登录、用户管理、支付记录查询与商品管理页面，按 API v1 契约请求服务端。

## 开发

需要 Node.js 20.19+ 或 22.12+、npm 10+。

```sh
cp .env.example .env.local
npm ci
npm run dev
```

默认地址为 `http://127.0.0.1:5173`。`VITE_API_BASE_URL` 是公开的 API 基础地址，可留空使用同源请求；不要放入密钥。部署时需将非静态路径回退到 `index.html`，并配置 API 的跨域或同源代理。

## 检查与构建

```sh
npm run typecheck
npm run build
npm run preview
```

构建命令同时运行 TypeScript/Vue 类型检查。`src/api/client.ts` 提供统一 JSON 请求、管理员令牌和错误提示。服务端负责最终权限校验。

## 管理功能

管理后台提供管理员登录、用户列表与详情（含支付记录）、用户密码/状态/有效期修改、支付记录查询和商品增删改查。API 契约见 `../api/API_CONTRACT.md`。登录令牌存储于当前浏览器标签页的 `sessionStorage`，退出会调用服务端撤销会话并清除本地令牌；401 响应也会清除令牌并跳转登录页。`VITE_API_BASE_URL` 指向 API 服务，例如 `http://localhost:8080`；跨域部署时服务端需配置相应 CORS 或使用同源代理。
