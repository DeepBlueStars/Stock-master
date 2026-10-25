package com.xian.stockai.config;

import com.xian.stockai.po.HotspotResult;
import com.xian.stockai.po.ScreeningResult;
import com.xian.stockai.po.ScreeningScoreRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private IndexOperations indexOperations;

    @Test
    void ensuresUniqueAscendingTradeDateIndexOnAllCollections() {
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(indexOperations);

        new ScreeningIndexInitializer(mongoTemplate).run(null);

        verify(mongoTemplate).indexOps(ScreeningScoreRecord.class);
        verify(mongoTemplate).indexOps(ScreeningResult.class);
        verify(mongoTemplate).indexOps(HotspotResult.class);
        ArgumentCaptor<Index> captor = ArgumentCaptor.forClass(Index.class);
        verify(indexOperations, times(3)).ensureIndex(captor.capture());
        for (Index index : captor.getAllValues()) {
            assertEquals(1, ((Number) index.getIndexKeys().get("trade_date")).intValue());
            assertTrue(index.getIndexOptions().getBoolean("unique", false));
        }
    }

    @Test
    void keepsGoingWhenOneCollectionIndexFails() {
        when(mongoTemplate.indexOps(ScreeningScoreRecord.class))
                .thenThrow(new RuntimeException("mongo down"));
        when(mongoTemplate.indexOps(ScreeningResult.class)).thenReturn(indexOperations);
        when(mongoTemplate.indexOps(HotspotResult.class)).thenReturn(indexOperations);

        assertDoesNotThrow(() -> new ScreeningIndexInitializer(mongoTemplate).run(null));

        verify(mongoTemplate).indexOps(ScreeningResult.class);
        verify(mongoTemplate).indexOps(HotspotResult.class);
        verify(indexOperations, times(2)).ensureIndex(any(Index.class));
    }
}
