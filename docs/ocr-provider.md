# OCR Provider 配置

## 默认策略

系统默认：

```dotenv
OCR_ENABLED=false
OCR_PROVIDER=disabled
OCR_ENDPOINT=
OCR_API_KEY=
```

此配置不得发起任何外部 OCR 请求。前端应显示“未启用”，后端在调用 OCR 接口时返回明确的业务错误，而不是把文件发送给默认或隐式服务。

## Provider 抽象

后端建议定义统一适配器：

- 输入：任务 ID、文件引用、语言、页码范围；
- 输出：文本块、页码、坐标、置信度、原始字段的最小必要子集；
- 状态：`PENDING`、`PROCESSING`、`REVIEW_REQUIRED`、`SUCCEEDED`、`FAILED`、`CANCELLED`；
- 能力：健康检查、超时、取消、重试、限流和错误归一化。

Provider 名称可采用 `disabled`、`local` 或经批准的供应商标识。`OCR_ENDPOINT` 必须为 HTTPS；禁止动态接受用户提供的任意 URL，以避免 SSRF。

## 启用本地 OCR

本地 Provider 优先用于敏感材料。配置示例（值须按实际服务填写）：

```dotenv
OCR_ENABLED=true
OCR_PROVIDER=local
OCR_ENDPOINT=http://approved-internal-ocr:port
OCR_API_KEY=
```

仅允许访问固定内网地址，实施网络隔离、认证和资源限制。处理结束后按策略删除临时文件。

## 启用外部 OCR

启用前必须完成：

1. 数据分类和隐私影响评估；
2. 获得数据主体告知/授权或确认合法处理依据；
3. 审核服务地域、加密、留存、模型训练使用、分包商及删除机制；
4. 签署数据处理协议并确认事件通知时限；
5. 只传最少页面，必要时遮蔽证件号等敏感字段；
6. 通过秘密管理系统注入 API Key；
7. 在隔离测试环境验证，不使用生产真实数据。

随后设置 `OCR_ENABLED=true`、经批准的 `OCR_PROVIDER`、固定 `OCR_ENDPOINT` 和 `OCR_API_KEY`，重启 backend。密钥仅进入后端，不进入浏览器。

## 可靠性与安全

- 默认超时 30 秒；仅对网络超时、429、部分 5xx 做有上限的指数退避；
- 每个任务使用幂等键，避免重复计费和重复外发；
- 记录 Provider、任务、耗时、结果和请求 ID，不记录图片、全文或密钥；
- 低置信度必须进入人工复核，OCR 文本不能直接覆盖权威家谱字段；
- Provider 故障时保持原文件和任务状态，不自动切换到未经审批的 Provider；
- 提供删除任务及供应商侧数据删除流程。

## 停用与密钥轮换

先设置 `OCR_ENABLED=false` 并部署，确认无运行中任务，再撤销旧密钥。轮换时创建新密钥、更新秘密、重启并验证，最后撤销旧密钥。疑似泄露时应立即停用 OCR、撤销密钥并启动安全事件流程。
