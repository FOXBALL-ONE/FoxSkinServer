# HMCL 获取 Minecraft 正版玩家资料的流程

本文根据 [HMCL](https://github.com/HMCL-dev/HMCL) 当前 `main` 分支的实现整理，说明 HMCL 如何使用微软账号登录，并获取对应的 Minecraft Java 版玩家资料。

这里的“玩家资料”指 Minecraft Services 返回的游戏档案，例如玩家 UUID、游戏昵称、皮肤和披风；它不是 Microsoft Graph 返回的微软账号邮箱或个人资料。

## 1. 总体流程

```text
Microsoft OAuth
    -> Microsoft access_token / refresh_token
    -> Xbox Live Token
    -> XSTS Token
    -> Minecraft access_token
    -> 检查 Minecraft Java 版授权
    -> 获取 Minecraft profile
    -> 玩家 UUID、昵称、皮肤、披风
```

HMCL 的主要实现位于：

- [`OAuth.java`](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/OAuth.java)：微软 OAuth 授权码和设备码登录。
- [`MicrosoftService.java`](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/microsoft/MicrosoftService.java)：Xbox、XSTS 和 Minecraft Services 的 token 交换。
- [`MicrosoftSession.java`](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/microsoft/MicrosoftSession.java)：保存登录会话和 Minecraft 玩家资料。

## 2. Microsoft OAuth 登录

HMCL 请求的 OAuth scope 是：

```text
XboxLive.signin offline_access
```

`offline_access` 用于获得 refresh token，使启动器能够在 access token 过期后刷新登录状态。

### 授权码模式

HMCL 启动本地回调服务，生成 PKCE 的 `code_verifier` 和 `code_challenge`，然后打开微软授权页：

```text
https://login.live.com/oauth20_authorize.srf
```

授权完成后，微软回调本地地址并返回 authorization code。HMCL 再请求：

```text
POST https://login.live.com/oauth20_token.srf
```

使用 authorization code、`code_verifier` 和 redirect URI 换取：

```text
access_token
refresh_token
```

### 设备码模式

HMCL 也支持设备码登录：

```text
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode
```

用户在微软页面输入设备码后，HMCL 轮询：

```text
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
```

轮询期间处理 `authorization_pending`、`expired_token` 和 `slow_down` 等状态，成功后同样得到 Microsoft access token 和 refresh token。

## 3. Microsoft Token 换成 Xbox Live Token

微软 OAuth token 不能直接调用 Minecraft Services。HMCL 首先请求：

```http
POST https://user.auth.xboxlive.com/user/authenticate
Content-Type: application/json
```

请求体中的关键字段如下：

```json
{
  "Properties": {
    "AuthMethod": "RPS",
    "SiteName": "user.auth.xboxlive.com",
    "RpsTicket": "d=<Microsoft access_token>"
  },
  "RelyingParty": "http://auth.xboxlive.com",
  "TokenType": "JWT"
}
```

响应中的两个关键值是：

- `Token`：Xbox Live 用户 token。
- `DisplayClaims.xui[0].uhs`：用户哈希（`uhs`）。

## 4. Xbox Live Token 换成 XSTS Token

HMCL 将上一步的 Xbox token 放入以下请求：

```http
POST https://xsts.auth.xboxlive.com/xsts/authorize
Content-Type: application/json
```

请求体的关键字段：

```json
{
  "Properties": {
    "SandboxId": "RETAIL",
    "UserTokens": ["<Xbox Live Token>"]
  },
  "RelyingParty": "rp://api.minecraftservices.com/",
  "TokenType": "JWT"
}
```

响应中的 `Token` 是 XSTS token。HMCL 同时校验 XSTS 返回的 `uhs` 是否和第一步一致，并根据 Xbox 返回的错误码处理没有 Xbox 账号、儿童账号或家庭组限制等情况。

## 5. XSTS Token 换成 Minecraft Token

HMCL 请求：

```http
POST https://api.minecraftservices.com/authentication/login_with_xbox
Content-Type: application/json
```

请求体：

```json
{
  "identityToken": "XBL3.0 x=<uhs>;<XSTS Token>"
}
```

成功响应包含：

```json
{
  "username": "...",
  "roles": [],
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

这里的 `access_token` 是后续访问 Minecraft Services 的 token，不是最初的 Microsoft OAuth token。

## 6. 检查正版授权

HMCL 使用 Minecraft access token 请求：

```http
GET https://api.minecraftservices.com/entitlements/mcstore
Authorization: Bearer <Minecraft access_token>
```

当前实现把该接口的 HTTP 200 作为拥有商店授权的必要检查。请求失败时登录流程终止。

如果获取 Minecraft profile 时返回 HTTP 404，HMCL 进一步请求：

```http
GET https://api.minecraftservices.com/entitlements/license
Authorization: Bearer <Minecraft access_token>
```

并检查 `items` 中是否存在：

```text
game_minecraft
```

由此区分：

1. 账号没有 Minecraft Java 版授权。
2. 账号有授权，但没有可用的 Minecraft Java 版角色档案。

## 7. 获取 Minecraft 玩家资料

通过授权检查后，HMCL 请求：

```http
GET https://api.minecraftservices.com/minecraft/profile
Authorization: Bearer <Minecraft access_token>
```

典型响应结构：

```json
{
  "id": "玩家 UUID，不带连字符",
  "name": "游戏昵称",
  "skins": [
    {
      "id": "皮肤 ID",
      "state": "ACTIVE",
      "url": "皮肤图片地址",
      "variant": "CLASSIC"
    }
  ],
  "capes": []
}
```

HMCL 主要使用以下字段：

| 字段 | 含义 |
| --- | --- |
| `id` | Minecraft 玩家 UUID |
| `name` | 当前游戏昵称 |
| `skins` | 皮肤列表，包含状态、URL 和模型类型 |
| `capes` | 披风列表 |

## 8. 获取更完整的纹理资料

HMCL 还可以根据玩家 UUID 请求 Mojang Session Server：

```http
GET https://sessionserver.mojang.com/session/minecraft/profile/<uuid>
```

该接口返回完整的 Yggdrasil Game Profile。`properties` 中通常包含 Base64 编码的 `textures` 属性，解析后可以得到皮肤和披风的纹理地址。

HMCL 对该资料使用异步缓存，主要用于显示玩家皮肤；初次登录时的 UUID 和昵称仍来自 `/minecraft/profile`。

## 9. HMCL 保存的会话资料

HMCL 在 [`MicrosoftSession`](https://github.com/HMCL-dev/HMCL/blob/main/HMCLCore/src/main/java/org/jackhuang/hmcl/auth/microsoft/MicrosoftSession.java) 中保存：

```text
tokenType       通常为 Bearer
accessToken     Minecraft access token
refreshToken    Microsoft refresh token
notAfter        Minecraft access token 的过期时间
user.id         登录响应中的 username
profile.id      Minecraft 玩家 UUID
profile.name    Minecraft 游戏昵称
```

启动 Minecraft 时，HMCL 将玩家昵称、UUID 和 Minecraft access token 组装为游戏认证信息。刷新登录时使用 Microsoft refresh token，并检查刷新后的 UUID 是否仍然与原先选择的角色一致。

## 10. 不会获取什么资料

HMCL 这条流程没有调用 Microsoft Graph 的 `/me` 接口，因此不会通过该流程获取：

- Microsoft 账号邮箱
- Microsoft 账号真实姓名
- Microsoft 头像等通用账号资料

它获取的是 Xbox/Minecraft 体系中的游戏身份和游戏档案。`username` 也被 HMCL 作为会话中的用户标识保存，但玩家展示信息以 `/minecraft/profile` 返回的 `id` 和 `name` 为准。

## 11. 实现时的安全要求

- 不要在服务端日志中记录 Microsoft access token、refresh token、XSTS token 或 Minecraft access token。
- `refresh_token` 长期有效性通常高于短期 access token，应加密存储并限制访问权限。
- 不要让前端或第三方接口直接暴露上述 token。
- 认证成功必须同时检查 Minecraft entitlement 和 profile，不能只根据微软 OAuth 成功判断拥有正版资格。
- 玩家身份主键应使用 Minecraft profile UUID，不应仅使用玩家昵称。
- Minecraft Services 或 Xbox 接口异常时应拒绝本次验证，不能降级为按玩家名放行。

## 12. FoxSkinServer 中的正版绑定实现

当前项目已将上述链路接入 FoxSkinServer：

### 配置

在 `.env` 或部署环境中配置 Microsoft OAuth 应用：

```properties
FOXSKIN_OAUTH_MICROSOFT_CLIENT_ID=<Microsoft 应用 ID>
FOXSKIN_OAUTH_MICROSOFT_CLIENT_SECRET=<Microsoft 应用密钥>
MOJANG_AUTH_ENABLED=true
```

Microsoft 应用的回调地址必须配置为：

```text
https://<站点域名>/api/auth/oauth/microsoft/callback
```

`MOJANG_AUTH_ENABLED` 默认是 `false`。关闭时不会接受正版 Yggdrasil `join` 请求，但不影响 FoxSkin 账号登录。

### 管理端运行时开关

```text
GET /api/admin/mojang/status
PUT /api/admin/mojang/status?enabled=true
Authorization: Bearer <管理员 access token>
```

开关持久化在 `options` 表的 `mojang_authentication_enabled` 中。关闭时会立即删除尚未消费的正版 join 凭证；Microsoft OAuth 也会同时从可用提供商中消失。重新开启后既有绑定继续生效。
### 绑定接口

1. 用户先登录 FoxSkinServer，并选择一个自己拥有的角色。
2. 前端打开：

   ```text
   GET /api/auth/oauth/microsoft/redirect?mode=bind&player_id=<角色主键>
   ```

3. 用户在微软页面完成登录。服务端依次执行 Microsoft OAuth、Xbox Live、XSTS 和 Minecraft Services 校验，确认正版授权与玩家档案后建立绑定。
4. 查询当前用户的绑定：

   ```text
   GET /api/account/mojang-bindings
   Authorization: Bearer <FoxSkin access token>
   ```

5. 撤销绑定：

   ```text
   DELETE /api/account/mojang-bindings/{binding_id}
   Authorization: Bearer <FoxSkin access token>
   ```

一个活动正版 UUID 只能绑定一个角色，一个角色也只能有一个活动正版绑定。撤销记录保留在绑定表中，后续可重新绑定。

### 正版启动器进服

正版启动器仍需使用指向 FoxSkinServer 的 `authlib-injector`。客户端向：

```text
POST /api/yggdrasil/sessionserver/session/minecraft/join
```

发送官方 Minecraft access token 和官方 `selectedProfile` UUID。FoxSkinServer 校验 token、官方 profile 和绑定关系后，登记短期一次性 join 凭证；服务端的 `hasJoined` 请求消费该凭证，并返回绑定 FoxSkin 角色的签名 Profile。

FoxSkin 角色 UUID 可以与官方 UUID 不同：官方 UUID 用于认证，绑定角色 UUID 用于服务器内身份和材质展示。
