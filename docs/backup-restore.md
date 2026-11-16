# 备份与恢复

## 备份范围与目标

必须同时备份：

1. MySQL 逻辑备份（家谱、用户、关系、任务和审计数据）；
2. `uploads` 卷（照片、附件和导入源文件）；
3. 部署版本号及非秘密配置模板；
4. 密钥应由独立秘密管理系统托管，不写入备份清单。

建议目标：生产环境 RPO 不超过 24 小时、RTO 不超过 4 小时；业务方可按数据量提高频率。

## 创建备份

```powershell
.\scripts\backup.ps1
.\scripts\backup.ps1 -Destination 'D:\family-tree-backup'
```

脚本从容器环境读取数据库密码，不硬编码或写入命令参数。输出 SQL、上传卷压缩包及 JSON 清单。备份完成后：

- 对文件计算 SHA-256 并保存到受保护位置；
- 加密后复制到不同故障域/离机介质；
- 限制读取权限并定义保留期；
- 每月至少做一次隔离环境恢复演练。

示例校验：

```powershell
Get-FileHash D:\family-tree-backup\* -Algorithm SHA256
```

## 恢复前准备

1. 确认恢复时间点、版本兼容性和变更窗口；
2. 隔离目标环境，停止业务写入；
3. 对现状再做一次保护性备份；
4. 校验备份清单、哈希、SQL 与压缩包可读性；
5. 使用与备份兼容的应用和数据库版本。

## 恢复数据库

先启动 MySQL，等待 healthy。以下命令从 `.env` 加载 Compose 配置，密码由容器环境读取：

```powershell
Get-Content -Raw 'D:\family-tree-backup\family-tree-YYYYMMDD-HHMMSS.sql' |
  docker compose --env-file .env -f deploy/compose.yaml exec -T mysql sh -c 'exec mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

大文件可使用 `cmd /c type` 或在 Linux 主机用输入重定向，避免 PowerShell 管道编码转换。恢复前确保目标库为空或已按迁移策略处理；不得把不受信任 SQL 导入生产。

## 恢复上传卷

停止 backend，避免恢复过程中写入：

```powershell
docker compose --env-file .env -f deploy/compose.yaml stop backend
$project = 'family-tree' # 应与 .env 中 COMPOSE_PROJECT_NAME 一致
docker run --rm --mount "type=volume,src=${project}_uploads,dst=/data" alpine:3.21 sh -c 'rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true'
docker run --rm --mount "type=volume,src=${project}_uploads,dst=/data" --mount "type=bind,src=D:\family-tree-backup,dst=/backup,readonly" alpine:3.21 tar -xzf /backup/family-tree-uploads-YYYYMMDD-HHMMSS.tar.gz -C /data
docker compose --env-file .env -f deploy/compose.yaml start backend frontend
```

清空卷是破坏性操作，必须二次核对卷名并保留保护性备份。若无需覆盖，可恢复到新卷进行校验。

## 恢复验收

- 所有容器 healthy；
- 管理员和普通用户可登录且权限正确；
- 家谱节点数、关系数与备份基准一致；
- 抽查照片/附件可读且哈希一致；
- 导入、导出、审计日志正常；
- 恢复过程、用时、数据时间点和异常已记录。

恢复演练不得连接生产 OCR Provider，也不得向外发送真实数据。
