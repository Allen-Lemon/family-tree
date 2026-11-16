# family-tree 家谱管理系统

`family-tree` 是面向家族资料管理的前后端分离应用。本文档定义项目结构、开发依赖、容器化启动方式、默认管理员初始化参数、主要页面与接口，以及 Windows 部署注意事项。项目目录名必须保持为 `family-tree`。

## Docker Compose 部署（当前推荐）

使用根目录 `compose.yaml`：前端 Nginx + Java 21 后端，**继续连接原远程 MySQL `42.193.151.196:3306/family_tree`**，不再创建本地 MySQL。完整步骤、迁移文件及权限说明见 [docs/deployment.md](docs/deployment.md)。

```sh
# 已有源码环境：在明确允许读取 .env.local 并创建 .env 后，复用原凭据及加密密钥
node scripts/prepare-compose.mjs

# Docker 已安装并启动后
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
```

访问 **http://127.0.0.1:8088/**，使用原账号。上传、人像和备份文件默认绑定 `./data/uploads`。不要重置原加密密钥，不要清库或重新初始化业务数据。

新主机先 `cp .env.example .env` 并安全填写现有凭据/密钥。默认入口仅本机可访问；远程访问需显式配置 `HTTP_BIND`、防火墙和 HTTPS。

**当前准备环境没有 Docker CLI / Engine，容器构建和启动尚未验证。** 安装 Docker Desktop（Linux containers）或在部署服务器上执行上述命令。

具体配置、文件迁移和验证步骤见 `docs/deployment.md`。

## 目录结构

```text
family-tree/
├─ backend/                 后端源码（由后端团队维护）
├─ frontend/                前端源码（由前端团队维护）
├─ compose.yaml             主部署入口：远程 MySQL + 后端 + 前端/Nginx
├─ deploy/
│  ├─ compose.yaml          包含根 Compose 的兼容入口
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
                         ├─ 远程 MySQL（原 family_tree 数据库）
                         ├─ uploads 持久卷
                         ├─ 可选 ClamAV
                         └─ 可选 OCR Provider（默认禁用外发）
```

后端不发布宿主机端口，仅供 Nginx 访问；MySQL 使用既有远程地址。网页默认绑定 `127.0.0.1:8088`，生产远程访问需显式配置监听地址和 TLS。

## 本地依赖

容器方式（推荐）：

- Windows 10/11 或 Linux；
- Docker Desktop / Docker Engine 24+；
- Docker Compose v2.24+；
- PowerShell 5.1+（使用根目录脚本时）。

源码开发方式的基线依赖由当前 Dockerfile 反映：

- JDK 21、Maven 3.9；
- Node.js 22、npm；
- MySQL 8.0+（当前使用远程 8.0.36）；
- Nginx（部署镜像内提供）。

> `deploy/backend.Dockerfile` 默认按 Maven 项目构建 `backend/` 并运行产出的 JAR；`deploy/frontend.Dockerfile` 默认执行 `npm run build`，静态输出目录为 `frontend/dist/`。若源码采用其他构建布局，应只调整 `deploy/` 中 Dockerfile，不必把 Dockerfile 放入源码目录。

## 快速启动

在 PowerShell 中进入项目根目录：

```powershell
# 已有 .env.local 时复用原数据库凭据和加密密钥，不覆盖已有 .env
node .\scripts\prepare-compose.mjs
# 新主机才使用 Copy-Item .env.example .env，并填写原有配置
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

不要日常使用 `-RemoveVolumes`；容器托管卷的删除要求显式设置 `FAMILY_TREE_ALLOW_VOLUME_DELETE=YES`。远程数据库和绑定的上传目录不会被 Compose 删除，但仍需独立备份。

Linux/macOS 可直接执行：

```bash
cp .env.example .env
# 填写原数据库凭据和原加密密钥

docker compose up -d --build --wait --wait-timeout 300
```

服务入口：

- Web 页面：`http://127.0.0.1:8088/`
- API 根路径：`http://127.0.0.1:8088/api/v1`
- 后端健康检查：`docker compose exec -T backend curl -f http://127.0.0.1:8080/actuator/health`
- Nginx 健康检查：`http://127.0.0.1:8088/healthz`

## 默认管理员环境变量

系统不内置可用的默认密码。首次启动前必须在 `.env` 中配置：

| 变量 | 示例/默认 | 说明 |
|---|---|---|
| `BOOTSTRAP_ADMIN_USERNAME` | `admin` | 初始管理员用户名 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 无 | 必填；沿用原环境配置，不覆盖已有用户密码 |
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

- 远程 MySQL：需通过远程 mysqldump 或数据库托管备份独立保存；
- `UPLOADS_PATH`（默认 `./data/uploads`）：媒体、人像、导入文件及应用备份绑定目录；
- `clamav_db`：可选病毒库；
- 原有加密密钥：单独安全保管，不可丢失或重新生成。

原 `scripts/backup.ps1` 不适用于外部数据库，现会明确拒绝执行，避免产生不完整备份。部署前同时备份远程数据库、上传目录及配置密钥。详见 [docs/deployment.md](docs/deployment.md)，旧备份恢复文档中的本地 MySQL 容器步骤不适用于当前架构。

## OCR 与隐私默认值

`OCR_ENABLED=false`、`OCR_PROVIDER=disabled`，默认不会把图片外发。只有在完成数据分类、用户告知/授权、供应商安全评估及密钥配置后，才可开启外部 OCR。配置方式见 [docs/ocr-provider.md](docs/ocr-provider.md)。

## Windows 注意事项

1. 使用 Docker Desktop 的 Linux containers 模式，并启用 WSL 2 后端。
2. 保持仓库与 `.env` 为 UTF-8；不要使用旧版记事本以 ANSI 保存中文配置。
3. PowerShell 执行策略阻止脚本时，仅对当前进程放行：`Set-ExecutionPolicy -Scope Process Bypass`，不要降低系统全局策略。
4. Docker Desktop 文件共享应允许访问项目所在磁盘；项目路径含中文时，建议从本项目脚本启动，避免手工拼接未加引号的路径。
5. 默认使用 8088；端口被占用时，在 `.env` 修改 `HTTP_PORT`，并使用 `http://127.0.0.1:新端口/` 访问。
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
