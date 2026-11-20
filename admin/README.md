# 金价监控管理后台

Vue 3 + TypeScript + Vite + Element Plus + Vue Router 的基础工程。Element Plus 按使用的组件与样式导入。依赖版本在 `package.json` 中固定，并由 `package-lock.json` 锁定依赖树。当前提供总览、金价数据、监控规则、运行状态页面入口；业务接口尚未定义，因此页面暂不请求真实数据。

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

尚无业务行为测试；构建命令同时运行 TypeScript/Vue 类型检查。`src/api/client.ts` 提供统一 JSON 请求和错误提示。鉴权方式、接口路径、字段与权限规则待 API 契约确定后接入。客户端界面权限只用于展示，服务端负责最终校验。
