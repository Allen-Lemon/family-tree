# API 契约（v1）

本文档供前后端对齐，描述目标 v1 契约。基础路径为 `/api/v1`，除登录/刷新和明确公开资源外均需认证。当前代码中的 `/api`、`/api/trees`、会话/CSRF 登录和同步任务属于既有接口；前后端应共同迁移并设置明确兼容期，不能仅由代理重写而掩盖请求/响应语义差异。请求和响应使用 UTF-8 JSON；文件上传使用 `multipart/form-data`。时间为 ISO 8601 UTC（如 `2026-11-09T08:00:00Z`），日期为 `YYYY-MM-DD`。ID 一律作为不透明字符串处理。

## 通用约定

认证采用同源服务器会话：登录成功后由服务端设置 `HttpOnly`、`SameSite=Lax` 的会话 Cookie；生产环境必须同时设置 `Secure`。浏览器不得把长期令牌保存到 `localStorage`。除安全方法外，写请求还需携带服务端签发的 CSRF Token（默认请求头 `X-XSRF-TOKEN`）。

分页请求：`page=1&pageSize=20&sort=createdAt,desc`，`pageSize` 最大 100。分页响应：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

错误响应：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数不合法",
  "requestId": "req_01...",
  "details": [{ "field": "name", "reason": "REQUIRED" }]
}
```

状态码：`200` 查询/更新成功，`201` 创建成功，`202` 异步任务已受理，`204` 删除成功，`400` 格式错误，`401` 未认证，`403` 无权限，`404` 不存在，`409` 冲突/版本冲突，`413` 文件过大，`415` 类型不支持，`422` 业务校验失败，`429` 限流，`500` 内部错误。错误不得泄露堆栈。

写接口可接受 `Idempotency-Key`。更新资源使用 `version` 字段进行乐观锁；不匹配返回 `409 VERSION_CONFLICT`。

## 认证

### `GET /auth/csrf`

签发或刷新 CSRF Token；响应包含令牌信息，同时可使用 Spring Security 默认的 `XSRF-TOKEN` Cookie 机制。前端在后续写请求中通过 `X-XSRF-TOKEN` 回传。

### `POST /auth/login`

请求：

```json
{ "username": "admin", "password": "<password>" }
```

响应 `200` 并设置会话 Cookie：

```json
{
  "user": { "id": "usr_1", "username": "admin", "displayName": "管理员", "roles": ["ADMIN"] },
  "expiresAt": "2026-11-09T10:00:00Z"
}
```

### `POST /auth/logout`

注销服务端会话、清理会话 Cookie，返回 `204`。

### `GET /auth/me`

返回当前会话用户及权限集合；未登录返回 `401`。

## 用户与角色（ADMIN）

- `GET /users?page&pageSize&q&status`：用户列表；
- `POST /users`：创建用户；字段 `username`、`displayName`、`email?`、`roles[]`、`temporaryPassword`；
- `GET /users/{userId}`：详情；
- `PATCH /users/{userId}`：更新显示名、邮箱、状态、角色与 `version`；
- `POST /users/{userId}/reset-password`：设置临时密码并要求下次登录修改；
- `DELETE /users/{userId}`：停用/软删除，返回 `204`；不得删除最后一名管理员。

角色枚举建议：`ADMIN`、`EDITOR`、`VIEWER`。

## 家谱

家谱对象：

```json
{
  "id": "fam_1",
  "name": "示例家谱",
  "description": "",
  "rootPersonId": "per_1",
  "ownerId": "usr_1",
  "visibility": "PRIVATE",
  "version": 1,
  "createdAt": "2026-11-09T08:00:00Z",
  "updatedAt": "2026-11-09T08:00:00Z"
}
```

- `GET /families?page&pageSize&q`：当前用户可见家谱；
- `POST /families`：创建，字段 `name`、`description?`、`visibility?`；
- `GET /families/{familyId}`：详情；
- `PATCH /families/{familyId}`：更新可变字段和 `version`；
- `DELETE /families/{familyId}`：软删除，返回 `204`；
- `GET /families/{familyId}/tree?rootPersonId&depth=4`：返回可视化子图。

树响应：

```json
{
  "familyId": "fam_1",
  "rootPersonId": "per_1",
  "persons": [],
  "relationships": [],
  "truncated": false
}
```

## 成员

成员对象关键字段：`id`、`familyId`、`name`、`formerName?`、`gender`（`MALE|FEMALE|OTHER|UNKNOWN`）、`birthDate?`、`deathDate?`、`isLiving`、`biography?`、`avatarMediaId?`、`privacyLevel`（`PRIVATE|FAMILY|PUBLIC`）、`version`、时间戳。

- `GET /families/{familyId}/persons?page&pageSize&q&living`：成员列表；
- `POST /families/{familyId}/persons`：创建成员；`name` 必填；
- `GET /persons/{personId}`：详情；
- `PATCH /persons/{personId}`：局部更新并携带 `version`；
- `DELETE /persons/{personId}`：无未处理关系后软删除，否则 `409 PERSON_HAS_RELATIONSHIPS`；
- `GET /persons/{personId}/relationships`：成员的关系列表。

在世成员的敏感字段按权限裁剪，不能仅由前端隐藏。

## 亲属关系

关系对象：

```json
{
  "id": "rel_1",
  "familyId": "fam_1",
  "type": "PARENT_CHILD",
  "person1Id": "per_parent",
  "person2Id": "per_child",
  "startDate": null,
  "endDate": null,
  "notes": "",
  "version": 1
}
```

- `POST /families/{familyId}/relationships`：创建；
- `PATCH /relationships/{relationshipId}`：更新；
- `DELETE /relationships/{relationshipId}`：删除，返回 `204`。

类型：`PARENT_CHILD`（person1 为父/母，person2 为子女）、`SPOUSE`、`ADOPTIVE_PARENT_CHILD`、`GUARDIAN`。后端必须拒绝自关联、重复关系、祖先循环和跨家谱引用；`SPOUSE` 查询时按无向关系处理。

## 媒体与附件

- `POST /families/{familyId}/media`：multipart，字段 `file`、`personId?`、`title?`、`description?`，返回 `201` 或病毒扫描异步时 `202`；
- `GET /media/{mediaId}`：元数据；
- `GET /media/{mediaId}/content`：授权下载，设置安全的 Content-Disposition；
- `PATCH /media/{mediaId}`：更新元数据和 `version`；
- `DELETE /media/{mediaId}`：软删除。

媒体对象包括 `id`、`fileName`、`contentType`、`size`、`sha256`、`scanStatus`（`PENDING|CLEAN|INFECTED|ERROR`）和时间戳。感染或扫描错误文件不得下载。

## OCR

- `POST /media/{mediaId}/ocr`：请求 `{ "language": "zh-CN", "pages": "1-3" }`，返回 `202 { "jobId": "ocr_1", "status": "PENDING" }`；
- `GET /ocr/jobs/{jobId}`：状态、进度、错误码；
- `GET /ocr/jobs/{jobId}/result`：文本块、页码、坐标、置信度；
- `POST /ocr/jobs/{jobId}/cancel`：取消；
- `POST /ocr/jobs/{jobId}/confirm`：人工确认后将选定字段应用到成员，需携带目标 `personId` 和字段映射。

禁用 OCR 时创建任务返回 `409 OCR_DISABLED`，且不得外发文件。

## 导入

- `POST /families/{familyId}/imports`：multipart `file`、`format`（`GEDCOM|CSV|JSON|ZIP`）、`mode`（`VALIDATE_ONLY|MERGE`），返回 `202`；
- `GET /imports/{jobId}`：进度、统计、警告与错误摘要；
- `GET /imports/{jobId}/errors?page&pageSize`：逐行错误；
- `POST /imports/{jobId}/commit`：提交已通过预检的暂存数据，返回 `202`；
- `POST /imports/{jobId}/cancel`：取消。

重复的 `Idempotency-Key` 返回原任务，不重复创建数据。

## 导出

- `POST /families/{familyId}/exports`：请求 `{ "format": "JSON", "includeMedia": false, "includeSensitive": false }`，返回 `202`；
- `GET /exports/{jobId}`：任务状态；
- `GET /exports/{jobId}/download`：完成后下载；链接短时有效且绑定当前用户；
- `DELETE /exports/{jobId}`：删除生成文件。

敏感字段导出需要单独权限并写审计。

## 审计（ADMIN）

`GET /audit-logs?page&pageSize&actorId&action&resourceType&from&to` 返回只读分页记录。字段：`id`、`occurredAt`、`actorId`、`action`、`resourceType`、`resourceId`、`result`、`ip`（按策略脱敏）、`requestId`。不得提供修改或删除接口。

## 健康与版本

- `GET /actuator/health`：供容器/运维探测，匿名响应仅为总体状态；
- `GET /system/version`：返回应用版本、API 版本和构建时间，不返回依赖详情或秘密。
