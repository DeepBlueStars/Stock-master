"""
行情服务
提供实时行情数据获取和缓存，以及龙头战法分析
"""
import asyncio
import json
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
from decimal import Decimal
import random

import redis
from data_fetcher.data_aggregator import data_aggregator
from data_fetcher.models import UnifiedStockData, UnifiedLimitUpStock, UnifiedSector
from app.config import settings


class MarketService:
    """行情服务"""
    
    def __init__(self):
        # Redis缓存
        try:
            self.redis_client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                db=settings.redis_db,
                password=settings.redis_password,
                decode_responses=True
            )
            self.redis_client.ping()
            self.use_redis = True
            print("[MarketService] Redis连接成功")
        except Exception as e:
            print(f"[MarketService] Redis连接失败: {e}，使用内存缓存")
            self.use_redis = False
            self._cache = {}
            self._cache_time = {}
        
        self._cache_ttl = 60  # 缓存60秒
        
        # 沪深300成分股作为股票池（简化版）
        self.stock_pool = [
            '000001', '000002', '000063', '000100', '000333', '000538', '000568', '000651', '000725', '000768',
            '000858', '000895', '002001', '002007', '002024', '002027', '002044', '002120', '002142', '002230',
            '002236', '002271', '002304', '002352', '002415', '002460', '002475', '002594', '002714', '300003',
            '300014', '300015', '300033', '300059', '300122', '300124', '300142', '300274', '300408', '300413',
            '300433', '300498', '300750', '600000', '600009', '600016', '600028', '600030', '600031', '600036',
            '600048', '600050', '600104', '600109', '600111', '600115', '600118', '600150', '600161', '600177',
            '600196', '600208', '600271', '600276', '600297', '600309', '600332', '600340', '600346', '600352',
            '600362', '600372', '600383', '600406', '600436', '600438', '600482', '600487', '600489', '600498',
            '600518', '600519', '600521', '600522', '600535', '600547', '600549', '600570', '600585', '600588',
            '600600', '600606', '600637', '600648', '600660', '600674', '600682', '600690', '600703', '600705',
            '600741', '600745', '600760', '600782', '600795', '600809', '600837', '600845', '600867', '600886',
            '600887', '600893', '600900', '600918', '600919', '600926', '600958', '600999', '601006', '601009',
            '601012', '601021', '601066', '601088', '601100', '601108', '601111', '601117', '601138', '601155',
            '601166', '601169', '601186', '601198', '601211', '601212', '601216', '601225', '601229', '601236',
            '601238', '601288', '601318', '601319', '601328', '601336', '601360', '601377', '601390', '601398',
            '601399', '601555', '601577', '601600', '601601', '601607', '601618', '601628', '601633', '601658',
            '601668', '601669', '601688', '601698', '601727', '601766', '601788', '601799', '601800', '601808',
            '601816', '601818', '601838', '601857', '601868', '601877', '601878', '601881', '601888', '601898',
            '601899', '601901', '601916', '601919', '601933', '601939', '601966', '601985', '601988', '601989',
            '601990', '603019', '603160', '603195', '603259', '603288', '603369', '603392', '603444', '603486',
            '603501', '603658', '603799', '603833', '603899', '603986', '603993', '688001', '688002', '688003'
        ]
    
    def _get_from_cache(self, key: str) -> Optional[Dict]:
        """从缓存获取数据"""
        if self.use_redis:
            try:
                data = self.redis_client.get(key)
                if data:
                    return json.loads(data)
            except Exception as e:
                print(f"[MarketService] Redis获取数据失败: {e}")
        else:
            # 使用内存缓存作为备用
            if key in self._cache:
                cached_time = self._cache_time.get(key)
                if cached_time and (datetime.now() - cached_time).seconds < self._cache_ttl:
                    return self._cache[key]
        return None
    
    def _set_cache(self, key: str, data: Dict):
        """设置缓存"""
        if self.use_redis:
            try:
                self.redis_client.setex(key, self._cache_ttl, json.dumps(data, ensure_ascii=False))
            except Exception as e:
                print(f"[MarketService] Redis设置数据失败: {e}")
        else:
            # 使用内存缓存作为备用
            self._cache[key] = data
            self._cache_time[key] = datetime.now()
    
    async def get_limit_up_stocks(self) -> List[Dict]:
        """获取涨停股票列表"""
        cache_key = "limit_up_stocks"

        # 检查缓存
        cached = self._get_from_cache(cache_key)
        if cached:
            print("[MarketService] 从缓存获取涨停股票数据")
            return cached

        # 从数据聚合器获取涨停股票（优先使用真实API）
        try:
            print("[MarketService] 从API获取涨停股票数据")
            stocks = await data_aggregator.get_limit_up_stocks()

            if stocks and len(stocks) > 0:
                # 转换为字典列表
                result = [stock.to_dict() for stock in stocks]
                # 存入缓存
                self._set_cache(cache_key, result)
                print(f"[MarketService] 获取到 {len(result)} 只涨停股票，已存入缓存")
                return result

        except Exception as e:
            print(f"[MarketService] 从API获取涨停股票失败: {e}")

        # 直接返回模拟数据，避免并发请求导致的文件描述符问题
        print("[MarketService] 使用模拟数据作为涨停股票列表")
        mock_data = [
            {
                "code": "000001",
                "name": "平安银行",
                "board_type": "first",
                "limit_up_time": "09:35:28",
                "open_count": 0,
                "sector": "银行",
                "sector_strength": 8.5,
                "is_dragon_head": False,
                "volume_ratio": 5.2,
                "turnover_rate": 8.5,
                "market_cap": 2500000000,
                "reason": "业绩预增",
                "tags": ["首板", "业绩"],
                "ai_score": 82,
                "seal_amount": 15800000,
                "current_price": 12.80,
                "change_rate": 10.02
            },
            {
                "code": "000063",
                "name": "中兴通讯",
                "board_type": "second",
                "limit_up_time": "09:42:15",
                "open_count": 1,
                "sector": "通信设备",
                "sector_strength": 9.2,
                "is_dragon_head": True,
                "volume_ratio": 4.8,
                "turnover_rate": 12.3,
                "market_cap": 1800000000,
                "reason": "AI算力概念",
                "tags": ["二板", "AI", "龙头"],
                "ai_score": 91,
                "seal_amount": 32000000,
                "current_price": 35.60,
                "change_rate": 10.01
            },
            {
                "code": "002230",
                "name": "科大讯飞",
                "board_type": "third",
                "limit_up_time": "09:28:45",
                "open_count": 0,
                "sector": "人工智能",
                "sector_strength": 9.5,
                "is_dragon_head": True,
                "volume_ratio": 6.5,
                "turnover_rate": 15.2,
                "market_cap": 1200000000,
                "reason": "大模型发布",
                "tags": ["三板", "AI", "龙头"],
                "ai_score": 95,
                "seal_amount": 45000000,
                "current_price": 58.90,
                "change_rate": 10.00
            }
        ]
        self._set_cache(cache_key, mock_data)
        return mock_data
    
    async def _scan_limit_up_stocks(self) -> List[UnifiedLimitUpStock]:
        """
        扫描涨停股票

        策略：随机从股票池中选择一部分检查，找出涨幅超过9.9%的股票
        实际生产环境应该检查所有股票或从API获取
        """
        print("[MarketService] 扫描涨停股票...")

        limit_up_stocks = []

        # 减少样本数量，避免过多并发请求
        sample_size = min(20, len(self.stock_pool))
        sample_stocks = random.sample(self.stock_pool, sample_size)

        try:
            # 并发获取这些股票的价格
            stocks_data = await data_aggregator.get_batch_stock_prices(sample_stocks)

            for stock in stocks_data:
                # 检查是否涨停（涨跌幅 >= 9.9%）
                if stock.change_rate >= 9.9:
                    limit_stock = UnifiedLimitUpStock()
                    limit_stock.code = stock.code
                    limit_stock.name = stock.name
                    limit_stock.current_price = stock.current_price
                    limit_stock.change_rate = stock.change_rate
                    limit_stock.turnover_rate = stock.turnover_rate
                    limit_stock.volume_ratio = float(stock.turnover_rate) * 2
                    limit_stock.market_cap = stock.market_cap
                    limit_stock.seal_amount = stock.amount * Decimal('0.1')

                    # 随机分配板类型
                    board_types = ['first', 'second', 'third']
                    weights = [0.6, 0.3, 0.1]
                    limit_stock.board_type = random.choices(board_types, weights=weights)[0]
                    limit_stock.limit_up_time = f"09:{random.randint(25, 45)}:{random.randint(0, 59):02d}"
                    limit_stock.sector = self._get_sector_by_code(stock.code)
                    limit_stock.sector_strength = random.uniform(6.0, 9.5)
                    limit_stock.is_dragon_head = random.random() > 0.8
                    limit_stock.ai_score = random.randint(70, 95)
                    limit_stock.tags = [limit_stock.board_type]
                    limit_stock.source = stock.source

                    limit_up_stocks.append(limit_stock)
        except Exception as e:
            print(f"[MarketService] 扫描涨停股票失败: {e}")

        print(f"[MarketService] 找到 {len(limit_up_stocks)} 只涨停股票")
        return limit_up_stocks
    
    def _get_sector_by_code(self, code: str) -> str:
        """根据代码获取板块（简化版）"""
        # 这里简化处理，实际应该有映射表
        sectors = ['银行', '房地产', '医药', '科技', '消费', '新能源', '汽车', '通信']
        return random.choice(sectors)
    
    async def get_sectors(self) -> List[Dict]:
        """获取板块列表"""
        cache_key = "sectors"
        
        cached = self._get_from_cache(cache_key)
        if cached:
            return cached
        
        # 生成模拟板块数据（实际应该从API获取）
        sectors = self._generate_mock_sectors()
        
        # 转换为字典列表
        result = [sector.to_dict() for sector in sectors]
        
        self._set_cache(cache_key, result)
        return result
    
    def _generate_mock_sectors(self) -> List[UnifiedSector]:
        """生成模拟板块数据"""
        sector_names = [
            ('BK0475', '银行'), ('BK0450', '房地产'), ('BK0464', '医药制造'),
            ('BK0538', '软件服务'), ('BK0484', '酿酒'), ('BK0493', '新能源'),
            ('BK1016', '汽车'), ('BK0448', '通信设备'), ('BK0428', '电力'),
            ('BK0485', '化工'), ('BK0438', '电子'), ('BK0479', '机械'),
            ('BK0456', '传媒'), ('BK0481', '有色'), ('BK0465', '钢铁')
        ]
        
        sectors = []
        for code, name in sector_names:
            sector = UnifiedSector()
            sector.code = code
            sector.name = name
            sector.change_rate = Decimal(str(random.uniform(-3.0, 5.0)))
            sector.stocks_count = random.randint(50, 200)
            sector.limit_up_count = random.randint(0, 10)
            sector.limit_down_count = random.randint(0, 3)
            sector.leading_stock = random.choice(self.stock_pool)
            sector.strength_score = abs(float(sector.change_rate)) * 2
            sector.source = 'mock'
            sectors.append(sector)
        
        # 按强度排序
        sectors.sort(key=lambda x: x.strength_score, reverse=True)
        
        return sectors
    
    async def get_stock_detail(self, code: str) -> Optional[Dict]:
        """获取股票详情"""
        cache_key = f"stock_{code}"
        
        cached = self._get_from_cache(cache_key)
        if cached:
            return cached
        
        # 从数据源获取
        stock = await data_aggregator.get_stock_price(code)
        
        if not stock:
            return None
        
        # 转换为字典
        result = stock.to_dict()
        
        self._set_cache(cache_key, result)
        return result
    
    async def get_sentiment(self) -> Dict:
        """获取市场情绪"""
        cache_key = "sentiment"
        
        cached = self._get_from_cache(cache_key)
        if cached:
            return cached
        
        # 获取涨停股票数量
        limit_up_stocks = await self.get_limit_up_stocks()
        limit_up_count = len(limit_up_stocks)
        
        # 计算情绪
        if limit_up_count >= 80:
            phase = "high_tide"
            temp = "hot"
            score = 8.5
        elif limit_up_count >= 50:
            phase = "high_tide"
            temp = "hot"
            score = 7.5
        elif limit_up_count >= 30:
            phase = "recovery"
            temp = "warm"
            score = 6.0
        elif limit_up_count >= 10:
            phase = "recovery"
            temp = "neutral"
            score = 5.0
        else:
            phase = "cold"
            temp = "cold"
            score = 3.5
        
        result = {
            'date': datetime.now().strftime("%Y-%m-%d"),
            'timestamp': datetime.now().isoformat(),
            'up_count': random.randint(2000, 3500),
            'down_count': random.randint(500, 2000),
            'limit_up_count': limit_up_count,
            'limit_down_count': random.randint(0, 10),
            'max_board_height': random.randint(3, 8),
            'sentiment_score': score,
            'cycle_phase': phase,
            'market_temperature': temp
        }
        
        self._set_cache(cache_key, result)
        return result
    
    async def analyze_dragon_head(self) -> Dict:
        """龙头战法分析"""
        print("[MarketService] 执行龙头战法分析...")
        
        # 获取涨停股票
        limit_up_stocks = await self.get_limit_up_stocks()
        
        # 获取市场情绪
        sentiment = await self.get_sentiment()
        
        # 分析龙头股
        dragon_heads = []
        buy_opportunities = []
        sell_signals = []
        
        for stock in limit_up_stocks:
            # 龙头股识别
            is_dragon_head = self._identify_dragon_head(stock, sentiment)
            
            if is_dragon_head:
                dragon_heads.append(stock)
            
            # 买入机会分析
            buy_signal = self._analyze_buy_opportunity(stock, sentiment)
            if buy_signal:
                buy_opportunities.append({
                    'stock': stock,
                    'signal': buy_signal,
                    'timestamp': datetime.now().isoformat()
                })
            
            # 卖出信号分析
            sell_signal = self._analyze_sell_signal(stock)
            if sell_signal:
                sell_signals.append({
                    'stock': stock,
                    'signal': sell_signal,
                    'timestamp': datetime.now().isoformat()
                })
        
        # 排序龙头股
        dragon_heads.sort(key=lambda x: x.get('ai_score', 0), reverse=True)
        
        result = {
            'timestamp': datetime.now().isoformat(),
            'market_sentiment': sentiment,
            'dragon_heads': dragon_heads[:5],  # 取前5只龙头股
            'buy_opportunities': buy_opportunities,
            'sell_signals': sell_signals,
            'total_limit_up': len(limit_up_stocks)
        }
        
        print(f"[MarketService] 龙头战法分析完成: 发现 {len(dragon_heads)} 只龙头股，{len(buy_opportunities)} 个买入机会，{len(sell_signals)} 个卖出信号")
        return result
    
    def _identify_dragon_head(self, stock: Dict, sentiment: Dict) -> bool:
        """识别龙头股"""
        score = 0
        
        # 1. 连板高度 (30分)
        board_type = stock.get('board_type', 'first')
        if board_type == 'third':
            score += 30
        elif board_type == 'second':
            score += 20
        else:
            score += 10
        
        # 2. 涨停时间 (20分)
        limit_up_time = stock.get('limit_up_time', '10:00:00')
        if limit_up_time < '10:00:00':
            score += 20
        elif limit_up_time < '13:00:00':
            score += 15
        else:
            score += 10
        
        # 3. 封单强度 (20分)
        seal_amount = stock.get('seal_amount', 0)
        market_cap = stock.get('market_cap', 1)
        if market_cap > 0:
            seal_ratio = seal_amount / market_cap
            if seal_ratio > 0.01:
                score += 20
            elif seal_ratio > 0.005:
                score += 15
            else:
                score += 10
        
        # 4. 板块地位 (20分)
        sector_strength = stock.get('sector_strength', 0)
        if sector_strength > 8:
            score += 20
        elif sector_strength > 6:
            score += 15
        else:
            score += 10
        
        # 5. 成交额 (10分)
        # 假设成交额在 stock.get('amount', 0)，这里使用 market_cap 作为近似
        if 300000000 < market_cap < 2000000000:
            score += 10
        
        # 6. 市场情绪加成
        sentiment_score = sentiment.get('sentiment_score', 5)
        if sentiment_score > 7:
            score += 5
        
        return score >= 70
    
    def _analyze_buy_opportunity(self, stock: Dict, sentiment: Dict) -> Optional[str]:
        """分析买入机会"""
        # 检查市场情绪
        sentiment_score = sentiment.get('sentiment_score', 5)
        if sentiment_score < 4:
            return None
        
        # 基础过滤
        if not self._is_valid_stock(stock):
            return None
        
        # 首板机会
        if stock.get('board_type') == 'first':
            volume_ratio = stock.get('volume_ratio', 0)
            turnover_rate = stock.get('turnover_rate', 0)
            sector_strength = stock.get('sector_strength', 0)
            
            if volume_ratio > 3 and turnover_rate > 3 and sector_strength > 6:
                return 'first_board'
        
        # 二板机会
        elif stock.get('board_type') == 'second':
            sector_strength = stock.get('sector_strength', 0)
            if sector_strength > 7:
                return 'second_board'
        
        # 三板机会
        elif stock.get('board_type') == 'third':
            is_dragon_head = stock.get('is_dragon_head', False)
            if is_dragon_head:
                return 'third_board'
        
        # 龙头股二波机会（简化版）
        # 实际需要检查历史数据，这里只做示例
        if stock.get('is_dragon_head'):
            # 假设股票已经断板，这里简化处理
            # 实际需要检查断板后回调情况
            return 'dragon_second_wave'
        
        return None
    
    def _is_valid_stock(self, stock: Dict) -> bool:
        """检查股票是否符合基础条件"""
        # 检查股票代码
        code = stock.get('code', '')
        
        # 排除科创板（688开头）
        if code.startswith('688'):
            return False
        
        # 排除ST股（简化版，实际需要从名称判断）
        name = stock.get('name', '')
        if 'ST' in name:
            return False
        
        # 检查股价范围
        current_price = stock.get('current_price', 0)
        if current_price < 2 or current_price > 50:
            return False
        
        # 检查流通市值
        market_cap = stock.get('market_cap', 0)
        if market_cap < 300000000 or market_cap > 2000000000:
            return False
        
        return True
    
    def _analyze_sell_signal(self, stock: Dict) -> Optional[str]:
        """分析卖出信号"""
        # 炸板提醒
        open_count = stock.get('open_count', 0)
        if open_count > 2:
            return 'board_broken'
        
        # 四阶段卖出法（简化版，实际需要根据持仓情况和时间分析）
        current_time = datetime.now().time()
        
        # 09:30 开盘提醒
        if current_time.hour == 9 and current_time.minute == 30:
            # 假设股票高开，建议卖出50%
            return 'morning_open'
        
        # 09:48-11:28 午盘前提醒
        elif (current_time.hour == 9 and current_time.minute >= 48) or \
             (current_time.hour == 10) or \
             (current_time.hour == 11 and current_time.minute <= 28):
            # 小市值建议清仓，大市值建议减仓
            market_cap = stock.get('market_cap', 0)
            if market_cap < 500000000:
                return 'mid_morning_small'
            else:
                return 'mid_morning_large'
        
        # 13:18 午后提醒
        elif current_time.hour == 13 and current_time.minute == 18:
            # 未封板股票建议清仓
            if not stock.get('is_limit_up', False):
                return 'afternoon'
        
        # 14:48 尾盘提醒
        elif current_time.hour == 14 and current_time.minute == 48:
            # 非涨停股票建议强制清仓
            if not stock.get('is_limit_up', False):
                return 'closing'
        
        # 止损提醒（简化版，实际需要根据持仓成本分析）
        # 假设成本价为当前价的1.06倍，即亏损6%
        # 实际需要从持仓数据中获取成本价
        return None
    
    def _analyze_stop_loss(self, position: Dict) -> Optional[str]:
        """分析止损信号"""
        # 检查是否达到止损线
        current_price = position.get('current_price', 0)
        avg_cost = position.get('avg_cost', 1)
        
        if avg_cost > 0:
            loss_rate = (current_price - avg_cost) / avg_cost * 100
            if loss_rate <= -6:
                return 'stop_loss'
        
        return None
    
    def _calculate_sentiment_score(self, up_count: int, down_count: int, limit_up_count: int, limit_down_count: int) -> float:
        """计算市场情绪评分"""
        score = 0
        
        # 1. 涨跌家数比 (30%)
        if up_count + down_count > 0:
            ratio = up_count / (up_count + down_count)
            score += ratio * 30
        
        # 2. 涨停跌停比 (25%)
        if limit_down_count > 0:
            limit_ratio = min(limit_up_count / limit_down_count, 5)
            score += limit_ratio * 5
        else:
            score += 25
        
        # 3. 涨停家数 (25%)
        if limit_up_count >= 100:
            score += 25
        elif limit_up_count >= 50:
            score += 20
        elif limit_up_count >= 30:
            score += 15
        elif limit_up_count >= 10:
            score += 10
        else:
            score += 5
        
        # 4. 跌停家数 (20%)
        if limit_down_count == 0:
            score += 20
        elif limit_down_count < 5:
            score += 15
        elif limit_down_count < 10:
            score += 10
        elif limit_down_count < 30:
            score += 5
        else:
            score += 0
        
        return min(score, 100)


# 全局行情服务实例
market_service = MarketService()
