package com.xian.stockai.service;

import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScreeningReportService {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FALLBACK = "fallback";

    private final ChatClient chatClient;

    public ScreeningReportService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 生成 AI 综合报告并写入 result（失败降级纯量化文本，不影响榜单与热点）。
     */
    public void attachReport(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        try {
            result.setAiReport(generateReport(result, hotspot, factors));
            result.setAiReportStatus(STATUS_SUCCESS);
        } catch (Exception e) {
            log.error("AI 综合报告生成失败，降级为量化文本: {}", e.getMessage());
            result.setAiReport(buildFallbackReport(result, hotspot, factors));
            result.setAiReportStatus(STATUS_FALLBACK);
        }
    }

    String generateReport(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        String prompt = buildPrompt(result, hotspot, factors);
        return chatClient.prompt().user(prompt).call().content();
    }

    String buildPrompt(ScreeningResult result, HotspotResult hotspot, List<StockFactor> factors) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是选股助手。根据以下量化选股与热点识别结果，用中文输出一份不超过300字的选股综合报告，")
                .append("必须包含「市场风格」「重点方向」「风险提示」三部分，风险提示中必须包含对纯炒作风险方向的提醒。\n");
        appendBoard(sb, "均衡型", result.getBalancedBoard());
        appendBoard(sb, "价值型", result.getValueBoard());
        appendBoard(sb, "成长型", result.getGrowthBoard());
        if (hotspot != null) {
            appendDirections(sb, "热点行业", hotspot.getIndustries());
            appendDirections(sb, "热点概念", hotspot.getConcepts());
        }
        int poolSize = factors == null ? 0 : factors.size();
        sb.append("【统计】优质池共 ").append(poolSize).append(" 只；因子均分：")
                .append("估值 ").append(avgFactor(factors, StockFactor::getValuation))
                .append("、成长 ").append(avgFactor(factors, StockFactor::getGrowth))
                .append("、质量 ").append(avgFactor(factors, StockFactor::getQuality))
                .append("、动量 ").append(avgFactor(factors, StockFactor::getMomentum));
        return sb.toString();
    }

    private void appendBoard(StringBuilder sb, String label, List<BoardEntry> board) {
        sb.append("【").append(label).append("Top20】");
        if (board == null || board.isEmpty()) {
            sb.append("暂无\n");
            return;
        }
        for (BoardEntry e : board) {
            sb.append(e.getName()).append("(").append(e.getSymbol())
                    .append(",综合").append(e.getTotalScore()).append(");");
        }
        sb.append("\n");
    }

    private void appendDirections(StringBuilder sb, String label, List<HotspotDirection> directions) {
        sb.append("【").append(label).append("Top10】");
        if (directions == null || directions.isEmpty()) {
            sb.append("暂无\n");
            return;
        }
        for (HotspotDirection d : directions) {
            sb.append(d.getName()).append("(热度").append(d.getHotScore())
                    .append(",").append(hypeText(d.getHypeLabel())).append(");");
        }
        sb.append("\n");
    }

    private String avgFactor(List<StockFactor> factors, Function<StockFactor, Integer> getter) {
        if (factors == null || factors.isEmpty()) {
            return "无数据";
        }
        List<Integer> values = factors.stream().map(getter).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return "无数据";
        }
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 纯量化降级文本（包含评分依据与炒作风险提示）。
     */
    public String buildFallbackReport(ScreeningResult result, HotspotResult hotspot,
                                      List<StockFactor> factors) {
        StringBuilder sb = new StringBuilder("[量化结论] ");
        sb.append("均衡型前三：").append(topThree(result.getBalancedBoard()));
        sb.append("；价值型前三：").append(topThree(result.getValueBoard()));
        sb.append("；成长型前三：").append(topThree(result.getGrowthBoard()));
        if (hotspot != null) {
            sb.append("。热点行业：").append(directionSummary(hotspot.getIndustries()));
            sb.append("；热点概念：").append(directionSummary(hotspot.getConcepts()));
        }
        int poolSize = factors == null ? 0 : factors.size();
        sb.append("。优质池共 ").append(poolSize).append(" 只，AI 不可用，以上为纯量化结论。");
        return sb.toString();
    }

    private String topThree(List<BoardEntry> board) {
        if (board == null || board.isEmpty()) {
            return "暂无";
        }
        return board.stream().limit(3)
                .map(e -> e.getName() + "(" + e.getTotalScore() + "分)")
                .collect(Collectors.joining("、"));
    }

    private String directionSummary(List<HotspotDirection> directions) {
        if (directions == null || directions.isEmpty()) {
            return "暂无";
        }
        return directions.stream().limit(3)
                .map(d -> d.getName() + "(热度" + d.getHotScore() + "，" + hypeText(d.getHypeLabel()) + ")")
                .collect(Collectors.joining("、"));
    }

    private String hypeText(String label) {
        if (HotspotService.HYPE_RISK.equals(label)) {
            return "纯炒作风险高";
        }
        if (HotspotService.HYPE_SUPPORTED.equals(label)) {
            return "有基本面支撑";
        }
        return "中性";
    }
}
