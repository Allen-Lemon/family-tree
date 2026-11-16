# Docker Compose 部署（远程 MySQL）

## 部署结构

默认使用项目根目录 `compose.yaml`：

- `frontend`：Node 22 构建 Vue 静态资源，Nginx 提供网页及 `/api/` 同源代理。
- `backend`：Maven / Java 21 构建 Spring Boot，容器内监听 8080，不向宿主机发布后端端口。
- 数据库：继续使用 **42.193.151.196:3306 / family_tree**，不会创建本地 MySQL，不会清空或重新导入已有库。
- 可选 `antivirus` profile：ClamAV，默认关闭。
- 上传、人像、导入导出、应用备份：绑定宿主机目录到 `/app/uploads`，默认复用 `./data/uploads`。

应用按 Flyway 正常执行尚未应用的版本迁移；并非完全不写数据库。上线前备份数据库和文件，迁移失败不要直接执行 repair/clean 或删除表。

## 1. 前置条件

- Docker Desktop（Windows，Linux containers / WSL2 模式），或 Linux Docker Engine。
- Docker Compose **v2.24+**（使用旧 `deploy/compose.yaml` 兼容入口需要 include 支持）。
- Docker 主机到远程 MySQL 的 3306 端口可达，数据库允许此主机的出口 IP。
- 默认入口 `127.0.0.1:8088` 未占用。
- Linux 上传目录需允许容器 UID/GID **10001:10001** 读写。

检查：

```sh
docker version
docker compose version
```

本次准备配置的 Windows 环境未安装 Docker CLI / Engine，因此不能在此环境宣称镜像构建、容器启动和容器内联调已经通过。

## 2. 配置 .env（不要重置现有密钥）

### 从已有源码运行环境迁移

原配置在根目录 `.env.local`。在用户明确允许读取此文件并创建 `.env` 后运行：

```sh
node scripts/prepare-compose.mjs
```

该脚本复制现有数据库凭据、初始化管理员参数、**原有数据加密密钥**到 `.env`，不打印值，不覆盖已有 `.env`，也不复制仅限源码运行的 `SERVER_ADDRESS=127.0.0.1`。容器内由 Compose 固定监听 `0.0.0.0:8080`。

**不得重新生成 `APP_DATA_ENCRYPTION_KEY`**，否则原有敏感资料不能解密。`.env` 和 `.env.local` 已被 Git 忽略，不要贴到聊天、仓库或 Dockerfile，也不要执行会打印展开密码的 `docker compose config`（用 `--quiet`）。

### 新主机手动配置

```sh
cp .env.example .env
```

PowerShell：

```powershell
Copy-Item .env.example .env
```

填写 DB_URL / DB_USERNAME / DB_PASSWORD、BOOTSTRAP_ADMIN_PASSWORD、APP_DATA_ENCRYPTION_KEY。现有系统的账号密码和加密密钥必须从原环境安全迁移，不使用 CHANGE_ME 占位值启动。Compose `.env` 含 `$` 的值应使用单引号，避免变量插值。

常用配置：

| 变量 | 默认/说明 |
|---|---|
| `HTTP_BIND` | `127.0.0.1`，本机访问；公网部署显式改为 `0.0.0.0` 并配防火墙/TLS |
| `HTTP_PORT` | `8088` |
| `DB_URL` | 指定远程 MySQL，启用 TLS 的 JDBC URL |
| `UPLOADS_PATH` | `./data/uploads`，相对根目录 compose.yaml；可改为绝对路径 |
| `SESSION_COOKIE_SECURE` | 本机 HTTP 为 false；全程 HTTPS 时 true |
| `IMAGE_TAG` | 默认 local；生产建议指定版本 |

远程 MySQL 当前按原配置使用 root。正式部署建议改为仅有 `family_tree` 权限的专用账号，限制数据库网络来源；本次不擅自修改远程数据库用户或密码。

## 3. 文件迁移和权限

