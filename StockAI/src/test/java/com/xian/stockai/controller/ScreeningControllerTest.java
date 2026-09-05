package com.xian.stockai.controller;

import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.task.ScreeningTask;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScreeningControllerTest {

    @Mock
    private ScreeningService screeningService;
    @Mock
    private ScreeningTask screeningTask;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScreeningController(screeningService, screeningTask))
                .build();
    }

    @Test
    void latestReturnsServiceResult() throws Exception {
        ScreeningResult record = new ScreeningResult();
        record.setTradeDate("2026-09-01");
        when(screeningService.latest()).thenReturn(Result.success(record));

        mockMvc.perform(post("/screening/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));

        verify(screeningService).latest();
    }

    @Test
    void latestFailsWhenNoData() throws Exception {
        when(screeningService.latest()).thenReturn(Result.fail("暂无选股结果"));
        mockMvc.perform(post("/screening/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.message").value("暂无选股结果"));
    }

    @Test
    void historyReadsPageAndSizeFromBody() throws Exception {
        when(screeningService.history(1, 5)).thenReturn(Result.success(List.of()));
        mockMvc.perform(post("/screening/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":1,\"size\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));
        verify(screeningService).history(1, 5);
    }

    @Test
    void historyUsesDefaultsWhenBodyMissing() throws Exception {
        when(screeningService.history(anyInt(), anyInt())).thenReturn(Result.success(List.of()));
        mockMvc.perform(post("/screening/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0));
        verify(screeningService).history(1, 10);
    }

    @Test
    void runDelegatesToTask() throws Exception {
        ScreeningResult record = new ScreeningResult();
        record.setTradeDate("2026-09-01");
        when(screeningTask.runScreening()).thenReturn(Result.success(record));
        mockMvc.perform(post("/screening/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(0))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-09-01"));
        verify(screeningTask).runScreening();
    }
}
