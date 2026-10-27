package com.xian.stockai.task;

import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.TradePlanSummary;
import com.xian.stockai.service.tradeplan.TradePlanSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradePlanSummaryTaskTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TradePlanSummaryService summaryService;

    private TradePlanSummaryTask task;

    @BeforeEach
    void setUp() {
        task = new TradePlanSummaryTask(positionRepository, summaryService);
    }

    private Position position(String userId) {
        Position position = new Position();
        position.setUserId(userId);
        position.setSymbol("600519");
        position.setTotalQuantity(100);
        return position;
    }

    @Test
    void generatesForEveryActiveUserAndContinuesAfterFailure() {
        when(positionRepository.findByTotalQuantityGreaterThan(0))
                .thenReturn(List.of(position("u1"), position("u2")));
        when(summaryService.generateWeekly("u1")).thenThrow(new RuntimeException("boom"));
        when(summaryService.generateWeekly("u2")).thenReturn(new TradePlanSummary());

        int failed = task.generateAll();

        assertEquals(1, failed);
        verify(summaryService).generateWeekly("u1");
        verify(summaryService).generateWeekly("u2");
    }

    @Test
    void deduplicatesUsersWithMultiplePositions() {
        when(positionRepository.findByTotalQuantityGreaterThan(0))
                .thenReturn(List.of(position("u1"), position("u1")));
        when(summaryService.generateWeekly("u1")).thenReturn(new TradePlanSummary());

        int failed = task.generateAll();

        assertEquals(0, failed);
        verify(summaryService, org.mockito.Mockito.times(1)).generateWeekly("u1");
    }
}
