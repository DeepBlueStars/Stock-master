package com.xian.stockai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author xian
 * @description 选股引擎配置（风格权重外置 application.yml，缺省值内置）
 */
@Component
@ConfigurationProperties(prefix = "screening")
public class ScreeningProperties {

    private Map<String, Map<String, BigDecimal>> weights = defaultWeights();

    private static Map<String, Map<String, BigDecimal>> defaultWeights() {
        Map<String, Map<String, BigDecimal>> w = new LinkedHashMap<>();
        w.put("value", style("0.40", "0.20", "0.30", "0.10"));
        w.put("growth", style("0.10", "0.40", "0.20", "0.30"));
        w.put("balanced", style("0.25", "0.25", "0.30", "0.20"));
        return w;
    }

    private static Map<String, BigDecimal> style(String valuation, String growth,
                                                String quality, String momentum) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("valuation", new BigDecimal(valuation));
        m.put("growth", new BigDecimal(growth));
        m.put("quality", new BigDecimal(quality));
        m.put("momentum", new BigDecimal(momentum));
        return m;
    }

    public Map<String, BigDecimal> weightsOf(String style) {
        Map<String, BigDecimal> w = weights.get(style);
        return w != null ? w : weights.get("balanced");
    }

    public Map<String, Map<String, BigDecimal>> getWeights() { return weights; }
    public void setWeights(Map<String, Map<String, BigDecimal>> weights) { this.weights = weights; }
}
