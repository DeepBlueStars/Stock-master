package com.xian.stockai.vo;

import com.xian.stockai.po.ExitSignal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xian
 * @description 一次卖出纪律判定的结果
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExitEvaluationRes {

    private String runId;

    private String tradeDate;

    private int evaluated;

    private int triggered;

    private int skipped;

    private List<ExitSignal> signals = new ArrayList<>();

    private List<String> skippedReasons = new ArrayList<>();
}
