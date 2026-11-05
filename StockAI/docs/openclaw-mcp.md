# 让 OpenClaw 调用卖出纪律引擎（MCP + REST）

卖出纪律（L1 硬止损 / L2 移动止盈 / L4 分批止盈）在 StockAI 后端里跑，**判定结果只落库、不推送**。
OpenClaw 通过 MCP 读取信号、确认信号、改纪律参数。

## 一、服务端暴露了什么

| 类型 | 地址 | 说明 |
|------|------|------|
| MCP (SSE) | `http://<host>:8888/sse` | Spring AI MCP Server，工具名见下表 |
| REST | `http://<host>:8888/exit/*` | 同样的能力，给不想接 MCP 的场合 |
| 定时任务 | 每个交易日 **17:30** | `exit.cron`，判定当天收盘后的信号 |

MCP 服务名：`stockai-mcp`（`spring.ai.mcp.server.name`）。OpenClaw 侧工具名是 `stockai__<tool>`。

## 二、OpenClaw 配置

编辑 `~/.openclaw/openclaw.json`，在 `mcp.servers` 下加一段：

```json
{
  "mcp": {
    "servers": {
      "stockai": {
        "url": "http://192.168.31.88:8888/sse",
        "transport": "sse",
        "connectionTimeoutMs": 5000,
        "requestTimeoutMs": 30000,
        "toolFilter": {
          "include": ["evaluate_exit_rules", "list_exit_signals", "ack_exit_signal", "list_exit_policies", "update_exit_policy", "list_exit_runs"]
        }
      }
    }
  }
}
```

`transport` 不写时 OpenClaw 默认就当 `sse`，写上更明确。
`include` 里没列 `buy_position` / `sell_position` —— 那两个是下单工具，不建议让 agent 随意用。

或者用 CLI：

```bash
openclaw mcp add stockai --url http://192.168.31.88:8888/sse --transport sse
openclaw mcp doctor stockai --probe     # 能看到工具列表才算通
```

## 三、可用工具

| 工具 | 用途 | 关键参数 |
|------|------|----------|
| `evaluate_exit_rules` | 立刻按最新收盘价跑一遍判定，写 `exit_signals` | `userId`（可省） |
| `list_exit_signals` | 查信号，默认全部状态 | `status` = `TRIGGERED` / `ACKED` / `RECOVERED` |
| `ack_exit_signal` | 把某条信号标记为已处理 | `signalId`、`note`（如「已卖出」「继续持有」） |
| `list_exit_policies` | 查每只持仓的纪律参数与状态 | 无 |
| `update_exit_policy` | 改某只持仓的纪律参数（只传要改的） | `symbol` + 任意阈值 |
| `list_exit_runs` | 查最近的判定执行记录 | 无 |

`userId` 一律可省略，服务端回落到 `stockai.mcp.default-user-id`（当前 `6928fca14f206ada1b7a9269`）。

### 典型对话

```
> 今天有没有该卖的？
（agent 调 list_exit_signals → 返回 TRIGGERED 的 515180 HARD_STOP，建议卖出 700 股）

> 我卖了，帮我记一下
（agent 调 ack_exit_signal，note="已卖出"）

> 512480 的移动止盈太紧了，回撤放到 20%
（agent 调 update_exit_policy，symbol=512480，trailingRate=0.20）
```

## 四、REST 对照表

| 方法 | 路径 | 参数 |
|------|------|------|
| POST | `/exit/evaluate` | `userId`（必填） |
| GET | `/exit/signals` | `userId`、`status`（可选） |
| POST | `/exit/signals/{id}/ack` | `note`（可选） |
| GET | `/exit/policies` | `userId` |
| POST | `/exit/policies` | `userId` + 要改的字段 |
| GET | `/exit/runs` | `userId`（可选） |

返回统一是 `Result<T>`：`{"code": 0, "message": null, "data": ...}`。

## 五、信号字段说明

`ExitSignal` 里对决策有用的是四个：

- `code`：`HARD_STOP` / `TRAILING_STOP` / `TAKE_PROFIT_1` / `TAKE_PROFIT_2`
- `action`：人话，如「清仓」「减仓 1/3」
- `suggestQuantity`：建议股数（100 股整数倍，已按初始建仓量算）
- `invalidate`：**这条信号什么情况下作废**，比如「收盘价回到 118.5 以上」

`invalid` 字段是给 agent 用的：下次判定时条件消失且过了 5 个交易日冷却期，信号会自动变 `RECOVERED`。

## 六、注意

- 判定用的是**收盘价**，盘中调 `evaluate_exit_rules` 只会拿到上一交易日的结论。
- 有冷却期（`exit.signal-cooldown-trade-days=5`）：同一只票同一个 `code`，5 个交易日内不会重复生成新信号。
- `update_exit_policy` 改的是**参数**，不会重置 `trailArmed` / `tp1Done` 这些运行状态。
  这些状态在**清仓后重新建仓**时会自动重置（判定时发现建仓日期变了就清零），日常不需要人工干预。
- 目前 `exit.notify-enabled=false`：信号不会推飞书/站内信，只进数据库。
