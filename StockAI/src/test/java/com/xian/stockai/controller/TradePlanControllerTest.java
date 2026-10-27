package com.xian.stockai.controller;

import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.vo.MonitorLinkRes;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.TradePlanRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradePlanControllerTest {

    @Mock
    private TradePlanService tradePlanService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TradePlanController(tradePlanService)).build();
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
}
