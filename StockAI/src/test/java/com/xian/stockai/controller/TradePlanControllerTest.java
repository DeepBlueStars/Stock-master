package com.xian.stockai.controller;

import com.xian.stockai.po.TradePlanSummary;
import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.service.tradeplan.TradePlanSummaryService;
import com.xian.stockai.vo.MonitorLinkRes;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanRes;
import com.xian.stockai.vo.TradePlanSummaryBriefRes;
import com.xian.stockai.vo.TradePlanSummaryRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradePlanControllerTest {

    @Mock
    private TradePlanService tradePlanService;
    @Mock
    private TradePlanSummaryService tradePlanSummaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradePlanController(tradePlanService, tradePlanSummaryService))
                .build();
    }

    @Test
    void listDelegatesToService() throws Exception {
        TradePlanRes res = new TradePlanRes();
        res.setSymbol("600519");
        res.setStockName("贵州茅台");
        when(tradePlanService.list("u1")).thenReturn(Result.success(List.of(res)));

        mockMvc.perform(post("/trade-plan/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data[0].symbol").value("600519"));
    }

    @Test
    void linkMonitorDelegatesToService() throws Exception {
        when(tradePlanService.linkMonitor("u1", "600519")).thenReturn(Result.success(new MonitorLinkRes()));

        mockMvc.perform(post("/trade-plan/link-monitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\",\"symbol\":\"600519\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));

        verify(tradePlanService).linkMonitor("u1", "600519");
    }

    @Test
    void summaryLatestDelegatesToService() throws Exception {
        TradePlanSummaryRes res = new TradePlanSummaryRes();
        res.setWeekStart("2026-10-19");
        res.setContent("# 周报");
        when(tradePlanSummaryService.latest("u1")).thenReturn(Result.success(res));

        mockMvc.perform(post("/trade-plan/summary/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"));
    }

    @Test
    void summaryListDelegatesToService() throws Exception {
        TradePlanSummaryBriefRes brief = new TradePlanSummaryBriefRes();
        brief.setWeekStart("2026-10-19");
        when(tradePlanSummaryService.history("u1")).thenReturn(Result.success(List.of(brief)));

        mockMvc.perform(post("/trade-plan/summary/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].weekStart").value("2026-10-19"));
    }

    @Test
    void summaryDetailDelegatesToService() throws Exception {
        when(tradePlanSummaryService.detail("u1", "2026-10-19"))
                .thenReturn(Result.success(new TradePlanSummaryRes()));

        mockMvc.perform(post("/trade-plan/summary/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\",\"weekStart\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));

        verify(tradePlanSummaryService).detail("u1", "2026-10-19");
    }

    @Test
    void summaryGenerateDelegatesToService() throws Exception {
        when(tradePlanSummaryService.generateWeekly("u1")).thenReturn(new TradePlanSummary());

        mockMvc.perform(post("/trade-plan/summary/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));

        verify(tradePlanSummaryService).generateWeekly("u1");
    }
}
