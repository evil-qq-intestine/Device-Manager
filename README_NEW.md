# Device Manager — MCP 集成（草稿）

> 本文是 MCP（Model Context Protocol）接入部分的草案，先独立成文，稳定后再并入 README。
> 覆盖：令牌模型、签发接口、工具鉴权注解、来源 IP 白名单、nginx 信任边界与 VPN 场景。

---

## 1. 概述

后端基于 `spring-ai-starter-mcp-server-webmvc` 暴露一个 MCP Server，把设备管理能力以工具（tool）形式提供给 MCP 客户端（Claude Desktop、本机 bot 等）。

- 工具用 Spring AI 的 `@McpTool` 声明，位于 `com.example.tool.ai.mcp`。
- 鉴权分两层：
  1. **传输层**：`Authorization: Bearer dm_xxx`，由 `McpBearerFilter` + `McpTokenAuthenticationProvider` 校验（只作用于 `/mcp/**`）。
  2. **工具层**：`@McpRequires` 注解声明最低令牌等级与所需 scope，由 `McpToolAuthorizationBeanPostProcessor` 在调用前强制。
- 令牌数据归属：工具永远通过 `McpScopeGuard.ownerId()` 取归属用户，**不接受客户端传入的 userId**。

---

## 2. 令牌模型

明文格式：`dm_` + 32 字节随机（Base64Url，无填充）。**只把 SHA-256 摘要入库，明文仅在签发响应里返回一次。**

对应实体 `McpToken`（表 `jwt_token`，权限子表 `api_token_permission`）：

| 字段 | 说明 |
| --- | --- |
| `tier` | 令牌等级：`EXTERNAL`（外部客户端）< `TRUSTED`（本机 bot） |
| `mcpTokenPermission` | 一组 scope（子表一行一个值） |
| `expiresAt` | 过期时间，可空 = 永不过期 |
| `allowedIp` | 允许的来源 IP，可空 = 不限制（支持逗号分隔、CIDR） |
| `isEnabled` | 是否启用，停用后立即失效 |
| `lastUsedAt` / `usedIp` | 最近一次成功认证时间与来源 IP（自动回写） |
| `createdAt` | 创建时间 |

### Scope 清单（`McpTokenPermission`）

| 枚举 | scope |
| --- | --- |
| `DEVICE_READ` | `device:read` |
| `DEVICE_WAKE` | `device:wake` |
| `DEVICE_SSH` | `device:ssh` |
| `DEVICE_SHUTDOWN` | `device:shutdown` |
| `SCRIPT_READ` | `script:read` |
| `SCRIPT_WRITE` | `script:write` |
| `SCRIPT_RUN` | `script:run` |
| `VERSION_READ` | `version:read` |

> 设备的“增/改/删”目前还没有独立 scope，需要时再加 `DEVICE_WRITE` / `DEVICE_DELETE`。

---

## 3. 签发与管理接口

管理接口挂在通用 JWT 登录链下（`/api/mcp/token`），签发/查看的都是**当前登录用户**自己的令牌。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/mcp/token` | 签发，明文 `dm_xxx` 仅此一次返回 |
| `GET` | `/api/mcp/token` | 列出当前用户的令牌 |
| `DELETE` | `/api/mcp/token/delete/{tokenId}` | 删除 |
| `PATCH` | `/api/mcp/token/deprecated/{tokenId}` | 停用（`isEnabled=false`） |
| `GET` | `/api/mcp/token/current-ip` | 返回服务端识别到的调用方 IP，用于填写 `allowedIp` |

### 请求体示例

```json
{
  "name": "claude-desktop",
  "tier": "EXTERNAL",
  "permissions": ["DEVICE_READ", "DEVICE_WAKE"],
  "expiresAt": "2026-12-31T00:00:00Z",
  "allowedIp": "203.0.113.10, 10.0.0.0/8"
}
```

- `tier`、`permissions` 必填（`@Valid` 校验）。
- `name` / `expiresAt` / `allowedIp` 可选；`expiresAt` 必须晚于当前时间。
- `id`、归属用户、摘要、明文均由服务端生成，客户端不可指定。

### 响应示例

```json
{
  "token": "dm_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
  "name": "claude-desktop",
  "permissions": ["device:read", "device:wake"]
}
```

---

## 4. 工具鉴权：`@McpRequires`

在 `@McpTool` 方法上叠加 `@McpRequires`，声明最低等级与所需 scope：

```java
@McpTool(name = "get_user_device", description = "Get a list of all devices currently owned by the user")
@McpRequires(tier = McpTokenTier.TRUSTED, scopes = McpTokenPermission.DEVICE_READ)
public List<DeviceListResponse> getDevice() {
    Integer userId = McpScopeGuard.ownerId();            // 归属来自令牌，不是参数
    return DeviceListResponse.from(deviceRepository.findByUserId(userId));
}
```

语义：

- `tier`：最低令牌等级，`EXTERNAL < TRUSTED`。标 `TRUSTED` 后 EXTERNAL 令牌会被拒；默认 `EXTERNAL` 表示任意有效令牌。
- `scopes`：逐个校验 scope。
- 校验失败抛 `McpAccessDeniedException`，被 Spring AI 包成 `result.isError=true` + HTTP 200，消息交给 LLM 自我纠正。
- 未标 `@McpRequires` 的工具不做额外限制（仍需通过 `/mcp` 链的令牌认证）。

> 实现上刻意不用 Spring AOP：Spring AI 的注解扫描器用
> `bean.getClass().getDeclaredMethods()` + `method.getAnnotation(McpTool.class)` 发现工具，
> 工具 Bean 一旦被 CGLIB 代理，方法注解会丢、工具就不注册了。因此
> `McpToolAuthorizationBeanPostProcessor` 改为包装 Spring AI 生成的
> `SyncToolSpecification.callHandler`，在调用前插入校验。

---

## 5. 来源 IP 探测与 `allowedIp`

### 探测链路

```
客户端 -> nginx(写 X-Forwarded-For) -> [ForwardedHeaderFilter] -> [Spring Security McpBearerFilter] -> McpTokenService
```

1. `server.forward-headers-strategy=framework`（`application.yaml`）让 Spring Boot 注册 `ForwardedHeaderFilter`，其 order 为 `Integer.MIN_VALUE`，**排在 Spring Security 之前**。
2. 该 filter 把 `request.getRemoteAddr()` 改写为 `X-Forwarded-For` / `Forwarded` 里的客户端 IP，并移除原始转发头。
3. `McpBearerFilter` 用 `WebAuthenticationDetails(request)` 取 `getRemoteAddress()`，传给 `findValidMcpToken`。
4. 同一个值用于回写 `usedIp` 和比对 `allowedIp`。

可用 `GET /api/mcp/token/current-ip` 查看服务端当前识别到的 IP。

### `allowedIp` 规则

由 `McpIpMatcher` 解析，支持逗号分隔多条，每条为精确 IP 或 CIDR；空 = 不限制：

```
203.0.113.10
10.0.0.0/8
2001:db8::/32
192.168.1.10, 10.0.0.0/8
```

- 同时支持 IPv4 / IPv6。
- 只接受 IP 字面量，不会触发 DNS 反解析。
- IPv4 与 IPv6 不互通（地址族不同即不匹配）。

### 关键安全前提

Spring 的 `ForwardedHeaderUtils.parseForwardedFor` 取的是 `X-Forwarded-For` / `Forwarded: for=` 的
**最左边（第一段）**。若 nginx 用 `$proxy_add_x_forwarded_for`（追加），客户端自带的伪造值会留在最左，
从而**绕过 IP 白名单**。请务必按下节配置。

---

## 6. nginx 配置与信任边界

### 推荐配置（覆写，而非追加）

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;

    # 关键：覆写而不是追加，避免客户端伪造 X-Forwarded-For
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Real-IP     $remote_addr;

    proxy_set_header Host              $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host  $host;
    proxy_set_header X-Forwarded-Port  $server_port;
}
```

