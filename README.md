# goldprice_monitor
监控实时金价

项目组成、目录规划与技术规范见 [项目架构文档](PROJECT_ARCHITECTURE.md)。

## 部署

`./deploy.sh` 参考 `chidugaiyi` 的部署方式，在本机构建 `api` 和 `admin`，再通过 SSH 发布到 `8.131.90.13`。Nginx 将 `https://gold.chidugaiyi.com/api/` 代理到本机 18080 端口，其余路径提供管理后台。`./deploy.sh status` 查看服务状态。

首次部署前，在服务器创建仅 root 可读的 `/etc/goldprice-monitor.env`，至少填写 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`APP_JWT_SECRET`；按需设置 `APP_ADMIN_USERNAME`、`APP_ADMIN_PASSWORD`。数据库及用户须预先创建，应用启动时会执行 Flyway 迁移。不要将该文件提交到 Git。默认 SSH 地址和密钥路径可用 `REMOTE_HOST`、`REMOTE_USER`、`SSH_KEY` 覆盖。
