# 部署说明

## 1. 前置条件

- 项目根目录名为 `family-tree`；
- Docker Engine 24+、Docker Compose v2；
- 生产主机建议 4 核 CPU、8 GB 内存、50 GB 可扩容磁盘；
- 80/443 端口可用，服务器时钟与 NTP 同步；
- DNS、TLS 证书和离机备份位置已准备。

## 2. 配置

在项目根目录执行：

```powershell
Copy-Item .env.example .env
```

为 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`ADMIN_PASSWORD` 和 `APP_DATA_ENCRYPTION_KEY` 分别生成独立随机值。不要复用密码，不要提交 `.env`。生产环境将 `CORS_ALLOWED_ORIGINS` 设置为实际 HTTPS 域名，并在确认全链路 HTTPS 后设置 `SESSION_COOKIE_SECURE=true`。

`OCR_ENABLED=false` 和 `OCR_PROVIDER=disabled` 应保持默认，除非完成了 OCR 外发审批。需要 ClamAV 时将 `CLAMAV_ENABLED=true`，并以 antivirus profile 启动。

## 3. 构建与启动

Windows：

```powershell
.\scripts\bootstrap-check.ps1
.\scripts\start.ps1
# 可选：.\scripts\start.ps1 -WithAntivirus
```

Linux：

```bash
docker compose --env-file .env -f deploy/compose.yaml config --quiet
docker compose --env-file .env -f deploy/compose.yaml up -d --build
```

Compose 以项目根为构建上下文，Dockerfile 位于 `deploy/`；不会要求修改 `backend/` 或 `frontend/`。

## 4. 验证

```powershell
docker compose --env-file .env -f deploy/compose.yaml ps
docker compose --env-file .env -f deploy/compose.yaml logs --tail 100 backend frontend mysql
Invoke-WebRequest http://localhost/healthz -UseBasicParsing
```

所有服务应为 `healthy`。使用 `.env` 中的初始管理员凭据登录，并确认核心家谱接口可访问。当前版本未提供修改密码页面，初始密码必须直接使用高强度随机值并妥善保存。

## 5. 生产入口与 TLS

内置 Nginx 提供 HTTP 和反向代理。生产环境必须在负载均衡器、Ingress 或主机级反向代理终止 TLS，并：

- 强制 HTTP 跳转 HTTPS；
- 仅开放 80/443，限制 `127.0.0.1:8080` 不得被远端访问；
- 配置可信代理与真实客户端 IP；
- 证书自动续期；
- 如确认全站 HTTPS，启用 HSTS；
- 按实际上传限制同步调整 `client_max_body_size` 与 `MAX_UPLOAD_SIZE_MB`。

## 6. 更新与回滚

1. 阅读变更说明并做数据库及上传卷备份；
2. 在预生产执行迁移和验收；
3. 记录当前镜像/提交版本；
4. `docker compose ... build --pull`；
5. `docker compose ... up -d`；
6. 检查健康状态、日志和核心业务；
7. 失败时恢复上一镜像；涉及不兼容数据库迁移时按恢复文档回滚数据。

禁止在未备份的情况下运行破坏性迁移。Compose 镜像标签在正式发布时应锁定为经过验证的明确版本或 digest。
