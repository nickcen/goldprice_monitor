# API 服务

返回[工程架构](../PROJECT_ARCHITECTURE.md)，参阅[公共约定](COMMON_CONVENTIONS.md)。

## 职责与技术栈

API 服务供管理后台和桌面应用共同使用，负责接入外部金价数据源、处理金价查询和监控配置等业务、持久化数据、提供 API，以及执行鉴权和权限校验。具体功能以产品需求为准。

技术栈为 Java 21、Spring Boot、MyBatis。框架的具体版本和数据库选型在项目初始化时确定，并锁定在构建配置中。

## 建议目录结构

```text
api/
├── src/
│   ├── main/
│   │   ├── java/<package>/
│   │   │   ├── controller/      # HTTP 接口与参数校验
│   │   │   ├── service/         # 业务规则
│   │   │   ├── mapper/          # MyBatis 数据访问
│   │   │   ├── model/           # 请求、响应与领域对象
│   │   │   ├── integration/     # 金价数据源适配
│   │   │   └── config/          # 应用配置
│   │   └── resources/          # 配置与 Mapper SQL
│   └── test/                   # 服务端测试
├── <build-file>                # 构建文件
└── README.md
```

`<package>` 和 `<build-file>` 为占位符。

## 技术规范

- 控制器负责协议适配与参数校验；业务规则放在服务层；数据库访问通过 MyBatis Mapper 实现。
- API 遵循[公共 API 契约](COMMON_CONVENTIONS.md#api-契约)，统一处理异常并返回稳定的错误结构。
- 金价使用精确数值类型，明确单位、币种和精度。
- 数据源访问封装在独立模块，配置超时、错误处理及按需的重试和限流。
- 数据库连接、密钥和第三方凭据由环境配置注入，不提交真实凭据。
- 日志记录关键操作及失败原因，不输出密码、令牌等敏感信息。
- README 记录 Java 版本、启动、构建、测试命令及配置项。
