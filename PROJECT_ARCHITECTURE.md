# 金价监控工程架构

本工程由三个独立项目组成。管理后台和桌面应用通过 HTTP API 使用同一个 API 服务；API 服务负责业务规则、外部金价数据接入和持久化。

```text
管理后台（Vue） ─────┐
                    ├── HTTP API ── API 服务（Java 21 / Spring Boot / MyBatis）
桌面应用（Tauri 2.0） ┘                  ├── 数据存储
                                       └── 外部金价数据源
```

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [API 服务](docs/API_SERVICE.md) | 服务端职责、目录结构、技术规范 |
| [管理后台](docs/ADMIN.md) | Vue 项目职责、目录结构、技术规范 |
| [桌面应用](docs/DESKTOP.md) | Tauri 项目职责、目录结构、技术规范 |
| [公共约定](docs/COMMON_CONVENTIONS.md) | API 契约、配置、安全和交付约定 |
| [数据库设计](docs/DATABASE_DESIGN.md) | 用户、商品和支付记录表的初版结构 |
| [待确定事项](docs/OPEN_DECISIONS.md) | 尚需确定的业务和技术选型 |

## 仓库规划

```text
goldprice_monitor/
├── api/                       # API 服务，规划目录
├── admin/                     # 管理后台，规划目录
├── desktop/                   # 桌面应用，规划目录
├── docs/                      # 项目规范文档
├── PROJECT_ARCHITECTURE.md
└── README.md
```

目前仓库尚未包含三个项目的代码。各项目目录结构是实施建议，创建项目时可按实际选型调整，并同步更新对应文档。
