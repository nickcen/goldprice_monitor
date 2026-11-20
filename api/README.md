# API 服务

Java 21、Spring Boot 3.5.6、MyBatis Spring Boot Starter 3.0.5、MySQL Connector/J 9.4.0 的最小工程。当前仅提供 Spring Boot Actuator 健康检查；业务接口和规则待产品需求确定。

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

数据库表的初稿见 `../docs/DATABASE_DESIGN.md`。本工程不自动建表或迁移；数据库版本与迁移方案仍待确定。尚未定义业务 API，因此没有添加空的 controller、service 或 mapper。
