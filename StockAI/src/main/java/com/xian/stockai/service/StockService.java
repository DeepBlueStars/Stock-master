package com.xian.stockai.service;

import com.xian.stockai.dao.*;
import com.xian.stockai.po.*;
import com.xian.stockai.service.tradeplan.TradePlanService;
import com.xian.stockai.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class StockService {
    private final StockTradeRepository stockTradeRepository;
    private final PositionRepository positionRepository;
    private final MarketQuotesRepository marketQuotesRepository;
    private final StockBasicInfoRepository stockBasicInfoRepository;
    private final EtfBasicInfoRepository etfBasicInfoRepository;
    private final TotalReturnRepository totalReturnRepository;
    private final RealizedReturnRepository realizedReturnRepository;
    private final UsersRepository usersRepository;
    private final AnalysisReportsRepository analysisReportsRepository;
    private final EtfDailyQuotesRepository etfDailyQuotesRepository;
    private final UserUseConfigDao userUseConfigDao;
    private final UserCategoryConfigRepository userCategoryConfigRepository;
    private final TradePlanService tradePlanService;

    public StockService(StockTradeRepository stockTradeRepository, PositionRepository positionRepository,
                        MarketQuotesRepository marketQuotesRepository, StockBasicInfoRepository stockBasicInfoRepository,
                        EtfBasicInfoRepository etfBasicInfoRepository, TotalReturnRepository totalReturnRepository,
                        RealizedReturnRepository realizedReturnRepository, UsersRepository usersRepository,
                        AnalysisReportsRepository analysisReportsRepository, EtfDailyQuotesRepository etfDailyQuotesRepository,
                        UserUseConfigDao userUseConfigDao, UserCategoryConfigRepository userCategoryConfigRepository,
                        TradePlanService tradePlanService) {
        this.stockTradeRepository = stockTradeRepository;
        this.positionRepository = positionRepository;
        this.marketQuotesRepository = marketQuotesRepository;
        this.stockBasicInfoRepository = stockBasicInfoRepository;
        this.etfBasicInfoRepository = etfBasicInfoRepository;
        this.totalReturnRepository = totalReturnRepository;
        this.realizedReturnRepository = realizedReturnRepository;
        this.usersRepository = usersRepository;
        this.analysisReportsRepository = analysisReportsRepository;
        this.etfDailyQuotesRepository = etfDailyQuotesRepository;
        this.userUseConfigDao = userUseConfigDao;
        this.userCategoryConfigRepository = userCategoryConfigRepository;
        this.tradePlanService = tradePlanService;
    }

    public Result<Void> buy(StockTrade stockTrade) {
        Result<Void> result = validateParams(stockTrade);
        if (!result.checkSuccess()) {
            return result;
        }

        // 计算佣金和过户费
        BigDecimal commission = calculateCommission(stockTrade.getAmount(), stockTrade.getSymbol());
        BigDecimal transferFee = calculateTransferFee(stockTrade.getAmount(), stockTrade.getSymbol()); // 过户费
        
        // 印花税为0（买入时不收）
        BigDecimal stampTax = BigDecimal.ZERO;
        
        // 实际成本 = 交易金额 + 佣金 + 过户费
        BigDecimal actualCost = stockTrade.getAmount().add(commission).add(transferFee);

        // 设置交易记录中的费用信息
        stockTrade.setCommission(commission);
        stockTrade.setTransferFee(transferFee);
        stockTrade.setStampTax(stampTax);
        stockTrade.setActualAmount(actualCost);

        // 根据userId和symbol查询持仓表
        Position position = positionRepository.findByUserIdAndSymbol(stockTrade.getUserId(), stockTrade.getSymbol());
        if (position == null) {
            position = new Position();
            position.setUserId(stockTrade.getUserId());
            position.setSymbol(stockTrade.getSymbol());
            position.setStockName(stockTrade.getStockName());
            position.setTotalQuantity(stockTrade.getQuantity());
            position.setAvgCostPrice(actualCost.divide(BigDecimal.valueOf(stockTrade.getQuantity()), 3, RoundingMode.HALF_UP));
            // 实际成本（含佣金和过户费）
            position.setTotalCost(actualCost);
            position.setCreatedAt(LocalDateTime.now());
        } else {
            position.setTotalQuantity(position.getTotalQuantity() + stockTrade.getQuantity());
            position.setTotalCost(position.getTotalCost().add(actualCost));
            position.setAvgCostPrice(position.getTotalCost().divide(BigDecimal.valueOf(position.getTotalQuantity()), 3, RoundingMode.HALF_UP));
        }
        position.setUpdatedAt(LocalDateTime.now());
        position.setType(stockTrade.getType());
        positionRepository.save(position);

        // 保存交易记录
        stockTrade.setCreatedAt(LocalDateTime.now());
        stockTrade.setUpdatedAt(LocalDateTime.now());
        stockTradeRepository.save(stockTrade);
        
        // 如果买入方式是现金，则扣除用户当前的现金
        if ("现金".equals(stockTrade.getBuyMethod())) {
            Optional<Users> userOpt = usersRepository.findById(stockTrade.getUserId());
            if (userOpt.isPresent()) {
                Users user = userOpt.get();
                if (user.getCash() == null) {
                    user.setCash(BigDecimal.ZERO);
                }
                if (user.getCash().compareTo(actualCost) < 0) {
                    return Result.fail("现金不足");
                }
                user.setCash(user.getCash().subtract(actualCost));
                usersRepository.save(user);
            }
        }
        
        // 买入后生成/刷新交易计划，失败不影响交易结果
        try {
            tradePlanService.refreshPlan(stockTrade.getUserId(), stockTrade.getSymbol());
        } catch (Exception e) {
            log.error("生成交易计划失败: userId={}, symbol={}", stockTrade.getUserId(), stockTrade.getSymbol(), e);
        }

        return result.setMessage("交易成功，佣金：" + commission + "，过户费：" + transferFee);
    }

    /**
     * 计算交易佣金
     * @param amount 交易金额
     * @return 佣金金额
     */
    private BigDecimal calculateCommission(BigDecimal amount, String symbol) {
        // 基础佣金：成交金额的万分之2.854
        BigDecimal baseCommissionRate = new BigDecimal("0.0002854"); // 万分之2.854 = 0.0002854
        BigDecimal baseCommission = amount.multiply(baseCommissionRate);
        BigDecimal minCommission;
        if (symbol.startsWith("1") || symbol.startsWith("5")) {
            // 购买ETF最低佣金0.1元
            minCommission = new BigDecimal("0.1");
        } else {
            // 够吗股票最低佣金5元
            minCommission = new BigDecimal("5");
        }
        BigDecimal finalCommission = baseCommission.max(minCommission);
        return finalCommission.setScale(3, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算过户费
     * @param amount 交易金额
     * @param symbol 股票代码
     * @return 过户费金额
     */
    private BigDecimal calculateTransferFee(BigDecimal amount, String symbol) {
        BigDecimal extraFee = BigDecimal.ZERO;
        // 如果是沪市股票（代码以6开头），额外收取成交金额的0.001%
        if (symbol.startsWith("6")) {
            BigDecimal extraFeeRate = new BigDecimal("0.00001"); // 0.001% = 0.00001
            extraFee = amount.multiply(extraFeeRate);
        }
        return extraFee.setScale(3, RoundingMode.HALF_UP);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> sell(StockTrade stockTrade) {
        Result<Void> result = validateParams(stockTrade);
        if (!result.checkSuccess()) {
            return result;
        }

        // 计算各项费用
        BigDecimal commission = calculateCommission(stockTrade.getAmount(), stockTrade.getSymbol());
        BigDecimal transferFee = calculateTransferFee(stockTrade.getAmount(), stockTrade.getSymbol());
        BigDecimal stampTax = calculateStampTax(stockTrade.getAmount(), stockTrade.getSymbol()); // 印花税
        
        // 总费用 = 佣金 + 过户费 + 印花税
        BigDecimal totalFees = commission.add(transferFee).add(stampTax);
        
        // 设置交易记录中的费用信息
        stockTrade.setCommission(commission);
        stockTrade.setTransferFee(transferFee);
        stockTrade.setStampTax(stampTax);
        stockTrade.setActualAmount(stockTrade.getAmount().subtract(totalFees));

        // 根据userId和symbol查询持仓表
        Position position = positionRepository.findByUserIdAndSymbol(stockTrade.getUserId(), stockTrade.getSymbol());
        if (position == null) {
            log.error("用户{}的{}持仓不存在", stockTrade.getUserId(), stockTrade.getSymbol());
            return Result.fail(stockTrade.getSymbol() + "持仓不存在");
        } else if (position.getTotalQuantity() < stockTrade.getQuantity()) {
            log.error("用户{}的{}持仓数量不足", stockTrade.getUserId(), stockTrade.getSymbol());
            return Result.fail(stockTrade.getSymbol() + "持仓数量不足");
        } else {
            // 计算总收益: 1.获取最近的实际收益
            RealizedReturn realizedReturn = realizedReturnRepository.findFirstByUserIdOrderByCreatedAtDesc(stockTrade.getUserId());
            if (realizedReturn == null) {
                realizedReturn = new RealizedReturn();
                realizedReturn.setRealizedReturn(BigDecimal.ZERO);
            }
            realizedReturn.setUserId(stockTrade.getUserId());
            // 2. 实际收益 = 最近实际收益 + 当前价格 * 卖出数量 - 卖出部分对应成本 - 过户费 - 佣金 - 印花税
            // 注意：分批卖出时只能摊销与卖出数量对应的成本，不能扣减整个持仓的总成本
            BigDecimal soldCost = position.getTotalCost()
                    .multiply(BigDecimal.valueOf(stockTrade.getQuantity()))
                    .divide(BigDecimal.valueOf(position.getTotalQuantity()), 2, RoundingMode.HALF_UP);
            BigDecimal profit = stockTrade.getPrice().multiply(BigDecimal.valueOf(stockTrade.getQuantity()))
                    .subtract(soldCost)
                    .subtract(transferFee).subtract(commission).subtract(stampTax);
            realizedReturn.setRealizedReturn(realizedReturn.getRealizedReturn().add(profit));
            realizedReturn.setCreatedAt(LocalDateTime.now());
            realizedReturn.setUpdatedAt(LocalDateTime.now());
            realizedReturnRepository.save(realizedReturn);

            // 采用加权平均算法，卖出股票持仓均价不变
            position.setTotalQuantity(position.getTotalQuantity() - stockTrade.getQuantity());
            if (position.getTotalQuantity() > 0) {
                position.setTotalCost(position.getAvgCostPrice().multiply(BigDecimal.valueOf(position.getTotalQuantity())));
            } else {
                position.setAvgCostPrice(BigDecimal.valueOf(0));
                position.setTotalCost(BigDecimal.valueOf(0));
            }
            position.setUpdatedAt(LocalDateTime.now());
            positionRepository.save(position);
        }

        // 保存交易记录
        stockTrade.setCreatedAt(LocalDateTime.now());
        stockTrade.setUpdatedAt(LocalDateTime.now());
        stockTradeRepository.save(stockTrade);
        
        // 卖出股票时，增加用户现金
        Optional<Users> userOpt = usersRepository.findById(stockTrade.getUserId());
        if (userOpt.isPresent()) {
            Users user = userOpt.get();
            BigDecimal sellAmount = stockTrade.getAmount().subtract(totalFees);
            if (user.getCash() == null) {
                user.setCash(BigDecimal.ZERO);
            }
            user.setCash(user.getCash().add(sellAmount));
            usersRepository.save(user);
        }
        
        return result.setMessage("交易成功，佣金：" + commission + "，过户费：" + transferFee + "，印花税：" + stampTax);
    }

    /**
     * 计算印花税
     * @param amount 交易金额
     * @return 印花税金额
     */
    private BigDecimal calculateStampTax(BigDecimal amount, String symbol) {
        if (symbol.startsWith("1") || symbol.startsWith("5")) {
            // ETF不征收印花税
            return BigDecimal.ZERO;
        }
        // 印花税目前只对卖出股票征收，税率为成交金额的0.05%
        BigDecimal stampTaxRate = new BigDecimal("0.0005"); // 0.05% = 0.0005
        return amount.multiply(stampTaxRate).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 参数验证
     * @param stockTrade 交易记录
     * @return 验证结果
     */
    private Result<Void> validateParams(StockTrade stockTrade) {
        if (stockTrade.getUserId() == null || stockTrade.getUserId().isEmpty()) {
            log.error("用户ID不能为空");
            return Result.fail("用户ID不能为空");
        }
        if (stockTrade.getQuantity() <= 0 || stockTrade.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("数量或价格不能小于等于0");
            return Result.fail("数量或价格不能小于等于0");
        }
        stockTrade.setAmount(new BigDecimal(stockTrade.getQuantity()).multiply(stockTrade.getPrice()));
        return Result.success("参数验证成功", null);
    }

    /**
     * 查询用户指定标的的持仓
     * @param userId 用户ID
     * @param symbol 股票代码
     * @return 持仓，不存在时返回 null
     */
    public Position getPosition(String userId, String symbol) {
        return positionRepository.findByUserIdAndSymbol(userId, symbol);
    }

    /**
     * 获取持仓股票列表
     * @param userId 用户ID
     * @return 持仓股票列表
     */
    public Result<List<PositionRes>> getPositionList(String userId) {
        // 加上当前价格、当天涨跌幅，并计算总收益和收益率
        List<PositionRes> resList = new ArrayList<>();
        List<Position> list = positionRepository.findByUserId(userId);
        for (Position position : list) {
            if (position.getTotalQuantity() > 0) {
                PositionRes positionRes = new PositionRes();
                if (position.getSymbol().startsWith("1") || position.getSymbol().startsWith("5")) {
                    EtfDailyQuotes etfDailyQuotes = etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(position.getSymbol());
                    positionRes = PositionRes.fromPosition(position, etfDailyQuotes);
                } else {
                    MarketQuotes marketQuotes = marketQuotesRepository.findByCode(position.getSymbol());
                    positionRes = PositionRes.fromPosition(position, marketQuotes);
                }
                
                // 查询最新的分析报告并提取投资建议
                List<AnalysisReports> analysisReports = analysisReportsRepository.findBySymbolAndStatusCompletedOrderByCreatedAtDesc(position.getSymbol());
                if (!analysisReports.isEmpty() && analysisReports.get(0).getRecommendation() != null) {
                    AnalysisReports analysisReport = analysisReports.get(0); // 获取最新的分析报告
                    positionRes.setReportId(analysisReport.getId());

                    String regex = "投资建议：([^。]+)。目标价格：(\\d+(?:\\.\\d+)?元)";
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
                    java.util.regex.Matcher matcher = pattern.matcher(analysisReport.getRecommendation());
                    if (matcher.find()) {
                        String advice = matcher.group(1);  // 投资建议内容
                        String price = matcher.group(2);  // 目标价格
                        positionRes.setRecommendation(advice + "：" + price);
                    } else {
                        positionRes.setRecommendation(analysisReport.getRecommendation());
                    }
                }
                
                resList.add(positionRes);
            }
        }
        return Result.success(resList);
    }

    /**
     * 获取持仓股票信息
     * @param userId 用户ID
     * @return 持仓股票信息
     */
    public Result<BasicPositionRes> getBasicPositionList(String userId) {
        BasicPositionRes res = new BasicPositionRes();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        // 获取持仓信息
        List<Position> list = positionRepository.findByUserId(userId);
        for (Position position : list) {
            if (position.getTotalQuantity() > 0) {
                PositionRes positionRes = new PositionRes();
                if (position.getSymbol().startsWith("1") || position.getSymbol().startsWith("5")) {
                    EtfDailyQuotes etfDailyQuotes = etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(position.getSymbol());
                    positionRes = PositionRes.fromPosition(position, etfDailyQuotes);
                } else {
                    MarketQuotes marketQuotes = marketQuotesRepository.findByCode(position.getSymbol());
                    positionRes = PositionRes.fromPosition(position, marketQuotes);
                }
                totalValue = totalValue.add(positionRes.getTotalValue());
                totalCost = totalCost.add(positionRes.getTotalCost());
            }
        }

        res.setPositionCount(list.size());
        res.setTotalValue(totalValue);
        res.setTotalCost(totalCost);
        res.setTotalProfit(totalValue.subtract(totalCost));
        if (!BigDecimal.ZERO.equals(totalCost)) {
            res.setTotalProfitRate(res.getTotalProfit().divide(totalCost, 4, RoundingMode.HALF_UP));
        } else {
            res.setTotalProfitRate(BigDecimal.ZERO);
        }

        // 获取交易次数
        res.setTradeCount(stockTradeRepository.countByUserId(userId));
        return Result.success(res);
    }

    /**
     * 获取交易记录列表
     * @param req 查询条件
     * @return 交易记录列表
     */
    public Result<List<StockTrade>> getTradeList(StockTradeReq req) {
        List<StockTrade> trades;
        if (req.getUserId() != null && !req.getUserId().isEmpty()) {
            if (req.getCode() != null && !req.getCode().isEmpty()) {
                trades = stockTradeRepository.findByUserIdAndSymbol(req.getUserId(), req.getCode());
            } else {
                trades = stockTradeRepository.findByUserId(req.getUserId());
            }
        } else {
            trades = stockTradeRepository.findAll();
        }
        return Result.success(trades);
    }

    public Result<StockBasicInfo> getStockBasicInfo(String symbol) {
        StockBasicInfo stock = null;
        // 如果symbol以"1"或"5"开头，从etf_basic_info查询
        if (symbol.startsWith("1") || symbol.startsWith("5")) {
            EtfBasicInfo etf = etfBasicInfoRepository.findBySymbol(symbol);
            if (etf != null) {
                // 转换为StockBasicInfo对象返回
                stock = new StockBasicInfo();
                stock.setId(etf.getId());
                stock.setSymbol(etf.getSymbol());
                stock.setName(etf.getName());
            }
        } else {
            // 否则从stock_basic_info查询
            stock = stockBasicInfoRepository.findBySymbol(symbol);
        }
        if (stock == null) {
            log.error("股票或ETF{}不存在", symbol);
            return Result.fail(symbol + "不存在");
        }
        return Result.success(stock);
    }

    /**
     * 计算总收益
     */
    public void calculateTotalReturn() {
        // 查询所有已注册用户
        List<Users> users = usersRepository.findAll();
        for (Users user : users) {
            // 获取最近的总收益
            RealizedReturn realizedReturn = realizedReturnRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
            TotalReturn totalReturn = new TotalReturn();
            totalReturn.setUserId(user.getId());

            if (realizedReturn == null) {
                totalReturn.setTotalReturn(BigDecimal.ZERO);
            } else {
                totalReturn.setTotalReturn(realizedReturn.getRealizedReturn());
            }

            // 获取该用户的持仓
            List<Position> positions = positionRepository.findByUserId(user.getId());
            for (Position position : positions) {
                BigDecimal currentPrice = BigDecimal.ZERO;
                // 区分ETF和普通股票
                if (position.getSymbol().startsWith("1") || position.getSymbol().startsWith("5")) {
                    // ETF查询etfDailyQuotesRepository
                    EtfDailyQuotes etfDailyQuotes = etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(position.getSymbol());
                    if (etfDailyQuotes != null) {
                        currentPrice = etfDailyQuotes.getClose();
                    }
                } else {
                    // 普通股票查询marketQuotesRepository
                    MarketQuotes marketQuotes = marketQuotesRepository.findByCode(position.getSymbol());
                    if (marketQuotes != null) {
                        currentPrice = marketQuotes.getClose();
                    }
                }
                // 计算收益
                if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ret = currentPrice.multiply(BigDecimal.valueOf(position.getTotalQuantity())).subtract(position.getTotalCost());
                    totalReturn.setTotalReturn(totalReturn.getTotalReturn().add(ret));
                }
            }

            log.info("用户{}的总收益为{}", user.getId(), totalReturn.getTotalReturn());

            totalReturn.setCreatedAt(LocalDateTime.now());
            totalReturn.setUpdatedAt(LocalDateTime.now());
            totalReturnRepository.save(totalReturn);
        }
    }

    /**
     * 根据userId和时间查询总收益
     * @param req 查询条件
     * @return 总收益列表
     */
    public Result<List<TotalReturn>> listTotalReturn(ReturnReq req) {
        String userId = req.getUserId();
        LocalDateTime startTime = req.getStartTime();
        LocalDateTime endTime = req.getEndTime();
        
        List<TotalReturn> result;
        if (startTime == null && endTime == null) {
            // 当开始时间和结束时间都为null时，查询用户的所有总收益记录
            result = totalReturnRepository.findByUserId(userId);
        } else {
            result = totalReturnRepository.findByUserIdAndCreatedAtBetween(userId, startTime, endTime);
        }
        
        return Result.success(result);
    }
    
    /**
     * 获取用户资产配置分类持仓金额
     * @param userId 用户ID
     * @return 资产配置分类持仓金额列表
     */
    public Result<List<AssetCategoryPositionRes>> getAssetCategoryPosition(String userId) {
        try {
            // 1. 获取用户当前使用的分类配置
            UserUseConfig userUseConfig = userUseConfigDao.findByUserId(userId);
            if (userUseConfig == null) {
                log.error("用户{}未配置资产分类", userId);
                return Result.fail("用户未配置资产分类");
            }
            
            // 2. 获取用户资产配置详情
            UserCategoryConfig userCategoryConfig = userCategoryConfigRepository.findById(userUseConfig.getConfigId()).orElse(null);
            if (userCategoryConfig == null) {
                log.error("用户{}的资产分类配置{}不存在", userId, userUseConfig.getConfigId());
                return Result.fail("资产分类配置不存在");
            }
            
            // 3. 解析配置JSON（假设config字段是JSON格式）
            com.alibaba.fastjson2.JSONObject configJson;
            try {
                configJson = com.alibaba.fastjson2.JSON.parseObject(userCategoryConfig.getConfig());
            } catch (Exception e) {
                log.error("解析用户{}的资产分类配置失败: {}", userId, e.getMessage());
                return Result.fail("资产分类配置格式错误");
            }
            
            // 4. 获取配置中的assets列表
            java.util.List<com.alibaba.fastjson2.JSONObject> assets = configJson.getJSONArray("assets").toJavaList(com.alibaba.fastjson2.JSONObject.class);
            if (assets == null || assets.isEmpty()) {
                log.error("用户{}的资产分类配置中未配置资产", userId);
                return Result.fail("资产分类配置中未配置资产");
            }
            
            // 5. 获取持仓记录；没有持仓也要返回配置类别，
            //    配置结构 / 策略建议的类别轴必须来自同一份配置
            List<Position> positions = positionRepository.findByUserId(userId);
            java.util.Set<String> configuredNames = new java.util.LinkedHashSet<>();
            for (com.alibaba.fastjson2.JSONObject asset : assets) {
                String name = asset.getString("name");
                if (name != null && !name.isBlank()) {
                    configuredNames.add(name);
                }
            }

            // 6. 按「配置类别」汇总持仓金额
            java.util.Map<String, BigDecimal> categoryAmountMap = new java.util.HashMap<>();

            if (positions != null) {
                for (Position position : positions) {
                    if (position.getTotalQuantity() <= 0) {
                        continue;
                    }

                    // 获取当前价格
                    BigDecimal currentPrice = BigDecimal.ZERO;
                    if (position.getSymbol().startsWith("1") || position.getSymbol().startsWith("5")) {
                        EtfDailyQuotes etfDailyQuotes = etfDailyQuotesRepository.findTopBySymbolOrderByTradeDateDescUpdatedAtDesc(position.getSymbol());
                        if (etfDailyQuotes != null) {
                            currentPrice = etfDailyQuotes.getClose();
                        }
                    } else {
                        MarketQuotes marketQuotes = marketQuotesRepository.findByCode(position.getSymbol());
                        if (marketQuotes != null) {
                            currentPrice = marketQuotes.getClose();
                        }
                    }

                    if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    // 计算持仓金额
                    BigDecimal positionAmount = currentPrice.multiply(BigDecimal.valueOf(position.getTotalQuantity()));

                    // 买入时选的 type 就是配置类别；老数据里的「基金/ETF」按代码归到配置类别
                    String category = resolvePositionCategory(position.getType(), position.getSymbol(), configuredNames);
                    if (category == null) {
                        continue;
                    }
                    categoryAmountMap.merge(category, positionAmount, BigDecimal::add);
                }
            }

            // 7. 只返回配置里的类别：左侧滑杆、右侧结构、策略建议共用这一套口径
            List<AssetCategoryPositionRes> result = new java.util.ArrayList<>();
            for (com.alibaba.fastjson2.JSONObject asset : assets) {
                String name = asset.getString("name");
                BigDecimal amount = categoryAmountMap.getOrDefault(name, BigDecimal.ZERO);

                // 如果是现金类，从users集合中的cash属性获取
                if ("现金".equals(name)) {
                    Optional<Users> userOpt = usersRepository.findById(userId);
                    if (userOpt.isPresent() && userOpt.get().getCash() != null) {
                        amount = userOpt.get().getCash();
                    }
                }

                AssetCategoryPositionRes res = new AssetCategoryPositionRes();
                res.setCategoryName(name);
                res.setPositionAmount(amount);
                res.setType(asset.getString("type"));
                res.setColor(asset.getString("color"));

                result.add(res);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取用户{}资产配置分类持仓金额失败: {}", userId, e.getMessage(), e);
            return Result.fail("获取资产配置分类持仓金额失败");
        }
    }

    /**
     * 把持仓落到配置类别上。买入时选的 type 就是配置类别，优先直接用；
     * 老数据里的「基金」「ETF」或空值按代码前缀推断，再并入配置类别：
     * 配置里没有「基金」桶时并入「股票」（同属权益仓），保证页面类别和配置完全一致。
     */
    static String resolvePositionCategory(String positionType, String symbol, java.util.Set<String> configuredNames) {
        if (configuredNames == null || configuredNames.isEmpty()) {
            return null;
        }
        if (positionType != null && configuredNames.contains(positionType.trim())) {
            return positionType.trim();
        }
        String inferred = resolveAssetCategory(symbol);
        if (configuredNames.contains(inferred)) {
            return inferred;
        }
        if ("基金".equals(inferred) && configuredNames.contains("股票")) {
            return "股票";
        }
        if (configuredNames.contains("股票")) {
            return "股票";
        }
        return configuredNames.iterator().next();
    }

    /**
     * 把持仓代码归到资产类别。规则按代码前缀，不依赖 position.type：
     * 黄金 ETF（518xxx）、债券 ETF（511xxx、少数 159xxx）、其余 ETF/LOF（1/5 开头）算基金，
     * 剩下的当股票。分不清的一律按股票处理，宁可少分一类也不要把钱漏掉。
     */
    static String resolveAssetCategory(String symbol) {
        if (symbol == null) {
            return "股票";
        }
        String code = symbol.trim();
        if (code.isEmpty()) {
            return "股票";
        }
        if (code.startsWith("518") || code.startsWith("159934")) {
            return "黄金";
        }
        if (code.startsWith("511") || code.startsWith("159926") || code.startsWith("159972")) {
            return "债券";
        }
        if (code.startsWith("1") || code.startsWith("5")) {
            return "基金";
        }
        return "股票";
    }
}