多级代理时改用 `real_ip` 模块，只信任已知代理网段：

```nginx
set_real_ip_from 10.0.0.0/8;      # 受信代理网段
real_ip_header   X-Forwarded-For;
real_ip_recursive on;
```

### 信任边界

- `forward-headers-strategy=framework` **信任任意来源**的转发头。
- 因此：**应用端口不能直接对外**，只监听 `127.0.0.1`/内网，或防火墙只放行 nginx。
  否则客户端可直连并伪造 `X-Forwarded-For`，冒充任意 IP 绕过 `allowedIp`。
- 若确实需要直连（无 nginx），则不要依赖 `allowedIp`，并考虑改用 `native` 策略 + 受信代理列表。

---

## 7. VPN / 动态 IP 场景

- **经 nginx 的 VPN**：nginx 看到的源 IP 是 **VPN 出口 / NAT 地址**，不是设备本地地址。`allowedIp` 要填该出口地址或网段。
- **VPN 直连应用（分流、绕过 nginx）**：源 IP 是 VPN 隧道地址（如 `10.8.0.x`），白名单要放该地址池。
- **不要绑定单个 IP**：VPN 出口常变、多人共享同一出口、IPv6 隐私扩展会频繁换地址。建议：
  - 用 CIDR，例如 `10.8.0.0/24`，IPv6 用 `/64`；
  - 或对移动/VPN 客户端不设 `allowedIp`，改用「短有效期 + 最小 scope + `TRUSTED` 分级」控风险。

---

## 8. 传输端点（部署必读）

默认情况下（`application.yaml` 未配置 `spring.ai.mcp.server.*`），spring-ai 2.0.0 启用的是
**SSE** 传输，真实端点为：

- `GET /sse`（建立会话）
- `POST /mcp/message`（发送 JSON-RPC）

而当前 `McpSecurityConfig` 的 `securityMatcher("/mcp/**")` **只覆盖 `/mcp/message`，不覆盖 `/sse`**，
dm_ 令牌在 `/sse` 握手时会被通用 JWT 链拦下。二选一修复：

**方案 A：改用 Streamable HTTP（端点变为 `/mcp`，现有 matcher 生效）**

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
```

**方案 B：保留 SSE，把 `/sse` 纳入 MCP 安全链**

```java
.securityMatcher("/mcp/**", "/sse")
```

> 注意方案 A 下还需确认 `securityMatcher("/mcp/**")` 对纯 `/mcp`（无尾斜杠）也匹配。

---

## 9. 已知限制 / TODO

- 无前端管理界面，令牌需直接调 API 创建/停用。
- 停用后不可重新启用（只能新建或删除）。
- 不具备 admin 管理他人令牌的能力。
- 任何登录用户都可为自己签发 `TRUSTED` 令牌；若 TRUSTED 仅限本机 bot，需要额外的签发限制。
- `allowedIp` 列长度 `varchar(45)`；逗号多条在严格长度的数据库上可能超限（SQLite 不强制）。
- 工具级 scope 之外，还需补 write 类工具的审计日志（可用 `McpScopeGuard.tokenId()`）。
