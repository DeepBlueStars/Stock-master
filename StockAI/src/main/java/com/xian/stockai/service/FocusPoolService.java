package com.xian.stockai.service;

import com.xian.stockai.dao.MonitorEventRepository;
import com.xian.stockai.dao.UserFavoritesRepository;
import com.xian.stockai.po.MonitorEvent;
import com.xian.stockai.po.StockDailyKline;
import com.xian.stockai.po.UserFavorites;
import com.xian.stockai.service.market.KlineProvider;
import com.xian.stockai.vo.FocusStockView;
import com.xian.stockai.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author xian
 * @description 关注池里的个股关注档案：报告 §10 / §12 / §14 的摘要，以及它和监测日历的连接。
 * <p>
 * 不单独建集合：关注档案挂在 user_favorites 的某只股票上（favorites[].focus），
 * 只改 focus 块，不动标签、备注、提醒价等其他端写入的字段。
 * 边界与复核线一致：只存结论、算距离、留痕，不自动下单、不因价格到点改状态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FocusPoolService {

    /**
     * 报告超过这么多天没更新，页面标「报告过期」
     */
    private static final int STALE_DAYS = 90;

    /**
     * 离第一档上沿不到 3%，算「接近买点」
     */
    private static final BigDecimal NEAR_BUY_RATIO = new BigDecimal("0.03");

    private static final int TIMELINE_MAX_SIZE = 50;

    private static final Set<String> STATUSES = Set.of(
            UserFavorites.FocusProfile.STATUS_WATCHING,
            UserFavorites.FocusProfile.STATUS_TRIGGERED,
            UserFavorites.FocusProfile.STATUS_HOLDING,
            UserFavorites.FocusProfile.STATUS_PAUSED,
            UserFavorites.FocusProfile.STATUS_DROPPED,
            UserFavorites.FocusProfile.STATUS_CLOSED);

    private static final Set<String> TIER_STATUSES = Set.of(
            UserFavorites.FocusProfile.TIER_WAITING,
            UserFavorites.FocusProfile.TIER_TRIGGERED,
            UserFavorites.FocusProfile.TIER_DONE,
            UserFavorites.FocusProfile.TIER_CANCELLED);

    private static final Map<String, Set<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_WATCHING, Set.of(
                UserFavorites.FocusProfile.STATUS_TRIGGERED,
                UserFavorites.FocusProfile.STATUS_PAUSED,
                UserFavorites.FocusProfile.STATUS_DROPPED));
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_TRIGGERED, Set.of(
                UserFavorites.FocusProfile.STATUS_HOLDING,
                UserFavorites.FocusProfile.STATUS_WATCHING,
                UserFavorites.FocusProfile.STATUS_PAUSED,
                UserFavorites.FocusProfile.STATUS_DROPPED));
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_HOLDING,
                Set.of(UserFavorites.FocusProfile.STATUS_CLOSED));
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_PAUSED, Set.of(
                UserFavorites.FocusProfile.STATUS_WATCHING,
                UserFavorites.FocusProfile.STATUS_DROPPED));
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_DROPPED,
                Set.of(UserFavorites.FocusProfile.STATUS_WATCHING));
        TRANSITIONS.put(UserFavorites.FocusProfile.STATUS_CLOSED, Set.of());
    }

    private final UserFavoritesRepository userFavoritesRepository;
    private final MonitorEventRepository monitorEventRepository;
    private final KlineProvider klineProvider;

    /* ---------------- 读写 ---------------- */

    public Result<UserFavorites.FocusProfile> save(String userId, UserFavorites.FocusProfile request) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getSymbol())) {
            return Result.fail("代码不能为空");
        }
        String symbol = request.getSymbol().trim();
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : null;
        if (status != null && !STATUSES.contains(status)) {
            return Result.fail("状态只能是 WATCHING / TRIGGERED / HOLDING / PAUSED / DROPPED / CLOSED");
        }
        LocalDateTime now = LocalDateTime.now();
        UserFavorites pool = loadOrCreatePool(userId, now);
        UserFavorites.FavoriteStock favorite = findFavorite(pool, symbol);
        boolean newFavorite = favorite == null;
        if (newFavorite) {
            favorite = new UserFavorites.FavoriteStock();
            favorite.setStockCode(symbol);
            favorite.setMarket("A股");
            favorite.setAddedAt(now);
            favorite.setTags(new ArrayList<>());
            favorite.setNotes("");
            pool.getFavorites().add(favorite);
        }
        UserFavorites.FocusProfile target = favorite.getFocus();
        List<String> changes = new ArrayList<>();
        if (target == null) {
            target = new UserFavorites.FocusProfile();
            target.setSymbol(symbol);
            target.setStatus(status != null ? status : UserFavorites.FocusProfile.STATUS_WATCHING);
            target.setStatusChangedAt(now);
            target.setTimeline(new ArrayList<>());
            changes.add(newFavorite ? "加入关注池并新建关注档案" : "新建关注档案");
        } else {
            collectChanges(target, request, changes);
            if (status != null) {
                target.setStatus(status);
            }
        }
        target.setSymbol(symbol);
        target.setStockName(text(request.getStockName(), favorite.getStockName()));
        if (StringUtils.hasText(request.getStockName())) {
            favorite.setStockName(request.getStockName().trim());
        }
        target.setReportId(text(request.getReportId(), target.getReportId()));
        target.setReportDate(request.getReportDate() != null ? request.getReportDate() : target.getReportDate());
        target.setRating(text(request.getRating(), target.getRating()));
        target.setSource(text(request.getSource(), target.getSource()));
        target.setStance(text(request.getStance(), target.getStance()));
        target.setOneLine(text(request.getOneLine(), target.getOneLine()));
        target.setCoreThesis(text(request.getCoreThesis(), target.getCoreThesis()));
        target.setBuyPlan(request.getBuyPlan() != null ? request.getBuyPlan() : target.getBuyPlan());
        target.setSellDiscipline(request.getSellDiscipline() != null
                ? request.getSellDiscipline() : target.getSellDiscipline());
        target.setMonitorItems(request.getMonitorItems() != null
                ? request.getMonitorItems() : target.getMonitorItems());
        target.setDecisionMemo(request.getDecisionMemo() != null
                ? request.getDecisionMemo() : target.getDecisionMemo());
        normalizeTiers(target);
        if (StringUtils.hasText(request.getStatusNote())) {
            target.setStatusNote(request.getStatusNote().trim());
            if (changes.size() == 1 && changes.get(0).startsWith("加入关注池")) {
                appendTimeline(target, UserFavorites.FocusProfile.TIMELINE_NOTE,
                        request.getStatusNote().trim(), now);
            }
        }
        if (!changes.isEmpty()) {
            appendTimeline(target, UserFavorites.FocusProfile.TIMELINE_REPORT_UPDATE,
                    String.join("；", changes), now);
        }
        favorite.setFocus(target);
        pool.setUpdatedAt(now);
        userFavoritesRepository.save(pool);
        syncMonitorEvents(userId, favorite, now);
        log.info("保存关注档案: userId={}, symbol={}, status={}, tiers={}, newFavorite={}",
                userId, symbol, target.getStatus(), tierCount(target), newFavorite);
        return Result.success(target);
    }

    public Result<List<FocusStockView>> list(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        Optional<UserFavorites> pool = userFavoritesRepository.findByUserId(userId);
        List<FocusStockView> views = new ArrayList<>();
        if (pool.isPresent() && pool.get().getFavorites() != null) {
            for (UserFavorites.FavoriteStock favorite : pool.get().getFavorites()) {
                if (favorite != null && favorite.getFocus() != null) {
                    views.add(toView(favorite));
                }
            }
        }
        views.sort(Comparator.comparing(
                (FocusStockView view) -> view.getProfile().getReportDate(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Result.success(views);
    }

    /**
     * 关注池全量：没有关注档案的股票也返回，profile 为空
     */
    public Result<List<FocusStockView>> pool(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        Optional<UserFavorites> found = userFavoritesRepository.findByUserId(userId);
        List<FocusStockView> views = new ArrayList<>();
        if (found.isPresent() && found.get().getFavorites() != null) {
            for (UserFavorites.FavoriteStock favorite : found.get().getFavorites()) {
                if (favorite != null && StringUtils.hasText(favorite.getStockCode())) {
                    views.add(toView(favorite));
                }
            }
        }
        views.sort(Comparator
                .comparing((FocusStockView view) -> view.getProfile() == null ? null : view.getProfile().getReportDate(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FocusStockView::getAddedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return Result.success(views);
    }

    public Result<FocusStockView> get(String userId, String symbol) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (!StringUtils.hasText(symbol)) {
            return Result.fail("代码不能为空");
        }
        UserFavorites.FavoriteStock favorite = findFavorite(
                userFavoritesRepository.findByUserId(userId).orElse(null), symbol.trim());
        if (favorite == null || favorite.getFocus() == null) {
            return Result.fail("关注档案不存在: " + symbol);
        }
        return Result.success(toView(favorite));
    }

    /**
     * 从关注池移除这只股票（连带关注档案），并清掉待处理的个股监测事件；报告原文不删
     */
    public Result<Void> delete(String userId, String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return Result.fail("代码不能为空");
        }
        String code = symbol.trim();
        Optional<UserFavorites> pool = userFavoritesRepository.findByUserId(userId);
        if (pool.isEmpty() || pool.get().getFavorites() == null) {
            return Result.success();
        }
        boolean removed = pool.get().getFavorites()
                .removeIf(item -> item != null && code.equals(item.getStockCode()));
        if (removed) {
            pool.get().setUpdatedAt(LocalDateTime.now());
            userFavoritesRepository.save(pool.get());
            deletePendingEvents(userId, code);
            log.info("从关注池移除: userId={}, symbol={}", userId, code);
        }
        return Result.success();
    }

    /* ---------------- 状态 ---------------- */

    public Result<UserFavorites.FocusProfile> updateStatus(String userId, String symbol, String status,
                                                           Integer tier, String tierStatus, String note) {
        if (!StringUtils.hasText(userId)) {
            return Result.fail("用户ID不能为空");
        }
        if (!StringUtils.hasText(symbol)) {
            return Result.fail("代码不能为空");
        }
        if (!StringUtils.hasText(status) && tier == null) {
            return Result.fail("status 与 tier 至少要传一个");
        }
        if (StringUtils.hasText(status) && !STATUSES.contains(status)) {
            return Result.fail("状态只能是 WATCHING / TRIGGERED / HOLDING / PAUSED / DROPPED / CLOSED");
        }
        if (tier != null && StringUtils.hasText(tierStatus) && !TIER_STATUSES.contains(tierStatus)) {
            return Result.fail("档位状态只能是 WAITING / TRIGGERED / DONE / CANCELLED");
        }
        Optional<UserFavorites> pool = userFavoritesRepository.findByUserId(userId);
        UserFavorites.FavoriteStock favorite = findFavorite(pool.orElse(null), symbol.trim());
        if (favorite == null || favorite.getFocus() == null) {
            return Result.fail("关注档案不存在: " + symbol);
        }
        UserFavorites.FocusProfile profile = favorite.getFocus();
        String from = profile.getStatus();
        String to = StringUtils.hasText(status) ? status : from;
        if (!to.equals(from)) {
            if (!canTransit(from, to)) {
                return Result.fail(cannotTransitMessage(from, to));
            }
            if ((UserFavorites.FocusProfile.STATUS_PAUSED.equals(to)
                    || UserFavorites.FocusProfile.STATUS_DROPPED.equals(to))
                    && !StringUtils.hasText(note)) {
                return Result.fail("暂停或放弃必须填写原因");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (!to.equals(from)) {
            profile.setStatus(to);
            profile.setStatusChangedAt(now);
            profile.setStatusNote(note);
            if (UserFavorites.FocusProfile.STATUS_HOLDING.equals(to)
                    && !StringUtils.hasText(profile.getPositionSymbol())) {
                profile.setPositionSymbol(profile.getSymbol());
            }
            appendTimeline(profile, UserFavorites.FocusProfile.TIMELINE_STATUS_CHANGE,
                    from + " → " + to + (StringUtils.hasText(note) ? "：" + note : ""), now);
        } else if (StringUtils.hasText(note)) {
            profile.setStatusNote(note);
            appendTimeline(profile, UserFavorites.FocusProfile.TIMELINE_NOTE, note, now);
        }
        if (tier != null) {
            Result<Void> tierResult = applyTierStatus(profile, tier, tierStatus, note, now);
            if (!tierResult.checkSuccess()) {
                return Result.fail(tierResult.getMessage());
            }
        }
        pool.get().setUpdatedAt(now);
        userFavoritesRepository.save(pool.get());
        if (UserFavorites.FocusProfile.STATUS_DROPPED.equals(to)) {
            deletePendingEvents(userId, profile.getSymbol());
        } else if (UserFavorites.FocusProfile.STATUS_WATCHING.equals(to)) {
            syncMonitorEvents(userId, favorite, now);
        }
        log.info("更新关注状态: userId={}, symbol={}, {} -> {}, tier={}", userId, symbol, from, to, tier);
        return Result.success(profile);
    }

    private boolean canTransit(String from, String to) {
        Set<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    private String cannotTransitMessage(String from, String to) {
        if (UserFavorites.FocusProfile.STATUS_CLOSED.equals(from)) {
            return "CLOSED 不能直接回到 " + to + "，请重新导入报告后新建档案";
        }
        if (UserFavorites.FocusProfile.STATUS_DROPPED.equals(from)) {
            return "DROPPED 只能恢复为 WATCHING，不能直接进入 " + to;
        }
        if (UserFavorites.FocusProfile.STATUS_HOLDING.equals(from)) {
            return "HOLDING 只能流转到 CLOSED";
        }
        return "不允许从 " + from + " 直接流转到 " + to;
    }

    private Result<Void> applyTierStatus(UserFavorites.FocusProfile profile, Integer tier, String tierStatus,
                                         String note, LocalDateTime now) {
        List<UserFavorites.BuyTier> tiers = profile.getBuyPlan() == null ? null : profile.getBuyPlan().getTiers();
        UserFavorites.BuyTier target = null;
        if (tiers != null) {
            for (UserFavorites.BuyTier item : tiers) {
                if (item != null && tier.equals(item.getTier())) {
                    target = item;
                    break;
                }
            }
        }
        if (target == null) {
            return Result.fail("档案里没有第 " + tier + " 档");
        }
        if (!StringUtils.hasText(tierStatus)) {
            return Result.success();
        }
        target.setStatus(tierStatus);
        if (StringUtils.hasText(note)) {
            target.setNote(note);
        }
        String label = StringUtils.hasText(target.getLabel()) ? target.getLabel() : "第 " + tier + " 档";
        if (UserFavorites.FocusProfile.TIER_TRIGGERED.equals(tierStatus)) {
            appendTimeline(profile, UserFavorites.FocusProfile.TIMELINE_TIER_TRIGGERED,
                    label + "已触发" + (StringUtils.hasText(note) ? "：" + note : ""), now);
        } else if (UserFavorites.FocusProfile.TIER_DONE.equals(tierStatus)) {
            appendTimeline(profile, UserFavorites.FocusProfile.TIMELINE_TIER_TRIGGERED,
                    label + "已成交" + (StringUtils.hasText(note) ? "：" + note : ""), now);
        }
        return Result.success();
    }

    /* ---------------- 监测日历同步 ---------------- */

    private void syncMonitorEvents(String userId, UserFavorites.FavoriteStock favorite, LocalDateTime now) {
        UserFavorites.FocusProfile profile = favorite.getFocus();
        if (profile == null || profile.getMonitorItems() == null || profile.getMonitorItems().isEmpty()) {
            return;
        }
        deletePendingEvents(userId, favorite.getStockCode());
        List<MonitorEvent> toSave = new ArrayList<>();
        for (UserFavorites.MonitorItem item : profile.getMonitorItems()) {
            if (item == null || item.getEventDate() == null || !StringUtils.hasText(item.getMetric())) {
                continue;
            }
            MonitorEvent event = new MonitorEvent();
            event.setUserId(userId);
            event.setReportId(profile.getReportId());
            event.setSymbol(favorite.getStockCode());
            event.setEventDate(item.getEventDate());
            event.setTitle((StringUtils.hasText(favorite.getStockName())
                    ? favorite.getStockName() : favorite.getStockCode()) + " · " + item.getMetric());
            event.setCategory(MonitorEvent.CATEGORY_STOCK);
            event.setDetail(detailOf(item));
            event.setAction(item.getAction());
            event.setImportance(StringUtils.hasText(item.getImportance())
                    ? item.getImportance() : MonitorEvent.IMPORTANCE_MEDIUM);
            event.setStatus(MonitorEvent.STATUS_PENDING);
            event.setCreatedAt(now);
            event.setUpdatedAt(now);
            toSave.add(event);
        }
        if (!toSave.isEmpty()) {
            monitorEventRepository.saveAll(toSave);
        }
    }

    private void deletePendingEvents(String userId, String symbol) {
        monitorEventRepository.deleteByUserIdAndSymbolAndCategoryAndStatus(
                userId, symbol, MonitorEvent.CATEGORY_STOCK, MonitorEvent.STATUS_PENDING);
    }

    private String detailOf(UserFavorites.MonitorItem item) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.getCurrentValue())) {
            parts.add("现值 " + item.getCurrentValue());
        }
        if (StringUtils.hasText(item.getThreshold())) {
            parts.add("警戒线 " + item.getThreshold());
        }
        return String.join(" / ", parts);
    }

    /* ---------------- 行情与派生标签 ---------------- */

    private FocusStockView toView(UserFavorites.FavoriteStock favorite) {
        UserFavorites.FocusProfile profile = favorite.getFocus();
        FocusStockView view = new FocusStockView();
        view.setProfile(profile);
        view.setSymbol(favorite.getStockCode());
        view.setStockName(favorite.getStockName());
        view.setMarket(favorite.getMarket());
        view.setAddedAt(favorite.getAddedAt());
        view.setTags(favorite.getTags());
        view.setNotes(favorite.getNotes());
        LocalDate today = LocalDate.now();
        LocalDate base = profile == null ? null : profile.getReportDate();
        if (base != null) {
            view.setStaleDays((int) ChronoUnit.DAYS.between(base, today));
        }
        List<StockDailyKline> klines = klineProvider.ascending(favorite.getStockCode(), 2);
        if (!klines.isEmpty()) {
            StockDailyKline last = klines.get(klines.size() - 1);
            BigDecimal close = last.getClose();
            view.setPrice(close);
            view.setPriceDate(last.getTradeDate());
            if (klines.size() > 1) {
                BigDecimal prev = klines.get(klines.size() - 2).getClose();
                if (prev != null && prev.compareTo(BigDecimal.ZERO) > 0) {
                    view.setChangePct(close.subtract(prev)
                            .multiply(new BigDecimal("100"))
                            .divide(prev, 2, RoundingMode.HALF_UP));
                }
            }
        }
        if (profile == null) {
            return view;
        }
        UserFavorites.BuyTier targetTier = targetTier(profile);
        BigDecimal tierHigh = targetTier == null ? null : targetTier.getPriceHigh();
        List<String> flags = new ArrayList<>();
        if (view.getPrice() != null && tierHigh != null && tierHigh.compareTo(BigDecimal.ZERO) > 0) {
            view.setDistanceToTier1Pct(tierHigh.subtract(view.getPrice())
                    .multiply(new BigDecimal("100"))
                    .divide(view.getPrice(), 2, RoundingMode.HALF_UP));
            if (isInBuyZone(profile, view.getPrice())) {
                flags.add(FocusStockView.FLAG_IN_BUY_ZONE);
            } else if (view.getPrice().compareTo(tierHigh) > 0
                    && view.getPrice().subtract(tierHigh)
                    .divide(tierHigh, 4, RoundingMode.HALF_UP).compareTo(NEAR_BUY_RATIO) <= 0) {
                flags.add(FocusStockView.FLAG_NEAR_BUY);
            }
        }
        BigDecimal maxBuyPrice = profile.getBuyPlan() == null ? null : profile.getBuyPlan().getMaxBuyPrice();
        if (view.getPrice() != null && maxBuyPrice != null && view.getPrice().compareTo(maxBuyPrice) > 0) {
            flags.add(FocusStockView.FLAG_ABOVE_MAX);
        }
        if (view.getStaleDays() != null && view.getStaleDays() > STALE_DAYS) {
            flags.add(FocusStockView.FLAG_STALE);
        }
        view.setDerivedFlags(flags);
        return view;
    }

    /**
     * 当前还没走完的最小一档；都走完了就退回第一档，页面上仍然显示原来那个买点
     */
    private UserFavorites.BuyTier targetTier(UserFavorites.FocusProfile profile) {
        if (profile.getBuyPlan() == null || profile.getBuyPlan().getTiers() == null) {
            return null;
        }
        List<UserFavorites.BuyTier> tiers = new ArrayList<>();
        for (UserFavorites.BuyTier tier : profile.getBuyPlan().getTiers()) {
            if (tier != null && tier.getPriceHigh() != null) {
                tiers.add(tier);
            }
        }
        if (tiers.isEmpty()) {
            return null;
        }
        tiers.sort(Comparator.comparing(tier -> tier.getTier() == null ? 0 : tier.getTier()));
        for (UserFavorites.BuyTier tier : tiers) {
            if (!UserFavorites.FocusProfile.TIER_DONE.equals(tier.getStatus())
                    && !UserFavorites.FocusProfile.TIER_CANCELLED.equals(tier.getStatus())) {
                return tier;
            }
        }
        return tiers.get(0);
    }

    private boolean isInBuyZone(UserFavorites.FocusProfile profile, BigDecimal price) {
        if (profile.getBuyPlan() == null || profile.getBuyPlan().getTiers() == null) {
            return false;
        }
        for (UserFavorites.BuyTier tier : profile.getBuyPlan().getTiers()) {
            if (tier == null || tier.getPriceLow() == null || tier.getPriceHigh() == null) {
                continue;
            }
            if (UserFavorites.FocusProfile.TIER_DONE.equals(tier.getStatus())
                    || UserFavorites.FocusProfile.TIER_CANCELLED.equals(tier.getStatus())) {
                continue;
            }
            if (price.compareTo(tier.getPriceLow()) >= 0 && price.compareTo(tier.getPriceHigh()) <= 0) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- 辅助 ---------------- */

    private UserFavorites loadOrCreatePool(String userId, LocalDateTime now) {
        Optional<UserFavorites> existing = userFavoritesRepository.findByUserId(userId);
        if (existing.isPresent()) {
            UserFavorites pool = existing.get();
            if (pool.getFavorites() == null) {
                pool.setFavorites(new ArrayList<>());
            }
            return pool;
        }
        UserFavorites pool = new UserFavorites();
        pool.setUserId(userId);
        pool.setFavorites(new ArrayList<>());
        pool.setCreatedAt(now);
        pool.setUpdatedAt(now);
        return pool;
    }

    private UserFavorites.FavoriteStock findFavorite(UserFavorites pool, String symbol) {
        if (pool == null || pool.getFavorites() == null || !StringUtils.hasText(symbol)) {
            return null;
        }
        for (UserFavorites.FavoriteStock item : pool.getFavorites()) {
            if (item != null && symbol.equals(item.getStockCode())) {
                return item;
            }
        }
        return null;
    }

    private void normalizeTiers(UserFavorites.FocusProfile profile) {
        if (profile.getBuyPlan() == null || profile.getBuyPlan().getTiers() == null) {
            return;
        }
        for (UserFavorites.BuyTier tier : profile.getBuyPlan().getTiers()) {
            if (tier == null) {
                continue;
            }
            if (!StringUtils.hasText(tier.getStatus())) {
                tier.setStatus(UserFavorites.FocusProfile.TIER_WAITING);
            }
            if (!StringUtils.hasText(tier.getLabel()) && tier.getTier() != null) {
                tier.setLabel("第 " + tier.getTier() + " 档");
            }
        }
    }

    private void collectChanges(UserFavorites.FocusProfile old, UserFavorites.FocusProfile request,
                                List<String> changes) {
        if (StringUtils.hasText(request.getReportId())
                && !request.getReportId().equals(old.getReportId())) {
            changes.add("来源报告已更新");
        }
        BigDecimal oldMax = old.getBuyPlan() == null ? null : old.getBuyPlan().getMaxBuyPrice();
        BigDecimal newMax = request.getBuyPlan() == null ? null : request.getBuyPlan().getMaxBuyPrice();
        if (oldMax != null && newMax != null && oldMax.compareTo(newMax) != 0) {
            changes.add("不追价 " + plain(oldMax) + " → " + plain(newMax));
        }
        List<UserFavorites.BuyTier> oldTiers = old.getBuyPlan() == null ? null : old.getBuyPlan().getTiers();
        List<UserFavorites.BuyTier> newTiers = request.getBuyPlan() == null ? null : request.getBuyPlan().getTiers();
        if (oldTiers != null && newTiers != null) {
            for (UserFavorites.BuyTier newTier : newTiers) {
                if (newTier == null || newTier.getTier() == null) {
                    continue;
                }
                for (UserFavorites.BuyTier oldTier : oldTiers) {
                    if (oldTier == null || !newTier.getTier().equals(oldTier.getTier())) {
                        continue;
                    }
                    String oldRange = range(oldTier);
                    String newRange = range(newTier);
                    if (oldRange != null && newRange != null && !oldRange.equals(newRange)) {
                        changes.add((StringUtils.hasText(newTier.getLabel())
                                ? newTier.getLabel() : "第 " + newTier.getTier() + " 档")
                                + " " + oldRange + " → " + newRange);
                    }
                }
            }
        }
    }

    private String range(UserFavorites.BuyTier tier) {
        if (tier.getPriceLow() == null || tier.getPriceHigh() == null) {
            return null;
        }
        return plain(tier.getPriceLow()) + "-" + plain(tier.getPriceHigh());
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void appendTimeline(UserFavorites.FocusProfile profile, String type, String text, LocalDateTime at) {
        if (profile.getTimeline() == null) {
            profile.setTimeline(new ArrayList<>());
        }
        UserFavorites.TimelineItem item = new UserFavorites.TimelineItem();
        item.setAt(at);
        item.setType(type);
        item.setText(text);
        profile.getTimeline().add(item);
        while (profile.getTimeline().size() > TIMELINE_MAX_SIZE) {
            profile.getTimeline().remove(0);
        }
    }

    private int tierCount(UserFavorites.FocusProfile profile) {
        return profile.getBuyPlan() == null || profile.getBuyPlan().getTiers() == null
                ? 0 : profile.getBuyPlan().getTiers().size();
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
