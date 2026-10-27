package com.xian.stockai.service.tradeplan;

import com.alibaba.fastjson2.JSON;
import com.xian.stockai.config.TradePlanProperties;
import com.xian.stockai.dao.TradePlanSummaryRepository;
import com.xian.stockai.po.TradePlanSummary;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanRes;
import com.xian.stockai.vo.TradePlanSummaryBriefRes;
import com.xian.stockai.vo.TradePlanSummaryRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author xian
 * @description 交易计划周报服务：组装数据、调用模型、结构校验与降级
 */
@Service
@Slf4j
public class TradePlanSummaryService {

    private static final String[] SECTION_TITLES = {
            "## 一、组合概览", "## 二、本周买卖回顾", "## 三、当前持仓交易计划", "## 四、行业分布",
            "## 五、风险与关注", "## 六、下周关注方向", "## 七、下周行动清单", "## 八、免责声明"
    };
    private static final String DISCLAIMER = "以上内容由量化规则与历史数据生成，仅作辅助参考，不构成投资建议；"
            + "实际交易由用户自行决策并承担风险。";

    private final ChatClient chatClient;
    private final TradePlanSummaryDataAssembler dataAssembler;
    private final TradePlanSummaryRepository summaryRepository;
    private final TradePlanProperties properties;
    private final Resource reportPrompt;
    private final Resource reportTemplate;

    public TradePlanSummaryService(ChatClient chatClient,
                                   TradePlanSummaryDataAssembler dataAssembler,
                                   TradePlanSummaryRepository summaryRepository,
                                   TradePlanProperties properties,
                                   @Value("classpath:/prompts/trade-plan-weekly-report.md") Resource reportPrompt,
                                   @Value("classpath:/prompts/trade-plan-weekly-template.md") Resource reportTemplate) {
        this.chatClient = chatClient;
        this.dataAssembler = dataAssembler;
        this.summaryRepository = summaryRepository;
        this.properties = properties;
        this.reportPrompt = reportPrompt;
        this.reportTemplate = reportTemplate;
    }

