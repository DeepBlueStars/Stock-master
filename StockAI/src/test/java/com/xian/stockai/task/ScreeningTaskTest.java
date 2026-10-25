package com.xian.stockai.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xian.stockai.dao.IndexConstituentsRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.IndexConstituents;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.StockFactor;
import com.xian.stockai.service.HotspotService;
import com.xian.stockai.service.ScreeningReportService;
import com.xian.stockai.service.ScreeningService;
import com.xian.stockai.vo.Result;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    private MongoTemplate mongoTemplate;
    @Mock
    private MongoConverter mongoConverter;
    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private IndexConstituentsRepository indexConstituentsRepository;

    private ScreeningTask task;

    @BeforeEach
    void setUp() {
        task = new ScreeningTask(screeningService, hotspotService, screeningReportService,
                mongoTemplate, stockDailyKlineRepository, indexConstituentsRepository);
    }

    /** 模拟 POJO → Document 转换，供 upsert 写入路径使用。 */
    private void stubUpsertConversion() {
        when(mongoTemplate.getConverter()).thenReturn(mongoConverter);
        doAnswer(invocation -> {
            ScreeningResult result = invocation.getArgument(0);
            Document doc = invocation.getArgument(1);
            if (result.getId() != null) {
                doc.put("_id", result.getId());
            }
            doc.put("trade_date", result.getTradeDate());
            doc.put("generated_at", result.getGeneratedAt());
            doc.put("value", result.getValueBoard());
            doc.put("growth", result.getGrowthBoard());
            doc.put("balanced", result.getBalancedBoard());
            doc.put("ai_report", result.getAiReport());
            doc.put("ai_report_status", result.getAiReportStatus());
            return null;
        }).when(mongoConverter).write(any(ScreeningResult.class), any(Document.class));
    }

    private IndexConstituents constituent(String symbol) {
        IndexConstituents c = new IndexConstituents();
        c.setSymbol(symbol);
        return c;
    }

    private void mockCoverage(List<String> poolSymbols, long coveredCount) {
        when(indexConstituentsRepository.findAll()).thenReturn(
                poolSymbols.stream().map(this::constituent).toList());
        when(stockDailyKlineRepository.countByTradeDate(anyString())).thenReturn(coveredCount);
    }

    private void mockHappyPath() {
        stubUpsertConversion();
        mockCoverage(List.of("A", "B", "C", "D"), 3);
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
        mockCoverage(List.of("A", "B"), 0);
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        assertEquals("当日K线数据未就绪", result.getMessage());
        verifyNoInteractions(screeningService);
        verifyNoInteractions(hotspotService);
        verifyNoInteractions(screeningReportService);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void runScreeningLogsKlineCoverage() {
        mockHappyPath();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ScreeningTask.class);
        appender.start();
        logger.addAppender(appender);
        try {
            task.runScreening();
        } finally {
            logger.detachAppender(appender);
        }
        assertTrue(appender.list.stream()
                        .anyMatch(e -> e.getFormattedMessage().contains("当日K线覆盖 3/4")),
                "应记录当日 K 线覆盖率日志，实际: " + appender.list);
    }

    @Test
    void runScreeningOrchestratesStepsInOrder() {
        mockHappyPath();
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        InOrder inOrder = inOrder(screeningService, hotspotService, screeningReportService,
                mongoTemplate);
        inOrder.verify(screeningService).calculateAndSaveSnapshot(anyString());
        inOrder.verify(screeningService).buildBoard(eq("value"), any());
        inOrder.verify(screeningService).buildBoard(eq("growth"), any());
        inOrder.verify(screeningService).buildBoard(eq("balanced"), any());
        inOrder.verify(hotspotService).buildAndSave(anyString());
        inOrder.verify(screeningReportService).attachReport(any(), any(), any());
        inOrder.verify(mongoTemplate).upsert(any(Query.class), any(Update.class),
                eq(ScreeningResult.class));
    }

    @Test
    void runScreeningFailsFastWhenSnapshotFails() {
        mockCoverage(List.of("A", "B"), 1);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenThrow(new RuntimeException("db down"));
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
        verifyNoInteractions(hotspotService);
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class),
                eq(ScreeningResult.class));
    }

    @Test
    void runScreeningContinuesWhenBoardFails() {
        stubUpsertConversion();
        mockCoverage(List.of("A", "B"), 1);
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
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ScreeningResult.class));
    }

    @Test
    void runScreeningSkipsNullFieldsInUpsert() {
        stubUpsertConversion();
        mockCoverage(List.of("A", "B"), 1);
        when(screeningService.calculateAndSaveSnapshot(anyString()))
                .thenReturn(List.of(new StockFactor()));
        when(screeningService.buildBoard(eq("value"), any()))
                .thenThrow(new RuntimeException("board boom"));
        when(screeningService.buildBoard(eq("growth"), any())).thenReturn(List.of());
        when(screeningService.buildBoard(eq("balanced"), any())).thenReturn(List.of());

        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(),
                eq(ScreeningResult.class));

        assertEquals(LocalDate.now().toString(),
                queryCaptor.getValue().getQueryObject().getString("trade_date"));

        Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
        assertNotNull(set);
        assertFalse(set.containsKey("_id"), "不应更新 _id，实际: " + set);
        assertFalse(set.containsKey("value"), "失败的榜单不应以 null 覆盖旧值，实际: " + set);
        assertTrue(set.containsKey("growth"), "成功榜单应写入，实际: " + set);
        assertTrue(set.containsKey("balanced"), "成功榜单应写入，实际: " + set);
    }

    @Test
    void runScreeningContinuesWhenHotspotFails() {
        mockHappyPath();
        when(hotspotService.buildAndSave(anyString()))
                .thenThrow(new RuntimeException("hotspot boom"));
        Result<ScreeningResult> result = task.runScreening();
        assertTrue(result.checkSuccess());
        verify(screeningReportService).attachReport(any(), any(), any());
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ScreeningResult.class));
    }

    @Test
    void runScreeningFailsWhenResultUpsertFails() {
        mockHappyPath();
        doThrow(new RuntimeException("db down"))
                .when(mongoTemplate).upsert(any(Query.class), any(Update.class),
                        eq(ScreeningResult.class));
        Result<ScreeningResult> result = task.runScreening();
        assertFalse(result.checkSuccess());
    }
}
