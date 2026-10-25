# stock-screener 验证报告

- Change: stock-screener
- Date: 2026-10-25
- verify_mode: full
- 轮次: verify-fail 第 1 轮修复后重新验证（verify_failures=1）

## 验证结论

通过。上一轮验证发现的 3 个 IMPORTANT 问题均已修复并有测试证据，最终审查与补修复查均为 Clean；实现符合 proposal、delta spec、design.md 与 Design Doc。

## 检查项结果

| # | 检查项 | 结果 |
|---|--------|------|
| 1 | tasks.md 全部任务已完成 `[x]` | PASS（12/12，含 3 个修复任务） |
| 2 | 实现符合 `design.md` 高层设计 | PASS |
| 3 | 实现符合 Design Doc（因子/热点/评级/AI/定时任务） | PASS |
| 4 | 能力规格场景全部通过 | PASS（6 Requirement / 6 Scenario，见下） |
| 5 | proposal.md 目标已满足 | PASS |
| 6 | delta spec 与 design doc 无矛盾 | PASS（本轮未改 spec/design，仅修复实现） |
| 7 | 关联设计文档可定位 | PASS（`docs/superpowers/specs/2026-09-01-stock-screener-design.md`） |

## 场景覆盖证据

| Scenario | 测试证据 |
|---|---|
| 计算个股评分 | `ScreeningServiceTest`（百分位方向、PE≤0、缺失重归一化） |
| 生成候选股列表 | `ScreeningServiceTest`（三风格权重、Top 20、排序、理由） |
| 输出长期价值评级 | `ScreeningServiceTest`（阈值 65/45 边界） |
| 定期更新选股结果 | `ScreeningTaskTest`（cron 编排、覆盖率校验、分步入库） |
| 识别热点方向 | `HotspotServiceTest`（行业/概念聚合、<3 过滤、热度排序） |
| 标注炒作风险 | `HotspotServiceTest`（三档阈值、依据文本） |

## 本轮修复验证

| 问题 | 修复提交 | 验证证据 |
|---|---|---|
| 缺失子指标被计 0 分 | `4f51082` | RED 26 tests/7 failures → GREEN 26/26；全缺→null、单项→100%、理由不输出缺失指标 |
| K 线窗口无日期上界 | `f020eeb` | 查询 `trade_date <= 评分日`；首根非评分日不计动量/热点；聚焦 48/48 |
| 唯一索引未创建 + 并发重复 | `dd766b6` | 启动幂等 ensureIndex（三集合）；三处写入改原子 upsert；task reviewer Approved（0 Critical/0 Important） |
| 同日重跑 null 覆盖 | `99d3001` | upsert 跳过 null 字段；RED 9 tests/1 failure → GREEN 9/9；scoped re-review ADDRESSED/Clean |

## 测试证据（本报告生成前实际执行）

- 后端：`mvn test -q`（cwd `StockAI`）→ exit 0，surefire 21 个测试类共 **135 tests，0 failures / 0 errors / 0 skipped**
- 前端：`npm run build`（cwd `StockAI-Console`）→ 成功（2087 modules，7.48s；仅有 chunk >500kB 的既有告警）
- OpenSpec：`comet classic openspec -- validate stock-screener --json` → 1 passed / 0 failed，issues 为空

## 接受的非阻塞问题（记录原因与影响范围）

以下均为审查记录的 Minor，本轮接受延后，不影响正确性/安全/核心验收：

1. `/screening/run` 返回对象不再回读持久化 `id`：前端仅消费 `tradeDate`/`boards`/`aiReportStatus`/`aiReport`，无消费方依赖 id。
2. 三处 upsert 逻辑重复约 6 行：可维护性建议，后续统一抽取。
3. `ScreeningIndexInitializer` 建索引失败仅记日志：三个集合由本 change 新建，首启无重复数据；若部署环境存在同日重复历史文档需先清理，否则唯一约束无法建立。
4. upsert 转换测试使用 mock 转换，未覆盖真实 `MongoConverter`；建议后续补一条真实转换冒烟。
5. `StockDailyKlineRepository` 中 `findTop21BySymbolOrderByTradeDateDesc`、`existsByTradeDate` 已无生产调用（反射测试保留），存在误用旧语义的风险，建议后续清理。
6. 同日并发 upsert 在极端并发下仍可能抛 `DuplicateKeyException`（由唯一索引兜底，无重试）；当前单 `@Scheduled` 串行编排下风险低。

## 工作区说明

- 不属于本 change 的未提交改动（`.vscode/settings.json`、`docs/openspec/changes/trade-plan/`）已排除在验证输入之外。
- `docs/openspec/changes/stock-screener/.comet/` 下的 brief/report/diff 为本次协调产物，未提交。
