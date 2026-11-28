# API 服务

Java 21、Spring Boot 3.5.6、MyBatis Spring Boot Starter 3.0.5、MySQL Connector/J 9.4.0 的最小工程。使用 Spring Boot 管理的 Flyway 版本执行 MySQL 迁移。当前仅提供 Spring Boot Actuator 健康检查；业务接口和规则待产品需求确定。

## 环境

安装 JDK 21、Maven 3.9+ 和 MySQL 8.0。创建 `goldprice_monitor` 数据库及权限受限的应用用户。默认启用 `local` profile，`application-local.yml` 可从仓库根目录或 `api/` 目录加载根目录的 `.env.local`。可参照根目录的 `.env.example` 创建该文件。生产环境由 systemd 设置 `SPRING_PROFILES_ACTIVE=prod`，`application-prod.yml` 加载服务工作目录 `/opt/goldprice-monitor/.env.prod`；部署脚本将根目录的私有 `.env.prod` 复制到该位置并限制文件权限。两个文件均不提交到 Git；已部署的密钥和数据库密码如曾出现在版本历史中，应另行轮换。

| 变量 | 用途 | 开发默认值 |
| --- | --- | --- |
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/goldprice_monitor?connectionTimeZone=UTC` |
| `DB_USER` | MySQL 用户 | `goldprice_monitor` |
| `DB_PASSWORD` | MySQL 密码 | 空值，仅供本地配置 |

## 构建、测试与启动

在仓库根目录执行：

```sh
mvn -f api/pom.xml clean verify
mvn -f api/pom.xml spring-boot:run
```

健康检查位于 `GET http://localhost:8080/actuator/health`，返回 Spring Boot 标准 JSON。数据库不可用时健康状态为 `DOWN`，这是预期行为。测试关闭数据库健康探测，以验证 HTTP 端点而无需外部 MySQL。生产环境应通过部署系统注入数据库凭据；不要把密码写入仓库或命令行。

## 数据库迁移

应用启动时，Flyway 从 `src/main/resources/db/migration` 执行尚未应用的版本化 SQL，并在数据库中维护 `flyway_schema_history`。`V1__initial_schema.sql` 实现 `../docs/DATABASE_DESIGN.md` 中的初版四张表。后续结构调整请新增 `V2__...sql` 等迁移文件，不修改已经部署过的脚本。Flyway 与应用共用 `DB_URL`、`DB_USER`、`DB_PASSWORD`，数据库用户须有建表和修改表的权限；生产环境可使用独立的迁移账号和部署步骤。

首次运行需要空数据库。如果现有数据库已经手动建了表，请先核对其结构与 V1 一致，并制定基线方案；当前配置关闭自动基线，不会跳过已有表。测试中的 HTTP 健康检查显式关闭 Flyway，以免依赖外部 MySQL。

尚未定义业务 API，因此没有添加空的 controller、service 或 mapper。

## Business API

See [API_CONTRACT.md](API_CONTRACT.md). Set `APP_JWT_SECRET` to a unique secret of at least 32 characters before startup. Set `APP_SYSTEM_SALT` to a stable, random 32-character alphanumeric secret before startup; back it up securely, because changing it makes saved Gold credentials unreadable. On first startup after this upgrade, existing Gold account passwords and payment passwords are encrypted in place. Set `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` together to create an initial admin account if it does not exist; changing these environment values does not reset an existing password. `APP_PRICE_SOURCE_URL` defaults to `https://coinapi.gcsgcs.com/api/Accumulate/GetRealTimeGoldPrice` and can be overridden with a trusted JSON price endpoint. This source returns a price without a timestamp, so the API records the fetch time as `dataTime`. `APP_CURRENCY` (default `CNY`), `APP_PRICE_UNIT` (default `gram`), and `APP_MONITOR_INTERVAL_SECONDS` (default 60) control the initial user configuration. `DB_URL`, `DB_USER`, and `DB_PASSWORD` configure MySQL. `APP_DESKTOP_ORIGINS` sets the comma-separated allowed desktop CORS origins; defaults include macOS Tauri, Windows Tauri, and the local Vite development origin. No admin credentials are committed.

