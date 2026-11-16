# family-tree 家谱管理系统

`family-tree` 是面向家族资料管理的前后端分离应用。本文档定义项目结构、开发依赖、容器化启动方式、默认管理员初始化参数、主要页面与接口，以及 Windows 部署注意事项。项目目录名必须保持为 `family-tree`。

## 目录结构

```text
family-tree/
├─ backend/                 后端源码（由后端团队维护）
├─ frontend/                前端源码（由前端团队维护）
├─ deploy/
│  ├─ compose.yaml          MySQL、后端、前端/Nginx、可选 ClamAV
│  ├─ backend.Dockerfile    以项目根为构建上下文
│  ├─ frontend.Dockerfile   以项目根为构建上下文
│  └─ nginx/default.conf
├─ docs/                    接口、部署、运维、安全与验收文档
└─ scripts/                 Windows PowerShell 运维脚本
```

## 系统架构

```text
浏览器
  │ HTTP/HTTPS
  ▼
Nginx / 前端静态资源（端口 80）
  ├─ /               → SPA
  └─ /api/*          → backend:8080
                         ├─ MySQL 8.4（utf8mb4）
                         ├─ uploads 持久卷
                         ├─ 可选 ClamAV
                         └─ 可选 OCR Provider（默认禁用外发）
```

MySQL 与后端仅位于 Compose 内部网络。后端调试端口默认只绑定 `127.0.0.1:8080`，生产环境应通过 Nginx 访问，并在外围配置 TLS。

## 本地依赖

容器方式（推荐）：

- Windows 10/11 或 Linux；
- Docker Desktop / Docker Engine 24+；
- Docker Compose v2；
- PowerShell 5.1+（使用根目录脚本时）。

源码开发方式的基线依赖由当前 Dockerfile 反映：

- JDK 21、Maven 3.9；
- Node.js 22、npm；
- MySQL 8.4；
- Nginx 1.27（仅部署需要）。

> `deploy/backend.Dockerfile` 默认按 Maven 项目构建 `backend/` 并运行产出的 JAR；`deploy/frontend.Dockerfile` 默认执行 `npm run build`，静态输出目录为 `frontend/dist/`。若源码采用其他构建布局，应只调整 `deploy/` 中 Dockerfile，不必把 Dockerfile 放入源码目录。

## 快速启动

在 PowerShell 中进入项目根目录：

```powershell
Copy-Item .env.example .env
# 编辑 .env，将全部 CHANGE_ME 替换为独立的高强度随机值
.\scripts\bootstrap-check.ps1
.\scripts\start.ps1
```

开启可选病毒扫描服务：

```powershell
.\scripts\start.ps1 -WithAntivirus
```

同时将 `.env` 中 `CLAMAV_ENABLED=true`，然后重启后端。停止服务：

```powershell
.\scripts\stop.ps1
```

不要日常使用 `-RemoveVolumes`；它会删除数据库和上传文件卷，且脚本要求先显式设置 `FAMILY_TREE_ALLOW_VOLUME_DELETE=YES`。

Linux/macOS 可直接执行：

```bash
cp .env.example .env
# 填写 .env

docker compose --env-file .env -f deploy/compose.yaml up -d --build
```

服务入口：

- Web 页面：`http://localhost/`
- API 根路径：`http://localhost/api/v1`
- 本机后端健康检查：`http://127.0.0.1:8080/actuator/health`（若构建未暴露 Actuator，Compose 会回退探测 `/api/v1/auth/csrf`）
- Nginx 健康检查：`http://localhost/healthz`

## 默认管理员环境变量

系统不内置可用的默认密码。首次启动前必须在 `.env` 中配置：

| 变量 | 示例/默认 | 说明 |
|---|---|---|
| `ADMIN_USERNAME` | `admin` | 初始管理员用户名 |
| `ADMIN_PASSWORD` | 无 | 必填；使用长随机密码 |
| `ADMIN_EMAIL` | `admin@example.invalid` | 预留的管理员邮箱配置 |
| `APP_DATA_ENCRYPTION_KEY` | 无 | 必填；至少 32 字节随机值，用于应用数据保护 |

后端仅在指定用户名不存在时创建管理员，已有同名管理员时不会覆盖密码。当前版本没有修改密码页面；初始密码应使用安全随机值，如需轮换可创建新管理员并停用旧账号。

## 主要页面与接口

建议页面路由：

- `/login`：登录；
- `/dashboard`：概览；
- `/trees`：家谱列表；
- `/trees/:id`：家谱工作区；
- `/members/:id`：成员详情与亲属关系；
- `/graph`：树状、环形、扇形和沙漏图；
- `/media`：照片、文档和附件；
- `/data`：导入、导出和备份；
- `/ocr`：OCR 任务与人工复核；
- `/users`：用户与权限。

接口统一以 `/api/v1` 开头，包括认证、用户、家谱、成员、关系、媒体、OCR、导入导出与审计日志。状态码、分页、错误格式和字段约束见 [docs/api-contract.md](docs/api-contract.md)。

## 数据与持久化

- `mysql_data`：MySQL 数据目录；
- `uploads`：上传的媒体与导入文件；
- `clamav_db`：可选病毒库；
- 本机 `backups/`：备份脚本输出，已被 Git 忽略。

执行备份：

```powershell
.\scripts\backup.ps1
# 或指定备份位置
.\scripts\backup.ps1 -Destination 'D:\family-tree-backup'
```

完整恢复步骤与定期演练要求见 [docs/backup-restore.md](docs/backup-restore.md)。

## OCR 与隐私默认值

`OCR_ENABLED=false`、`OCR_PROVIDER=disabled`，默认不会把图片外发。只有在完成数据分类、用户告知/授权、供应商安全评估及密钥配置后，才可开启外部 OCR。配置方式见 [docs/ocr-provider.md](docs/ocr-provider.md)。

## Windows 注意事项

1. 使用 Docker Desktop 的 Linux containers 模式，并启用 WSL 2 后端。
2. 保持仓库与 `.env` 为 UTF-8；不要使用旧版记事本以 ANSI 保存中文配置。
3. PowerShell 执行策略阻止脚本时，仅对当前进程放行：`Set-ExecutionPolicy -Scope Process Bypass`，不要降低系统全局策略。
4. Docker Desktop 文件共享应允许访问项目所在磁盘；项目路径含中文时，建议从本项目脚本启动，避免手工拼接未加引号的路径。
5. 端口 80 被占用时，在 `.env` 设置 `HTTP_PORT=8088`，页面改为 `http://localhost:8088/`。
6. Docker Desktop 至少分配 4 GB 内存；开启 ClamAV 时建议 6 GB 以上。
7. `.env` 不得提交 Git，不要通过聊天或工单粘贴其内容。

## 文档索引

- [部署说明](docs/deployment.md)
- [运维说明](docs/operations.md)
- [备份恢复](docs/backup-restore.md)
- [安全说明](docs/security.md)
- [OCR Provider](docs/ocr-provider.md)
- [导入导出](docs/import-export.md)
- [API 契约](docs/api-contract.md)
- [端到端验收](docs/acceptance.md)
