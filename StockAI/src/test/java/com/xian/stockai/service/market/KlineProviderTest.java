package com.xian.stockai.service.market;

import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.StockDailyKlineRepository;
import com.xian.stockai.po.EtfDailyQuotes;
import com.xian.stockai.po.StockDailyKline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KlineProviderTest {

    @Mock
    private StockDailyKlineRepository stockDailyKlineRepository;
    @Mock
    private EtfDailyQuotesRepository etfDailyQuotesRepository;

    private KlineProvider provider;

    @BeforeEach
    void setUp() {
        provider = new KlineProvider(stockDailyKlineRepository, etfDailyQuotesRepository);
    }

    private EtfDailyQuotes etfQuote(String tradeDate, String close, String high, String low) {
        EtfDailyQuotes quote = new EtfDailyQuotes();
        quote.setSymbol("515180");
        quote.setTradeDate(tradeDate);
        quote.setClose(new BigDecimal(close));
        quote.setOpen(new BigDecimal(close));
        quote.setHigh(new BigDecimal(high));
        quote.setLow(new BigDecimal(low));
        return quote;
    }

    private StockDailyKline stockKline(String tradeDate, String close) {
        StockDailyKline kline = new StockDailyKline();
        kline.setSymbol("600519");
        kline.setTradeDate(tradeDate);
        kline.setOpen(new BigDecimal(close));
        kline.setHigh(new BigDecimal(close));
        kline.setLow(new BigDecimal(close));
        kline.setClose(new BigDecimal(close));
        return kline;
    }

    @Test
    void etfSymbolReadsEtfCollectionAndReturnsAscending() {
        // 仓库按交易日倒序返回，provider 需翻成升序
        when(etfDailyQuotesRepository.findTop250BySymbolOrderByTradeDateDesc("515180"))
                .thenReturn(List.of(etfQuote("2026-10-30", "1.383", "1.400", "1.370"),
                        etfQuote("2026-10-29", "1.373", "1.390", "1.360")));

        List<StockDailyKline> klines = provider.ascending("515180");

        assertEquals(2, klines.size());
        assertEquals("2026-10-29", klines.get(0).getTradeDate());
        assertEquals("2026-10-30", klines.get(1).getTradeDate());
        assertEquals(0, new BigDecimal("1.383").compareTo(klines.get(1).getClose()));
        verify(etfDailyQuotesRepository).findTop250BySymbolOrderByTradeDateDesc("515180");
    }

    @Test
    void etfZeroHighLowFallsBackToClose() {
        // 上游把 high/low 写成 0 的脏数据：补齐成 close，保证 ATR 不为 0
        when(etfDailyQuotesRepository.findTop250BySymbolOrderByTradeDateDesc("515180"))
                .thenReturn(List.of(etfQuote("2026-10-30", "1.383", "0", "0")));

        List<StockDailyKline> klines = provider.ascending("515180");

        assertEquals(1, klines.size());
        StockDailyKline kline = klines.get(0);
        assertEquals(0, new BigDecimal("1.383").compareTo(kline.getHigh()));
        assertEquals(0, new BigDecimal("1.383").compareTo(kline.getLow()));
        assertTrue(kline.getHigh().compareTo(kline.getClose()) >= 0);
        assertTrue(kline.getLow().compareTo(kline.getClose()) <= 0);
    }

    @Test
    void stockSymbolReadsStockDailyKline() {
        when(stockDailyKlineRepository.findTop250BySymbolOrderByTradeDateDesc("600519"))
                .thenReturn(List.of(stockKline("2026-10-30", "1500"), stockKline("2026-10-29", "1490")));

        List<StockDailyKline> klines = provider.ascending("600519");

        assertEquals(2, klines.size());
        assertEquals("2026-10-29", klines.get(0).getTradeDate());
        assertEquals(0, new BigDecimal("1490").compareTo(klines.get(0).getClose()));
        verify(stockDailyKlineRepository).findTop250BySymbolOrderByTradeDateDesc("600519");
    }

    @Test
    void missingDataReturnsEmptyList() {
        when(etfDailyQuotesRepository.findTop250BySymbolOrderByTradeDateDesc("512480")).thenReturn(new ArrayList<>());

        assertTrue(provider.ascending("512480").isEmpty());
        assertTrue(provider.ascending("").isEmpty());
        assertTrue(provider.ascending(null).isEmpty());
    }
}
