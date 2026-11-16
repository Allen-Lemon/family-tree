# 运维说明

## 日常检查

每日检查：

- `docker compose --env-file .env -f deploy/compose.yaml ps` 全部核心服务为 healthy；
- 首页、登录和 `/healthz` 可访问；
- 后端无连续 5xx、鉴权失败突增或数据库连接耗尽；
- 磁盘、MySQL 卷和 uploads 卷容量；
- 最近一次备份成功且已复制到离机位置；
- 系统时间同步、TLS 证书有效期。

## 常用命令

```powershell
# 启动或升级
.\scripts\start.ps1

# 查看状态和最近日志
docker compose --env-file .env -f deploy/compose.yaml ps
docker compose --env-file .env -f deploy/compose.yaml logs --tail 200 backend
docker compose --env-file .env -f deploy/compose.yaml logs -f frontend

# 平滑重启单个服务
docker compose --env-file .env -f deploy/compose.yaml restart backend

# 停止但保留数据
.\scripts\stop.ps1
```

## 监控建议

- 可用性：Nginx `/healthz` 与后端 `/actuator/health`；
- 应用：请求量、P95/P99、4xx/5xx、登录失败、导入/OCR 队列积压；
- JVM：堆、GC、线程、文件描述符；
- MySQL：连接数、慢查询、锁等待、缓冲池、复制/备份状态；
- 容量：卷剩余空间、上传增长率、日志大小；
- 安全：管理员变更、大批量导出、异常来源 IP、病毒扫描失败。

健康端点不得泄露环境变量、数据库地址、栈信息或密钥。

## 故障处理

### 服务 unhealthy

1. `docker compose ... ps` 确定故障服务；
2. `docker inspect` 查看健康检查输出；
3. 查看服务日志及其依赖服务日志；
4. 检查 `.env` 是否缺变量、端口冲突、磁盘是否已满；
5. 不要先删卷；优先修复配置并重启单服务。

### 数据库不可用

- 确认 mysql 容器、磁盘空间和 `mysql_data` 卷；
- 检查连接数、错误日志及凭据是否变更；
- 禁止通过重建卷“修复”；需要恢复时遵循 `backup-restore.md`。

### 上传/OCR 失败

- 检查大小、MIME、扩展名和可选 ClamAV；
- OCR 默认关闭；启用时检查 provider 超时、限流和脱敏要求；
- 外部服务不可用时保留待处理状态，不能静默丢弃或无限重试。

## 日志与审计

容器日志输出到 stdout/stderr，由平台轮转与集中采集。生产环境对应用日志做大小和保留期限制。日志不得记录密码、令牌、Cookie、完整证件号、OCR 密钥或上传原文。审计日志至少记录操作者、时间、来源、动作、对象、结果和请求 ID；普通管理员不得修改审计日志。

## 维护窗口

升级、恢复、密钥轮换和大规模导入应在维护窗口执行。操作前公告、备份并指定回滚点，操作后按验收清单抽测，记录执行人、版本、起止时间和结果。
