package com.xian.stockai.controller;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HotspotControllerTest {

    @Mock
    private HotspotService hotspotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HotspotController(hotspotService)).build();
    }

    @Test
    void latestReturnsServiceResult() throws Exception {
        HotspotResult record = new HotspotResult();
        record.setTradeDate("2026-09-01");
        when(hotspotService.latest()).thenReturn(Result.success(record));

        mockMvc.perform(post("/hotspot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));

        verify(hotspotService).latest();
    }

    @Test
    void latestFailsWhenNoData() throws Exception {
        when(hotspotService.latest()).thenReturn(Result.fail("暂无热点结果"));
        mockMvc.perform(post("/hotspot/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.message").value("暂无热点结果"));
    }
}
