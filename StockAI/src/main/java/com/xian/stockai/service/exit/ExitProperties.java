package com.xian.stockai.service.exit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 卖出纪律的参数。所有比例都用小数表示（0.25 = 25%）。
 */
@Component
@ConfigurationProperties(prefix = "exit")
@Getter
@Setter
public class ExitProperties {

    /**
     * L1 硬止损：跌到成本的 (1 - hardStopRate) 清仓
     */
    private BigDecimal hardStopRate = new BigDecimal("0.25");

    /**
     * 个股波动放大时的放宽阈值：ATR / 现价 超过该值就放宽硬止损
     */
    private BigDecimal hardStopRelaxAtrRatio = new BigDecimal("0.04");

    private BigDecimal hardStopRelaxedRate = new BigDecimal("0.30");

    /**
     * ETF 硬止损按 60 日年化波动率分档：[上限, 止损比例]，从小到大匹配，最后一项是不封顶的兜底
     */
    private List<EtfHardStopTier> etfHardStopTiers = new ArrayList<>(List.of(
            new EtfHardStopTier(new BigDecimal("0.15"), new BigDecimal("0.15")),
            new EtfHardStopTier(new BigDecimal("0.25"), new BigDecimal("0.20")),
            new EtfHardStopTier(null, new BigDecimal("0.25"))
    ));

    /**
     * ETF 硬止损换档的滞回带宽：波动率要越过档位边界这么多才换档，
     * 否则 σ 在 15% 附近来回抖就会让止损线在 −15% / −20% 之间跳 5 个点
     */
    private BigDecimal etfHardStopHysteresis = new BigDecimal("0.02");

    /**
     * L2 移动止盈：浮盈达到该比例武装
     */
    private BigDecimal trailingArmReturn = new BigDecimal("0.30");

    /**
     * L2 回撤宽度
     */
    private BigDecimal trailingRate = new BigDecimal("0.15");

    /**
     * 武装后的保本线
     */
    private BigDecimal breakevenReturn = new BigDecimal("0.10");

    /**
     * L4 两档止盈
     */
    private BigDecimal takeProfit1Return = new BigDecimal("0.30");
    private BigDecimal takeProfit1Fraction = new BigDecimal("0.3333");
    private BigDecimal takeProfit2Return = new BigDecimal("0.60");
    private BigDecimal takeProfit2Fraction = new BigDecimal("0.3333");

    /**
     * 加仓后多少个交易日内不重置 L2 状态
     */
    private int addBufferTradeDays = 10;

    /**
     * 同一信号冷却多少个交易日，避免阈值附近反复打扰
     */
    private int signalCooldownTradeDays = 5;

    /**
     * 卖出信号是否同时推送通知（默认只落库，由接口/前端消费）
     */
    private boolean notifyEnabled = false;

    /**
     * 判定时点：等日线落库之后再算
     */
    private String cron = "0 30 17 ? * MON-FRI";

    /**
     * 未传 userId 时使用默认用户（与 MCP 工具保持一致）
     */
    private String defaultUserId = "";

    @Getter
    @Setter
    public static class EtfHardStopTier {
        /**
         * 波动率上限（含），null 表示不封顶
         */
        private BigDecimal maxVolatility;
        private BigDecimal hardStopRate;

        public EtfHardStopTier() {
        }

        public EtfHardStopTier(BigDecimal maxVolatility, BigDecimal hardStopRate) {
            this.maxVolatility = maxVolatility;
            this.hardStopRate = hardStopRate;
        }
    }
}
