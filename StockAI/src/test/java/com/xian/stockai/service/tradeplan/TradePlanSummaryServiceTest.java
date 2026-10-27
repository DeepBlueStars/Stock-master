package com.xian.stockai.service.tradeplan;

import com.xian.stockai.config.TradePlanProperties;
import com.xian.stockai.dao.TradePlanSummaryRepository;
import com.xian.stockai.po.TradePlan;
import com.xian.stockai.po.TradePlanSummary;
import com.xian.stockai.vo.TradePlanRes;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradePlanSummaryServiceTest {

    private static final String VALID_CONTENT = """
            # 2026年第43周 交易计划周报（2026-10-19 ~ 2026-10-24）
            生成时间：2026-10-24T08:00

            ## 一、组合概览
            - 持仓 1 只

            ## 二、本周买卖回顾
            ### 2.1 买入
            本周无买入操作
            ### 2.2 卖出
            本周无卖出操作
            ### 2.3 清仓股复盘
            本周无清仓个股

            ## 三、当前持仓交易计划
            ### 贵州茅台（600519）
            - 仓位与成本：100 股

            ## 四、行业分布
            - 白酒：1 只

            ## 五、风险与关注
            1. 无

            ## 六、下周关注方向
            ### 6.1 行业方向
            暂无最新热点数据
            ### 6.2 个股观察
            暂无最新选股数据

            ## 七、下周行动清单
            - 本周无新增操作建议，维持既有计划

            ## 八、免责声明
            以上内容由量化规则与历史数据生成，仅作辅助参考，不构成投资建议；实际交易由用户自行决策并承担风险。
            """;

    @Mock
    private ChatClient chatClient;
    @Mock
    private TradePlanSummaryDataAssembler assembler;
    @Mock
    private TradePlanSummaryRepository repository;

    private TradePlanSummaryService service;

    @BeforeEach
    void setUp() {
        service = new TradePlanSummaryService(chatClient, assembler, repository, new TradePlanProperties(),
                new ClassPathResource("prompts/trade-plan-weekly-report.md"),
                new ClassPathResource("prompts/trade-plan-weekly-template.md"));
        when(assembler.assemble(anyString(), any(LocalDate.class), any(LocalDate.class))).thenReturn(data());
    }

    private Map<String, Object> data() {
        TradePlan plan = new TradePlan();
        plan.setUserId("u1");
        plan.setSymbol("600519");
        plan.setStockName("贵州茅台");
        plan.setQuantity(100);
        plan.setAvgCost(new BigDecimal("10.00"));
        plan.setLastClose(new BigDecimal("12.00"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekStart", "2026-10-19");
        data.put("weekEnd", "2026-10-24");
        data.put("generatedAt", "2026-10-24T08:00");
        data.put("summary", Map.of("positionCount", 1, "totalCost", new BigDecimal("1000.00")));
        data.put("weekTrades", List.of());
        data.put("clearedPositions", List.of());
        data.put("plans", List.of(TradePlanRes.from(plan)));
        data.put("industryDistribution", List.of());
        data.put("focusIndustries", List.of());
        data.put("focusStocks", List.of());
        return data;
    }

    private void mockChatSuccess(String content) {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(content);
    }

    @Test
    void fallsBackToQuantTextWhenChatClientThrows() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenThrow(new RuntimeException("timeout"));

        TradePlanSummary summary = service.generateWeekly("u1");

        assertEquals(TradePlanSummary.STATUS_FALLBACK, summary.getStatus());
        assertTrue(summary.getContent().contains("## 八、免责声明"));
        assertTrue(summary.getContent().contains("贵州茅台"));
        assertNotNull(summary.getFallbackReason());
        verify(repository).save(any(TradePlanSummary.class));
    }

    @Test
    void fallsBackWhenStructureIncomplete() {
        mockChatSuccess("## 一、组合概览\n内容缺失的周报");

        TradePlanSummary summary = service.generateWeekly("u1");

        assertEquals(TradePlanSummary.STATUS_FALLBACK, summary.getStatus());
        assertTrue(summary.getContent().contains("## 七、下周行动清单"));
    }

    @Test
    void savesSuccessWhenStructureValid() {
        mockChatSuccess(VALID_CONTENT);
        when(repository.findByUserIdAndWeekStart(anyString(), anyString())).thenReturn(Optional.empty());

        TradePlanSummary summary = service.generateWeekly("u1");

        assertEquals(TradePlanSummary.STATUS_SUCCESS, summary.getStatus());
        assertEquals("u1", summary.getUserId());
        assertEquals(LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString(), summary.getWeekStart());
        verify(repository).save(any(TradePlanSummary.class));
    }

    @Test
    void latestReturnsFailWhenNoSummary() {
        when(repository.findTopByUserIdOrderByWeekStartDesc("u1")).thenReturn(Optional.empty());

        Result<?> result = service.latest("u1");

        assertFalse(result.checkSuccess());
    }

    @Test
    void historyMapsBriefList() {
        TradePlanSummary summary = new TradePlanSummary();
        summary.setUserId("u1");
        summary.setWeekStart("2026-10-19");
        summary.setWeekEnd("2026-10-24");
        summary.setStatus(TradePlanSummary.STATUS_SUCCESS);
        summary.setContent("content");
        when(repository.findTop12ByUserIdOrderByWeekStartDesc("u1")).thenReturn(List.of(summary));

        Result<?> result = service.history("u1");

        assertTrue(result.checkSuccess());
        assertEquals(1, ((List<?>) result.getData()).size());
    }

    @Test
    void detailReturnsFailWhenWeekMissing() {
        when(repository.findByUserIdAndWeekStart("u1", "2026-10-19")).thenReturn(Optional.empty());

        Result<?> result = service.detail("u1", "2026-10-19");

        assertFalse(result.checkSuccess());
    }
}