    /**
     * 生成当期周报（同一周重复生成时覆盖）。
     */
    public TradePlanSummary generateWeekly(String userId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(5);
        Map<String, Object> data = dataAssembler.assemble(userId, weekStart, weekEnd);

        String content;
        String status = TradePlanSummary.STATUS_SUCCESS;
        String fallbackReason = null;
        try {
            String system = readResource(reportPrompt);
            String user = readResource(reportTemplate) + "\n\n数据：\n" + JSON.toJSONString(data);
            String generated = chatClient.prompt().system(system).user(user).call().content();
            if (!isStructurallyValid(generated, data)) {
                throw new IllegalStateException("周报结构校验失败");
            }
            content = generated;
        } catch (Exception e) {
            log.error("周报生成失败，降级为量化文本: userId={}, error={}", userId, e.getMessage());
            content = buildFallback(data, weekStart, weekEnd);
            status = TradePlanSummary.STATUS_FALLBACK;
            fallbackReason = e.getMessage();
        }

        TradePlanSummary summary = summaryRepository
                .findByUserIdAndWeekStart(userId, weekStart.toString())
                .orElseGet(TradePlanSummary::new);
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(LocalDateTime.now());
        }
        summary.setUserId(userId);
        summary.setWeekStart(weekStart.toString());
        summary.setWeekEnd(weekEnd.toString());
        summary.setGeneratedAt(LocalDateTime.now());
        summary.setContent(content);
        summary.setStatus(status);
        summary.setFallbackReason(fallbackReason);
        summary.setUpdatedAt(LocalDateTime.now());
        summaryRepository.save(summary);
        trimToRetention(userId);
        return summary;
    }

    /**
     * 最新一期周报
     */
    public Result<TradePlanSummaryRes> latest(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Result.fail("用户ID不能为空");
        }
        TradePlanSummary summary = summaryRepository.findTopByUserIdOrderByWeekStartDesc(userId).orElse(null);
        if (summary == null) {
            return Result.fail("暂无周报，每周六 08:00 自动生成");
        }
        return Result.success(TradePlanSummaryRes.from(summary));
    }

    /**
     * 最近 12 期周报列表
     */
    public Result<List<TradePlanSummaryBriefRes>> history(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Result.fail("用户ID不能为空");
        }
        List<TradePlanSummaryBriefRes> result = new ArrayList<>();
        for (TradePlanSummary summary : summaryRepository.findTop12ByUserIdOrderByWeekStartDesc(userId)) {
            result.add(TradePlanSummaryBriefRes.from(summary));
        }
        return Result.success(result);
    }

    /**
     * 指定周期周报全文
     */
    public Result<TradePlanSummaryRes> detail(String userId, String weekStart) {
        if (userId == null || userId.isEmpty() || weekStart == null || weekStart.isEmpty()) {
            return Result.fail("用户ID和周期不能为空");
        }
        TradePlanSummary summary = summaryRepository.findByUserIdAndWeekStart(userId, weekStart).orElse(null);
        if (summary == null) {
            return Result.fail("指定周期的周报不存在");
        }
        return Result.success(TradePlanSummaryRes.from(summary));
    }

    boolean isStructurallyValid(String content, Map<String, Object> data) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        for (String title : SECTION_TITLES) {
            if (!content.contains(title)) {
                return false;
            }
        }
        if (!content.contains("免责声明") || !content.contains("不构成投资建议")) {
            return false;
        }
        Object plans = data.get("plans");
        if (plans instanceof List<?> planList) {
            for (Object item : planList) {
                if (item instanceof TradePlanRes plan && plan.getStockName() != null
                        && !content.contains(plan.getStockName())) {
                    return false;
                }
            }
        }
        return true;
    }

    String buildFallback(Map<String, Object> data, LocalDate weekStart, LocalDate weekEnd) {
        Map<String, Object> summary = asMap(data.get("summary"));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(weekStart.getYear()).append("年第")
                .append(weekStart.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()))
                .append("周 交易计划周报（").append(weekStart).append(" ~ ").append(weekEnd).append("）\n");
        sb.append("生成时间：").append(LocalDateTime.now().withNano(0)).append("\n\n");

        sb.append("## 一、组合概览\n");
        sb.append("- 持仓 ").append(value(summary, "positionCount", "0")).append(" 只")
                .append("，总成本 ¥").append(money(summary.get("totalCost")))
                .append("，总市值 ¥").append(money(summary.get("totalMarketValue")))
                .append("，浮动盈亏 ¥").append(money(summary.get("totalFloatPnl")))
                .append("（").append(percent(summary.get("floatPnlRate"))).append("%）\n");
        sb.append("- 现金余额：¥").append(money(summary.get("cash"))).append("\n");
        sb.append("- 接近止损：").append(value(summary, "stopLossWarnings", "0")).append(" 只")
                .append("；到达止盈位：").append(value(summary, "targetReached", "0")).append(" 只")
                .append("；计划降级：").append(value(summary, "degradedCount", "0")).append(" 只\n\n");

        sb.append("## 二、本周买卖回顾\n");
        sb.append("### 2.1 买入\n");
        appendWeekTrades(sb, data.get("weekTrades"), "BUY", "本周无买入操作");
        sb.append("### 2.2 卖出\n");
        appendWeekTrades(sb, data.get("weekTrades"), "SELL", "本周无卖出操作");
        sb.append("### 2.3 清仓股复盘\n");
        appendCleared(sb, data.get("clearedPositions"));
        sb.append("\n");

        sb.append("## 三、当前持仓交易计划\n");
        appendPlans(sb, data.get("plans"));
        sb.append("\n");

        sb.append("## 四、行业分布\n");
        appendIndustry(sb, data.get("industryDistribution"));
        sb.append("\n");

        sb.append("## 五、风险与关注\n");
        sb.append("1. 【止损风险】接近止损 ").append(value(summary, "stopLossWarnings", "0")).append(" 只\n");
        sb.append("2. 【止盈机会】到达止盈位 ").append(value(summary, "targetReached", "0")).append(" 只\n");
        sb.append("3. 【波动异常】详见逐仓 ATR 占比\n");
        sb.append("4. 【计划降级】").append(value(summary, "degradedCount", "0")).append(" 只\n");
        sb.append("5. 【行业集中】详见行业分布占比\n\n");

        sb.append("## 六、下周关注方向\n");
        sb.append("### 6.1 行业方向\n");
        appendFocusIndustries(sb, data.get("focusIndustries"));
        sb.append("### 6.2 个股观察\n");
        appendFocusStocks(sb, data.get("focusStocks"));
        sb.append("\n");

        sb.append("## 七、下周行动清单\n");
        sb.append("- 本周无新增操作建议，维持既有计划\n\n");

        sb.append("## 八、免责声明\n");
        sb.append(DISCLAIMER).append("\n");
        return sb.toString();
    }

    private void appendWeekTrades(StringBuilder sb, Object trades, String side, String emptyText) {
        boolean any = false;
        if (trades instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = asMap(item);
                if (side.equalsIgnoreCase(String.valueOf(row.get("side")))) {
                    any = true;
                    sb.append("- ").append(value(row, "tradeDate", "—")).append(" ")
                            .append(value(row, "stockName", "—")).append("(")
                            .append(value(row, "symbol", "—")).append(") ")
                            .append(side.equals("BUY") ? "买入 " : "卖出 ")
                            .append(value(row, "quantity", "—")).append(" 股，均价 ")
                            .append(money(row.get("price"))).append("，实际")
                            .append(side.equals("BUY") ? "支出" : "到账").append(" ¥")
                            .append(money(row.get("actualAmount"))).append("，费用 ¥")
                            .append(money(row.get("fees"))).append("\n");
                }
            }
        }
        if (!any) {
            sb.append(emptyText).append("\n");
        }
    }

    private void appendCleared(StringBuilder sb, Object cleared) {
        boolean any = false;
        if (cleared instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = asMap(item);
                any = true;
                sb.append("#### ").append(value(row, "stockName", "—")).append("（")
                        .append(value(row, "symbol", "—")).append("）\n");
                sb.append("- 清仓日 ").append(value(row, "clearedDate", "—"))
                        .append("，持有 ").append(value(row, "holdDays", "—")).append(" 天，卖出 ")
                        .append(value(row, "sellQuantity", "—")).append(" 股，均价 ")
                        .append(money(row.get("sellAvgPrice"))).append("\n");
                if (Boolean.TRUE.equals(row.get("dataMissing"))) {
                    sb.append("- 成本基准 —，已实现盈亏 —（成本数据缺失）\n");
                } else {
                    sb.append("- 成本基准 ").append(money(row.get("costBasis")))
                            .append("，已实现盈亏 ¥").append(money(row.get("realizedPnl")))
                            .append("（").append(percent(row.get("realizedRate"))).append("%）\n");
                }
                sb.append("- 复盘结论：卖出已执行，后续按原计划纪律执行\n");
            }
        }
        if (!any) {
            sb.append("本周无清仓个股\n");
        }
    }

    private void appendPlans(StringBuilder sb, Object plans) {
        boolean any = false;
        if (plans instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof TradePlanRes plan)) {
                    continue;
                }
                any = true;
                sb.append("### ").append(plan.getStockName()).append("（").append(plan.getSymbol()).append("）\n");
                sb.append("- 仓位与成本：").append(plan.getQuantity()).append(" 股，均价 ")
                        .append(money(plan.getAvgCost())).append("，现价 ")
                        .append(money(plan.getLastClose())).append("\n");
                sb.append("- 止盈止损：建议止损 ").append(money(plan.getStopLossPrice()))
                        .append("，建议止盈 ").append(money(plan.getTakeProfitPrice())).append("\n");
                sb.append("- 买入方式：").append(plan.getBuyMode()).append("\n");
                sb.append("- 提示：").append(Boolean.TRUE.equals(plan.getDegraded())
                        ? plan.getDegradedReason() : "无").append("\n");
            }
        }
        if (!any) {
            sb.append("当前无持仓交易计划\n");
        }
    }

    private void appendIndustry(StringBuilder sb, Object distribution) {
        if (distribution instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                Map<String, Object> row = asMap(item);
                sb.append("- ").append(value(row, "industry", "未分类")).append("：")
                        .append(value(row, "symbolCount", "0")).append(" 只，成本 ¥")
                        .append(money(row.get("cost"))).append("，占比 ")
                        .append(percent(row.get("costRatio"))).append("%\n");
            }
        } else {
            sb.append("- 暂无持仓行业数据\n");
        }
    }

    private void appendFocusIndustries(StringBuilder sb, Object industries) {
        if (industries instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                Map<String, Object> row = asMap(item);
                sb.append("- ").append(value(row, "name", "—")).append("｜热度 ")
                        .append(value(row, "hotScore", "—")).append("｜")
                        .append(value(row, "hypeLabel", "—")).append("\n");
            }
        } else {
            sb.append("暂无最新热点数据\n");
        }
    }

    private void appendFocusStocks(StringBuilder sb, Object stocks) {
        if (stocks instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                Map<String, Object> row = asMap(item);
                sb.append("- ").append(value(row, "name", "—")).append("(")
                        .append(value(row, "symbol", "—")).append(")｜来源：")
                        .append(value(row, "board", "—")).append(" 榜单｜综合分 ")
                        .append(value(row, "totalScore", "—")).append("\n");
            }
        } else {
            sb.append("暂无最新选股数据\n");
        }
    }

    private void trimToRetention(String userId) {
        List<TradePlanSummary> summaries = summaryRepository.findByUserIdOrderByWeekStartAsc(userId);
        int overflow = summaries.size() - properties.getSummaryRetentionWeeks();
        for (int i = 0; i < overflow; i++) {
            summaryRepository.delete(summaries.get(i));
        }
    }

    private String readResource(Resource resource) throws Exception {
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private String value(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String money(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
    }

    private String percent(Object value) {
        if (value == null) {
            return "—";
        }
        BigDecimal decimal;
        try {
            decimal = value instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
        return decimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
