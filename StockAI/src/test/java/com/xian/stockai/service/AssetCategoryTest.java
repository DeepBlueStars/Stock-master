package com.xian.stockai.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 资产分类只看代码前缀：分类错了钱就会在总览里凭空消失。
 */
class AssetCategoryTest {

    @Test
    void etfCodesAreClassifiedAsFund() {
        assertEquals("基金", StockService.resolveAssetCategory("588200"));
        assertEquals("基金", StockService.resolveAssetCategory("159915"));
        assertEquals("基金", StockService.resolveAssetCategory("513100"));
    }

    @Test
    void goldAndBondEtfsGetTheirOwnBucket() {
        assertEquals("黄金", StockService.resolveAssetCategory("518880"));
        assertEquals("债券", StockService.resolveAssetCategory("511010"));
    }

    @Test
    void stockCodesStayStocks() {
        assertEquals("股票", StockService.resolveAssetCategory("600519"));
        assertEquals("股票", StockService.resolveAssetCategory("000001"));
        assertEquals("股票", StockService.resolveAssetCategory("300750"));
        assertEquals("股票", StockService.resolveAssetCategory("688981"));
    }

    @Test
    void nullOrBlankFallsBackToStock() {
        assertEquals("股票", StockService.resolveAssetCategory(null));
        assertEquals("股票", StockService.resolveAssetCategory("   "));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals("基金", StockService.resolveAssetCategory(" 515180 "));
    }

    @Test
    void buyTypeWinsOverCodeInference() {
        Set<String> config = configured("股票", "债券", "黄金", "现金");
        assertEquals("债券", StockService.resolvePositionCategory("债券", "515180", config));
    }

    @Test
    void fundPositionFoldsIntoStockWhenConfigHasNoFund() {
        Set<String> config = configured("股票", "债券", "黄金", "现金");
        assertEquals("股票", StockService.resolvePositionCategory("基金", "515180", config));
        assertEquals("股票", StockService.resolvePositionCategory("ETF", "513100", config));
    }

    @Test
    void fundStaysFundWhenConfigHasFundBucket() {
        Set<String> config = configured("股票", "基金", "现金");
        assertEquals("基金", StockService.resolvePositionCategory("基金", "515180", config));
    }

    private static Set<String> configured(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }
}
