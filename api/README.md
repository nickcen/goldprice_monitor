# API 服务

Java 21、Spring Boot 3.5.6、MyBatis Spring Boot Starter 3.0.5、MySQL Connector/J 9.4.0 的最小工程。使用 Spring Boot 管理的 Flyway 版本执行 MySQL 迁移。当前仅提供 Spring Boot Actuator 健康检查；业务接口和规则待产品需求确定。

## 环境

安装 JDK 21、Maven 3.9+ 和 MySQL 8.0。创建 `goldprice_monitor` 数据库及权限受限的应用用户。连接参数通过环境变量提供，不要提交真实凭据。可参考 `.env.example`；该文件不会被应用自动加载。

| 变量 | 用途 | 开发默认值 |
| --- | --- | --- |
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/goldprice_monitor?connectionTimeZone=UTC` |
| `DB_USER` | MySQL 用户 | `goldprice_monitor` |
| `DB_PASSWORD` | MySQL 密码 | 空值，仅供本地配置 |

## 构建、测试与启动

在 `api/` 目录执行：

```sh
mvn clean verify
mvn spring-boot:run
```

健康检查位于 `GET http://localhost:8080/actuator/health`，返回 Spring Boot 标准 JSON。数据库不可用时健康状态为 `DOWN`，这是预期行为。测试关闭数据库健康探测，以验证 HTTP 端点而无需外部 MySQL。生产环境应通过部署系统注入数据库凭据；不要把密码写入仓库或命令行。

## 数据库迁移

应用启动时，Flyway 从 `src/main/resources/db/migration` 执行尚未应用的版本化 SQL，并在数据库中维护 `flyway_schema_history`。`V1__initial_schema.sql` 实现 `../docs/DATABASE_DESIGN.md` 中的初版四张表。后续结构调整请新增 `V2__...sql` 等迁移文件，不修改已经部署过的脚本。Flyway 与应用共用 `DB_URL`、`DB_USER`、`DB_PASSWORD`，数据库用户须有建表和修改表的权限；生产环境可使用独立的迁移账号和部署步骤。

首次运行需要空数据库。如果现有数据库已经手动建了表，请先核对其结构与 V1 一致，并制定基线方案；当前配置关闭自动基线，不会跳过已有表。测试中的 HTTP 健康检查显式关闭 Flyway，以免依赖外部 MySQL。

尚未定义业务 API，因此没有添加空的 controller、service 或 mapper。

## Business API

See [API_CONTRACT.md](API_CONTRACT.md). Set `APP_JWT_SECRET` to a unique secret of at least 32 characters before startup. Set `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` together to create an initial admin account if it does not exist; changing these environment values does not reset an existing password. Configure `APP_PRICE_SOURCE_URL` to a trusted JSON price endpoint. `APP_CURRENCY` (default `CNY`), `APP_PRICE_UNIT` (default `gram`), `APP_MONITOR_INTERVAL_SECONDS` (default 60), and `APP_PUBLIC_BASE_URL` (default `http://localhost:8080`) control the initial user configuration. `DB_URL`, `DB_USER`, and `DB_PASSWORD` configure MySQL. `APP_DESKTOP_ORIGINS` sets the comma-separated allowed desktop CORS origins; defaults include macOS Tauri, Windows Tauri, and the local Vite development origin. No production price source or admin credentials are committed.
