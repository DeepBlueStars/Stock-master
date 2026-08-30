package com.xian.stockai.service;

import com.xian.stockai.dao.IndexDailyQuotesRepository;
import com.xian.stockai.dao.IndexValuationRepository;
import com.xian.stockai.dao.MarketRegimeRecordRepository;
import com.xian.stockai.po.IndexDailyQuotes;
import com.xian.stockai.po.IndexValuation;
import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.po.RegimeIndicators;
import com.xian.stockai.service.notify.FeishuNotificationSender;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketRegimeServiceTest {

    @Mock
    private IndexDailyQuotesRepository indexDailyQuotesRepository;
    @Mock
    private IndexValuationRepository indexValuationRepository;
    @Mock
    private MarketRegimeRecordRepository marketRegimeRecordRepository;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ObjectProvider<FeishuNotificationSender> feishuProvider;

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(
                indexDailyQuotesRepository, indexValuationRepository, marketRegimeRecordRepository,
                chatClient, feishuProvider, "sh000300",
                new BigDecimal("0.30"), new BigDecimal("0.80"));
    }

    private IndexDailyQuotes quote(String close, String volume) {
        IndexDailyQuotes q = new IndexDailyQuotes();
        q.setClose(new BigDecimal(close));
        q.setVolume(new BigDecimal(volume));
        return q;
    }

    private List<IndexDailyQuotes> trendingQuotes() {
        List<IndexDailyQuotes> list = new ArrayList<>();
        // 仓库按 tradeDate 降序返回（最新在前）：递增收盘价对应最新收盘价最高
        for (int i = 0; i < 250; i++) {
            list.add(quote(String.valueOf(3249 - i), "1000000"));
        }
        return list;
    }

    @Test
    void calculateIndicatorsComputesMaAndRatio() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        IndexValuation val = new IndexValuation();
        val.setPePercentile(new BigDecimal("0.60"));
        val.setPbPercentile(new BigDecimal("0.55"));
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(val);

        RegimeIndicators ind = service.calculateIndicators("sh000300");

        assertNotNull(ind);
        // 递增收盘价：MA20 > MA60 > MA250
        assertTrue(ind.getMa20().compareTo(ind.getMa60()) > 0);
        assertTrue(ind.getMa60().compareTo(ind.getMa250()) > 0);
        assertEquals(0, new BigDecimal("0.60").compareTo(ind.getPePercentile()));
        assertEquals(0, new BigDecimal("0.55").compareTo(ind.getPbPercentile()));
    }

    @Test
    void calculateIndicatorsReturnsNullWhenInsufficient() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(List.of(quote("3000", "1000000")));
        assertNull(service.calculateIndicators("sh000300"));
    }

    @Test
    void evaluateAndSaveWritesRecordAndPushesFeishu() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        FeishuNotificationSender sender = mock(FeishuNotificationSender.class);
        when(feishuProvider.getIfAvailable()).thenReturn(sender);

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        assertEquals(RegimeRule.REGIME_BULL, result.getData().getRegime());
        assertEquals(RegimeRule.ADVICE_AGGRESSIVE, result.getData().getPositionAdvice());
        assertTrue(result.getData().getReport().contains("量化结论"));
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
        verify(sender).send(any());
    }

    @Test
    void evaluateAndSaveSkipsPushWhenFeishuDisabled() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        when(feishuProvider.getIfAvailable()).thenReturn(null);

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
    }

    @Test
    void evaluateAndSaveFallsBackWhenAiFails() {
        when(indexDailyQuotesRepository.findTop250ByIndexCodeOrderByTradeDateDesc("sh000300"))
                .thenReturn(trendingQuotes());
        when(indexValuationRepository.findTopByIndexCodeOrderByTradeDateDesc("sh000300")).thenReturn(null);
        when(feishuProvider.getIfAvailable()).thenReturn(null);

        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("AI down"));

        Result<MarketRegimeRecord> result = service.evaluateAndSave();

        assertTrue(result.checkSuccess());
        assertTrue(result.getData().getReport().contains("量化结论"));
        verify(marketRegimeRecordRepository).save(any(MarketRegimeRecord.class));
    }
}
