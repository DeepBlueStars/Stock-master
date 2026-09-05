package com.xian.stockai.task;

import com.xian.stockai.dao.ScreeningResultRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.service.ScreeningReportService;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningTaskTest {

    @Mock
    private ScreeningService screeningService;
    @Mock
    private HotspotService hotspotService;
    @Mock
    private ScreeningReportService screeningReportService;
    @Mock
    private ScreeningResultRepository screeningResultRepository;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;

    private ScreeningTask task;

    @BeforeEach
    void setUp() {
        task = new ScreeningTask(screeningService, hotspotService, screeningReportService,
                screeningResultRepository, stockDailyKlineRepository);
    }

    private void mockHappyPath() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        List<StockFactor> factors = List.of(new StockFactor());
        when(screeningService.calculateAndSaveSnapshot(anyString())).thenReturn(factors);
        when(screeningService.buildBoard(eq("value"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("growth"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("balanced"), any())).thenReturn(List.of());
    }

    @Test
    void runDailyUsesScreeningCron() throws Exception {
        Method method = ScreeningTask.class.getMethod("runDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals("${screening.cron:0 0 18 * * MON-FRI}", scheduled.cron());
    }

    @Test
    void runScreeningSkipsWhenNoKlineToday() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(false);
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        verifyNoInteractions(screeningService);
        verifyNoInteractions(hotspotService);
        verifyNoInteractions(screeningReportService);
        verify(screeningResultRepository, never()).save(any());
    }

    @Test
    void runScreeningOrchestratesStepsInOrder() {
        mockHappyPath();
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        InOrder inOrder = inOrder(screeningService, hotspotService, screeningReportService,
                screeningResultRepository);
        inOrder.verify(screeningService).calculateAndSaveSnapshot(anyString());
        inOrder.verify(screeningService).buildBoard(eq("value"), any());
        inOrder.verify(screeningService).buildBoard(eq("growth"), any());
        inOrder.verify(screeningService).buildBoard(eq("balanced"), any());
        inOrder.verify(hotspotService).buildAndSave(anyString());
        inOrder.verify(screeningReportService).attachReport(any(), any(), any());
        inOrder.verify(screeningResultRepository).save(any(ScreeningResult.class));
    }

    @Test
    void runScreeningFailsFastWhenSnapshotFails() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenThrow(new RuntimeException("db down"));
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        verifyNoInteractions(hotspotService);
        verify(screeningResultRepository, never()).save(any());
    }

    @Test
    void runScreeningContinuesWhenBoardFails() {
        when(stockDailyKlineRepository.existsByTradeDate(anyString())).thenReturn(true);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenReturn(List.of(new StockFactor()));
        when(screeningService.buildBoard(eq("value"), any()))
                .thenThrow(new RuntimeException("board boom"));
        when(screeningService.buildBoard(eq("growth"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("balanced"), any())).thenReturn(List.of());

        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        verify(hotspotService).buildAndSave(anyString());
        verify(screeningReportService).attachReport(any(), any(), any());
        verify(screeningResultRepository).save(any(ScreeningResult.class));
    }

    @Test
    void runScreeningContinuesWhenHotspotFails() {
        mockHappyPath();
        when(hotspotService.buildAndSave(anyString()))
                .thenThrow(new RuntimeException("hotspot boom"));
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        verify(screeningReportService).attachReport(any(), any(), any());
        verify(screeningResultRepository).save(any(ScreeningResult.class));
    }
}
