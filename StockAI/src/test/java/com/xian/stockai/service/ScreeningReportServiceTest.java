package com.xian.stockai.service;

import com.xian.stockai.po.BoardEntry;
import com.xian.stockai.po.HotspotDirection;
import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.HotspotStock;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningReportServiceTest {

    @Mock
    private ChatClient chatClient;

    private ScreeningReportService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningReportService(chatClient);
    }

    private BoardEntry entry(String symbol, String name, String totalScore) {
        BoardEntry e = new BoardEntry();
        e.setSymbol(symbol);
        e.setName(name);
        e.setTotalScore(new BigDecimal(totalScore));
        e.setValuation(80);
        e.setGrowth(75);
        e.setQuality(90);
        e.setMomentum(60);
        e.setLongTermRating(ScreeningService.RATING_HIGH);
        e.setReason("ROE 池内前 15%");
        return e;
    }

    private ScreeningResult resultWithBoards() {
        ScreeningResult result = new ScreeningResult();
        result.setTradeDate("2026-09-01");
        result.setBalancedBoard(List.of(entry("600519", "贵州茅台", "95.50")));
        result.setValueBoard(List.of(entry("601318", "中国平安", "88.20")));
        result.setGrowthBoard(List.of(entry("300750", "宁德时代", "91.30")));
        return result;
    }

    private HotspotResult hotspotWithHypeRisk() {
        HotspotDirection medicine = new HotspotDirection();
        medicine.setName("医药");
        medicine.setHotScore(80);
        medicine.setHypeLabel(HotspotService.HYPE_RISK);
        medicine.setHypeBasis("质量中位数 35，热度得分 80");
        medicine.setStockCount(3);
        medicine.setLimitUpCount(1);
        medicine.setDailyAvgChange(new BigDecimal("0.086667"));
        medicine.setFiveDayAvgChange(new BigDecimal("0.086667"));
        medicine.setTopStocks(List.of());
        HotspotResult hotspot = new HotspotResult();
        hotspot.setTradeDate("2026-09-01");
        hotspot.setIndustries(List.of(medicine));
        hotspot.setConcepts(List.of());
        return hotspot;
    }

    private List<StockFactor> factors() {
        StockFactor f = new StockFactor();
        f.setSymbol("600519");
        f.setName("贵州茅台");
        f.setValuation(80);
        f.setGrowth(75);
        f.setQuality(90);
        f.setMomentum(60);
        f.setDataIncomplete(false);
        return List.of(f);
    }

    private void mockChatSuccess(String content) {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(content);
    }

    @Test
    void attachReportUsesAiContentOnSuccess() {
        mockChatSuccess("AI 综合报告内容");
        ScreeningResult result = resultWithBoards();
        service.attachReport(result, hotspotWithHypeRisk(), factors());
        assertEquals("AI 综合报告内容", result.getAiReport());
        assertEquals(ScreeningReportService.STATUS_SUCCESS, result.getAiReportStatus());
    }

    @Test
    void attachReportFallsBackWhenAiFails() {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        ScreeningResult result = resultWithBoards();
        service.attachReport(result, hotspotWithHypeRisk(), factors());

        assertEquals(ScreeningReportService.STATUS_FALLBACK, result.getAiReportStatus());
        // 降级文本必须包含评分依据（分数）与炒作风险提示（标注）
        assertTrue(result.getAiReport().contains("[量化结论]"));
        assertTrue(result.getAiReport().contains("95.50"));
        assertTrue(result.getAiReport().contains("纯炒作风险高"));
    }

    @Test
    void promptContainsBoardsAndHotspotHype() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(captor.capture())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("ok");

        service.attachReport(resultWithBoards(), hotspotWithHypeRisk(), factors());

        String prompt = captor.getValue();
        assertTrue(prompt.contains("贵州茅台"));
        assertTrue(prompt.contains("纯炒作风险高"));
        assertTrue(prompt.contains("优质池共 1 只"));
    }

    @Test
    void fallbackHandlesEmptyInputs() {
        ChatClient.ChatClientRequestSpec spec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        ScreeningResult empty = new ScreeningResult();
        service.attachReport(empty, null, List.of());
        assertEquals(ScreeningReportService.STATUS_FALLBACK, empty.getAiReportStatus());
        assertTrue(empty.getAiReport().contains("暂无"));
        assertTrue(empty.getAiReport().contains("[量化结论]"));
    }
}