在本机保留原 `data/uploads`，绑定挂载可直接复用，**不是复制进镜像**。迁到新服务器时需安全复制完整上传目录，包括 `profile-portraits`、媒体、backups、exports；只迁数据库会使原照片和附件缺失。

Linux 示例（先确认目标目录正确，再执行权限设置）：

```sh
mkdir -p data/uploads
sudo chown -R 10001:10001 data/uploads
```

不要用 `chmod 777`。Windows Docker Desktop 需允许访问项目盘符；中文路径应加引号。`.dockerignore` 默认拒绝所有文件，仅放行构建源码、迁移 SQL、配置及前端测试，排除真实凭据、上传、人像、日志、node_modules、target。

历史应用备份记录可能保存了 Windows 绝对路径；新生成备份使用容器路径。迁移后若旧备份下载失败，可从已迁移上传目录中的 backups 直接取出对应 JSON；上线前应验证此类历史文件记录，必要时在备份后执行专门的路径转换，不批量猜测或覆盖记录。

切换运行方式前，停止原源码后端，避免两个后端同时对同一数据库运行提醒任务。新容器入口端口与 Vite 5173 不同，但不能因此长期并行跑两套写服务。确认不会影响当前使用后执行：

```powershell
.\scripts\stop-local.ps1
```

## 4. 构建和启动

在项目根目录执行：

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
```

PowerShell 也可用：

```powershell
.\scripts\bootstrap-check.ps1
.\scripts\start.ps1
```

构建阶段不连接远程数据库，不运行初始化业务脚本。前端执行 `npm ci` / `npm run build`，后端执行 Maven 打包（镜像构建跳过测试；发布前另跑完整测试）。Java 运行镜像包含人像处理所需 AWT/fontconfig，启用 headless，并以非 root 用户运行。

访问：**http://127.0.0.1:8088/**。账号使用原系统账号，不会覆盖现有密码。

Nginx 不把 `/actuator` 和 Swagger 作为公共入口。`/healthz` 仅表示前端存活；backend 容器健康检查会实际检查数据库。后端重建后 Nginx 通过 Docker DNS 重新解析服务地址；SPA 入口禁用缓存，带 hash 的静态资源可长期缓存。

## 5. 必须执行的部署验证

```sh
docker compose ps
docker compose logs --tail 100 backend frontend
curl --fail http://127.0.0.1:8088/healthz
docker compose exec -T backend curl --fail http://127.0.0.1:8080/actuator/health
```

确认 `healthy` / `UP` 后，在真实浏览器登录，检查族谱列表、个人信息/已有照片、完整图谱、权限、上传及下载。仅有 HTTP 200 或 `docker compose config --quiet` 不能证明业务正确。

可选病毒扫描：

```sh
# 同时在.env设置CLAMAV_ENABLED=true
docker compose --profile antivirus up -d --build --wait --wait-timeout 600
```

OCR 默认 disabled，不外发资料。

## 6. 停止、更新和备份

```sh
# 停止，不删除数据库和绑定目录
docker compose down

# 更新：先备份远程数据库、uploads和原加密密钥
docker compose build --pull
docker compose up -d --wait --wait-timeout 300
```

`.\scripts\stop.ps1` 使用根 Compose。禁止为了更新执行 `down -v`、删除 uploads、Flyway clean。绑定目录和远程数据库不会由 compose down 删除，仍需正常离机备份。

原 `scripts/backup.ps1` 原先只适用于本地 MySQL 容器，默认外部数据库模式不适用。它现在会明确拒绝并提示使用远程数据库的 mysqldump/托管备份，不能误以为容器卷包含数据库。完整备份应包括：远程 MySQL 导出、宿主机上传目录、`.env` 中的原加密密钥（分别安全保管）。

旧命令 `docker compose --env-file .env -f deploy/compose.yaml ...` 作为兼容入口包含根 Compose，但不再启动本地 MySQL。今后统一使用根目录命令，避免维护两份互相矛盾的部署配置。
