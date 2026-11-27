# 桌面应用

返回[工程架构](../PROJECT_ARCHITECTURE.md)，参阅[公共约定](COMMON_CONVENTIONS.md)。

桌面端具体功能见[前端功能说明](DESKTOP_FUNCTIONS.md)。

## 职责与技术栈

桌面应用使用 Tauri 2.0，提供金价查看与监控体验。可按需求增加系统托盘、桌面通知和本地偏好设置。用户与监控配置通过 API 服务访问；金价按 API 下发的 URL 获取，可能来自 API 服务或外部数据源。本地只保存必要的设置或缓存。

首批目标操作系统为 Windows 和 macOS，桌面前端采用 Vite + TypeScript（原生 DOM）。计划使用 Tauri 更新机制，更新签名与发布配置尚待确定；开发与构建命令见 [desktop/README.md](../desktop/README.md)。

## 建议目录结构

```text
desktop/
├── src/                        # 桌面前端，内部结构随框架确定
│   ├── api/                    # API 请求封装
│   ├── components/             # 界面组件
│   └── platform/               # 桌面能力调用封装
├── src-tauri/
│   ├── src/                    # Rust 入口与原生命令
│   ├── capabilities/           # Tauri 权限配置
│   ├── icons/                  # 应用图标
│   ├── Cargo.toml
│   └── tauri.conf.json
├── package.json                # 按前端选型调整
└── README.md
```

## 技术规范

- 通过统一的 API 客户端访问服务端，遵循[公共 API 契约](COMMON_CONVENTIONS.md#api-契约)。
- 通知、托盘、文件访问等桌面能力集中封装，并按实际功能配置最小权限。
- 业务数据以 API 服务为准；本地缓存定义失效和刷新方式。
- 访问令牌等敏感信息采用适合目标平台的安全存储方式，避免明文写入普通配置文件或日志。
- API 基础地址通过应用配置提供，不在业务代码中写死。
- 桌面端展示日期时间时统一使用本地时区的 `YYYY-MM-DD HH:mm:ss` 格式（年-月-日 时:分:秒），月、日、时、分、秒均补齐两位；统一调用 `desktop/src/dateTime.ts` 的 `formatDateTime`，不要直接使用 `toLocaleString`。
- README 记录目标平台、开发与构建命令、签名和更新流程。
