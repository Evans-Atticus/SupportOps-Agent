# 三角色个人中心接口

接口统一前缀为 `/api/v1`，除登录外均需携带：

```http
Authorization: Bearer <accessToken>
```

统一响应结构：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "请求追踪号",
  "timestamp": "2026-07-23T15:00:00+08:00"
}
```

## 登录

`POST /auth/login`

```json
{
  "username": "admin",
  "password": "12345678l"
}
```

本地初始化账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `12345678l` |
| 客服人员 | `support01` | `12345678l` |
| 客户 | `customer01` | `12345678l` |

## 通用个人中心接口

三个角色分别使用 `/admin`、`/agent`、`/customer` 前缀：

- `GET /{role}/dashboard`：个人中心概览指标。
- `GET /{role}/modules/{module}?keyword=关键词`：查询指定模块，返回 `total` 和 `items`。
- `GET /{role}/modules/{module}/items/{id}`：按记录编号读取当前角色有权查看的详情。
- `GET /{role}/modules/{module}/advice`：生成当前模块的数据建议。
- `GET /{role}/modules/{module}/export?keyword=关键词`：按当前筛选条件生成 CSV。
- `GET /{role}/refunds?keyword=关键词`：查询当前角色数据范围内的退款记录。

模块编码：

- 管理员：`overview`、`people`、`ticket-stats`、`orders`、`logistics`、`refund-approval`、`agent-management`、`integrations`、`audit`。
- 客服：`workspace`、`conversations`、`tickets`、`orders`、`logistics`、`refunds`、`diagnoses`。
- 客户：`service`、`my-orders`、`my-logistics`、`after-sales`、`refunds`、`messages`、`profile`。

服务端会同时校验登录角色和模块白名单。客户接口只返回绑定客户本人的订单、工单、退款、物流和消息。

订单列表直接返回订单号、SKU 和产品名称；工单列表返回工单号和关联业务号；物流列表返回运单号、订单号和产品名称。列表显示字段与搜索条件保持一致。

## 客服会话接口

`POST /agent/conversations/{conversationNo}/reply`：由当前接待客服发送人工回复并更新会话摘要。

```json
{
  "content": "退款已提交支付渠道，预计三个工作日内原路到账。"
}
```

## 退款接口

- `POST /customer/refunds`：客户提交退款申请。
- `POST /agent/refunds`：客服代客户提交退款申请。
- `POST /admin/refunds/{refundNo}/approve`：管理员批准退款，可修改批准金额。
- `POST /admin/refunds/{refundNo}/reject`：管理员拒绝退款，拒绝原因必填。
- `POST /admin/refunds/{refundNo}/execute`：执行已批准退款。

批准请求：

```json
{
  "approvedAmount": 99.00,
  "reason": "扣除已使用配件费用"
}
```

拒绝请求：

```json
{
  "reason": "订单已超过售后期限，且未提供质量问题凭证"
}
```

退款状态流转：

```text
SUBMITTED / UNDER_REVIEW / NEED_MORE_INFO
  ├─ APPROVED → EXECUTING → SUCCEEDED / FAILED
  └─ REJECTED
```

审批、拒绝、执行会写入审计日志，并向相关客户生成站内通知。

## 人员管理接口

`POST /admin/people`：管理员新增客服账号。

```json
{
  "username": "support02",
  "password": "初始密码",
  "displayName": "客服小王",
  "dailyQuota": 50
}
```

创建的账号角色固定为 `SUPPORT_AGENT`，不会通过该接口创建管理员。

## 本地运行

项目必须使用 JDK 21：

```powershell
$env:JAVA_HOME='C:\path\to\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
mvn spring-boot:run "-Dspring-boot.run.profiles=real"
```

接口文档页面：`http://localhost:8080/swagger-ui.html`。
