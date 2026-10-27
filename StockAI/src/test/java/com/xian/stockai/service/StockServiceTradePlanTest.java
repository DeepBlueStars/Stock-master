package com.xian.stockai.service;

import com.xian.stockai.dao.AnalysisReportsRepository;
import com.xian.stockai.dao.EtfBasicInfoRepository;
import com.xian.stockai.dao.EtfDailyQuotesRepository;
import com.xian.stockai.dao.MarketQuotesRepository;
import com.xian.stockai.dao.PositionRepository;
import com.xian.stockai.dao.RealizedReturnRepository;
import com.xian.stockai.dao.StockBasicInfoRepository;
import com.xian.stockai.dao.StockTradeRepository;
import com.xian.stockai.dao.TotalReturnRepository;
import com.xian.stockai.dao.UserCategoryConfigRepository;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.dao.UserUseConfigDao;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTradePlanTest {

    @Mock
    private StockTradeRepository stockTradeRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private MarketQuotesRepository marketQuotesRepository;
    @Mock
    private StockBasicInfoRepository stockBasicInfoRepository;
    @Mock
    private EtfBasicInfoRepository etfBasicInfoRepository;
    @Mock
    private TotalReturnRepository totalReturnRepository;
    @Mock
    private RealizedReturnRepository realizedReturnRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private AnalysisReportsRepository analysisReportsRepository;
    @Mock
    private EtfDailyQuotesRepository etfDailyQuotesRepository;
    @Mock
    private UserUseConfigDao userUseConfigDao;
    @Mock
    private UserCategoryConfigRepository userCategoryConfigRepository;
    @Mock
    private TradePlanService tradePlanService;

    private StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(stockTradeRepository, positionRepository, marketQuotesRepository,
                stockBasicInfoRepository, etfBasicInfoRepository, totalReturnRepository,
                realizedReturnRepository, usersRepository, analysisReportsRepository,
                etfDailyQuotesRepository, userUseConfigDao, userCategoryConfigRepository, tradePlanService);
    }

    private StockTrade buyTrade() {
        StockTrade trade = new StockTrade();
        trade.setUserId("u1");
        trade.setSymbol("600519");
        trade.setStockName("贵州茅台");
        trade.setSide("BUY");
        trade.setQuantity(100);
        trade.setPrice(new BigDecimal("10.00"));
        trade.setBuyMethod("银行卡");
        return trade;
    }

    @Test
    void buyTriggersTradePlanRefresh() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(null);

        Result<Void> result = service.buy(buyTrade());

        assertTrue(result.checkSuccess());
        verify(tradePlanService).refreshPlan("u1", "600519");
    }

    @Test
    void buySucceedsWhenPlanRefreshFails() {
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(null);
        doThrow(new RuntimeException("plan boom")).when(tradePlanService).refreshPlan(anyString(), anyString());

        Result<Void> result = service.buy(buyTrade());

        assertTrue(result.checkSuccess());
        assertTrue(result.getMessage().contains("交易成功"));
    }
}
