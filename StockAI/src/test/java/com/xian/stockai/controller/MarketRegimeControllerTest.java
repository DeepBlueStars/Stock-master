package com.xian.stockai.controller;

import com.xian.stockai.po.MarketRegimeRecord;
import com.xian.stockai.service.MarketRegimeService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketRegimeControllerTest {

    @Mock
    private MarketRegimeService marketRegimeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketRegimeController(marketRegimeService)).build();
    }

    @Test
    void evaluateReturnsServiceResult() throws Exception {
        MarketRegimeRecord record = new MarketRegimeRecord();
        record.setRegime("BULL");
        when(marketRegimeService.evaluateAndSave()).thenReturn(Result.success(record));

        mockMvc.perform(post("/market-regime/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.regime").value("BULL"));

        verify(marketRegimeService).evaluateAndSave();
    }

    @Test
    void latestReturnsServiceResult() throws Exception {
        MarketRegimeRecord record = new MarketRegimeRecord();
        record.setPositionAdvice("激进（高仓位）");
        when(marketRegimeService.latest()).thenReturn(Result.success(record));

        mockMvc.perform(post("/market-regime/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.positionAdvice").value("激进（高仓位）"));

        verify(marketRegimeService).latest();
    }

    @Test
    void historyReturnsServiceResult() throws Exception {
        MarketRegimeRecord record = new MarketRegimeRecord();
        record.setDate("2026-08-29");
        when(marketRegimeService.history(30)).thenReturn(Result.success(List.of(record)));

        mockMvc.perform(post("/market-regime/history").param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data[0].date").value("2026-08-29"));

        verify(marketRegimeService).history(30);
    }

    @Test
    void historyUsesDefaultLimitThirty() throws Exception {
        when(marketRegimeService.history(30)).thenReturn(Result.success(List.of()));

        mockMvc.perform(post("/market-regime/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));

        verify(marketRegimeService).history(30);
    }
}
