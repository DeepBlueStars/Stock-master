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
import com.xian.stockai.dao.UserUseConfigDao;
import com.xian.stockai.dao.UsersRepository;
import com.xian.stockai.po.Position;
import com.xian.stockai.po.RealizedReturn;
import com.xian.stockai.po.StockTrade;
import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卖出收益摊销测试：分批卖出时只能扣减卖出部分对应的成本。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceSellCostTest {

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

    @Test
    void partialSellDeductsOnlySoldPortionCost() {
        Position position = new Position();
        position.setUserId("u1");
        position.setSymbol("600519");
        position.setTotalQuantity(300);
        position.setTotalCost(new BigDecimal("3000.000"));
        position.setAvgCostPrice(new BigDecimal("10.000"));
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position);
        when(realizedReturnRepository.findFirstByUserIdOrderByCreatedAtDesc("u1")).thenReturn(null);

        StockTrade sell = new StockTrade();
        sell.setUserId("u1");
        sell.setSymbol("600519");
        sell.setStockName("贵州茅台");
        sell.setSide("卖出");
        sell.setQuantity(100);
        sell.setPrice(new BigDecimal("12.00"));

        Result<Void> result = service.sell(sell);
        assertTrue(result.checkSuccess());

        ArgumentCaptor<RealizedReturn> captor = ArgumentCaptor.forClass(RealizedReturn.class);
        verify(realizedReturnRepository).save(captor.capture());

        // 卖出 100/300 股，对应成本 1000.00；收入 1200.00；再扣三项费用
        BigDecimal fees = sell.getCommission().add(sell.getTransferFee()).add(sell.getStampTax());
        BigDecimal expected = new BigDecimal("1200.00")
                .subtract(new BigDecimal("1000.00"))
                .subtract(fees);

        assertEquals(0, expected.compareTo(captor.getValue().getRealizedReturn()),
                "实得分批卖出收益应为 收入-卖出部分成本-费用，实际=" + captor.getValue().getRealizedReturn()
                        + "，期望=" + expected);
    }

    @Test
    void fullSellDeductsWholeCost() {
        Position position = new Position();
        position.setUserId("u1");
        position.setSymbol("600519");
        position.setTotalQuantity(100);
        position.setTotalCost(new BigDecimal("1000.000"));
        position.setAvgCostPrice(new BigDecimal("10.000"));
        when(positionRepository.findByUserIdAndSymbol("u1", "600519")).thenReturn(position);
        when(realizedReturnRepository.findFirstByUserIdOrderByCreatedAtDesc("u1")).thenReturn(null);

        StockTrade sell = new StockTrade();
        sell.setUserId("u1");
        sell.setSymbol("600519");
        sell.setStockName("贵州茅台");
        sell.setSide("卖出");
        sell.setQuantity(100);
        sell.setPrice(new BigDecimal("12.00"));

        Result<Void> result = service.sell(sell);
        assertTrue(result.checkSuccess());

        ArgumentCaptor<RealizedReturn> captor = ArgumentCaptor.forClass(RealizedReturn.class);
        verify(realizedReturnRepository).save(captor.capture());

        BigDecimal fees = sell.getCommission().add(sell.getTransferFee()).add(sell.getStampTax());
        BigDecimal expected = new BigDecimal("1200.00").subtract(new BigDecimal("1000.000")).subtract(fees);

        assertEquals(0, expected.compareTo(captor.getValue().getRealizedReturn()));
    }
}
