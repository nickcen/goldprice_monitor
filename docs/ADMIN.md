# 管理后台

返回[工程架构](../PROJECT_ARCHITECTURE.md)，参阅[公共约定](COMMON_CONVENTIONS.md)。

## 职责与技术栈

管理后台使用 Vue 和 Element Plus，提供浏览器中的管理界面。具体页面以[管理后台功能说明](ADMIN_FUNCTIONS.md)为准。业务数据通过 API 服务获取和修改。

Vue 和 Element Plus 的版本、构建工具、JavaScript 或 TypeScript 及状态管理方案在项目初始化时确定，并记录在本项目 README 中。

## 建议目录结构

```text
admin/
├── public/             # 静态资源
├── src/
│   ├── api/            # API 请求封装
│   ├── assets/         # 页面资源
│   ├── components/     # 可复用组件
│   ├── router/         # 页面路由
│   ├── views/          # 业务页面
│   ├── stores/         # 状态管理，按选型调整
│   └── App.vue
├── package.json
└── README.md
```

## 技术规范

- UI 风格采用 `npx getdesign@latest add vercel` 提供的 Vercel 设计规范；项目初始化时执行该命令，并以生成的规范指导页面视觉设计。
- 页面布局、表单、表格、分页、对话框、消息提示等界面与交互优先使用 Element Plus 提供的组件；现有组件无法满足需求时再开发自定义组件。
- 页面组件负责展示和交互；API 请求集中封装，统一处理鉴权、错误提示和加载状态。
- 接口字段和错误处理遵循[公共 API 契约](COMMON_CONVENTIONS.md#api-契约)。
- 路由与按钮权限用于界面控制，最终权限由 API 服务校验。
- API 基础地址通过环境配置提供，不在业务代码中写死。
- 前端构建产物不包含服务端密钥或其他私密凭据。
- README 记录开发、构建、测试命令及环境变量用途。