管理员账号使用 `admin_account` 表（Flyway V2）。首次启动可通过 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD` 创建账号；已有同名账号不会被重置。不要在仓库中保存管理员密码。管理 API 需要 ADMIN 令牌，普通用户令牌不可访问；管理员退出会撤销服务端会话。

### Gold账号

桌面端账号管理使用 `/api/v1/me/gold-accounts`（GET、POST），以及 `/{id}`（GET、PATCH、DELETE）；验证码操作使用 `/{id}/send-sms` 和 `/{id}/login`。这些接口需要本产品的 USER Bearer token，且只能访问当前用户自己的记录。列表和详情不返回 Gold 密码或 token。账号密码按此任务要求明文保存在数据库，请限制数据库访问和备份权限。

`GET /api/v1/me/gold-accounts/{id}/sellable` 使用该 Gold 账号已保存的登录 token 查询挂卖统计。所有返回数量按北京时间当天展示：`sellableCount` 是今天创建的非自动发放次数；`soldCount`、`soldWeight` 是今天已挂卖的次数和克重；`remainingSellableCount`、`remainingSellableWeight` 只统计今天创建且当前有效的待挂卖气泡；`sellableWeight` 是今日剩余气泡克重加今日已挂卖克重。账号未登录时返回 `GOLD_ACCOUNT_NOT_LOGGED_IN`。

`GET /api/v1/me/gold-accounts/grabcoin` 汇总当前用户关联账号今天创建且仍可挂的气泡数，以及今天创建且仍待卖出的订单数，返回 `accounts` 与 `orders`。`orders` 只包含在线账号今天创建的待卖出订单，按挂单价升序排列；未登录或查询失败的账号在 `accounts` 中显示状态，不计入订单列表。日期按北京时间计算。

`POST /api/v1/me/gold-accounts/{id}/grabcoin/sell` 接收 `{ "weight": 1 }`。服务端只从今天创建且仍有效的气泡中选择克重合计恰好等于请求克数的组合，再逐个调用 Gold 的按编号挂卖接口。返回 `success`、已完成气泡数 `completedCount`、实际挂卖克重 `actualWeight` 和 `orderNos`；上游中途失败时返回 `success: false` 和错误信息，已完成的挂卖不会撤销。请求需要本产品 USER token，账号需已登录 Gold。

`POST /api/v1/me/gold-accounts/{id}/grabcoin/sell-all` 无请求体，调用 Gold 的 `POST /api/grabcoin/order/sell-all-v2`，返回 Gold 的挂卖结果（`count`、`totalWeight`、`orderNos` 等）。

`POST /api/v1/me/gold-accounts/{id}/grabcoin/order/sell` 接收 `{ "orderId": 123 }`，从当前用户关联的 Gold 账号读取已保存的 `payment_password`，按 Gold 客户端公钥 RSA 加密后调用 `POST /api/grabcoin/order/sell-v2`。这两个接口均要求本产品 USER token 和已登录的关联 Gold 账号。

`POST /api/v1/me/gold-accounts/grabcoin/orders/sell` 接收 `{ "goldPrice": 883.17 }`，重新查询当前用户今天待卖出的订单，按挂单价匹配，并使用各订单所属账号的 token 和 `payment_password` 逐笔卖出。返回 `success`、`completedCount`、`totalCount` 和失败时的 `message`；中途失败不会撤销已卖出的订单。

外部 Gold 服务地址通过 `APP_PRICE_SERVER_BASE_URL` 配置。服务端调用 `/api/User/SendSms` 与 `/api/User/LoginByPhoneAndCode`，当前适配器要求返回 `status: true`，登录响应还需包含 `data.token`。缺少 token 时账号不会变为在线。正式接入前需用服务提供方的响应样例核对该契约。
