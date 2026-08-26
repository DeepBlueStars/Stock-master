import akshare as ak
import pandas as pd
from typing import Dict, Any, List
from datetime import datetime
import logging
import time

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class ETFService:
    def __init__(self):
        self.retry_count = 3
        self.retry_delay = 5  # 重试间隔（秒）
    
    def get_etf_spot_data(self) -> pd.DataFrame:
        """获取所有ETF实时数据"""
        # 尝试使用主数据源：fund_etf_spot_em
        for attempt in range(self.retry_count):
            try:
                logger.info(f"开始获取所有ETF实时数据（第{attempt+1}/{self.retry_count}次尝试）")
                # 使用akshare获取ETF实时数据
                df = ak.fund_etf_spot_em()
                logger.info(f"成功获取ETF实时数据，共{len(df)}条记录")
                return df
            except Exception as e:
                logger.error(f"第{attempt+1}次获取ETF实时数据失败: {e}")
                if attempt < self.retry_count - 1:
                    logger.info(f"将在{self.retry_delay}秒后重试...")
                    time.sleep(self.retry_delay)
                else:
                    logger.error(f"所有尝试都失败了，获取ETF实时数据失败: {e}", exc_info=True)
        
        # 主数据源失败，尝试使用备用数据源：fund_etf_category_sina
        logger.info("主数据源失败，尝试使用备用数据源获取ETF数据...")
        for attempt in range(self.retry_count):
            try:
                logger.info(f"开始使用备用数据源获取ETF数据（第{attempt+1}/{self.retry_count}次尝试）")
                # 使用新浪财经接口获取ETF列表
                df = ak.fund_etf_category_sina(symbol="ETF基金")
                logger.info(f"成功使用备用数据源获取ETF数据，共{len(df)}条记录")
                return df
            except Exception as e:
                logger.error(f"第{attempt+1}次使用备用数据源获取ETF数据失败: {e}")
                if attempt < self.retry_count - 1:
                    logger.info(f"将在{self.retry_delay}秒后重试...")
                    time.sleep(self.retry_delay)
                else:
                    logger.error(f"所有备用数据源尝试都失败了，获取ETF数据失败: {e}", exc_info=True)
                    return pd.DataFrame()
    
    def get_etf_info_by_symbol(self, symbol: str) -> Dict[str, Any]:
        """根据代码获取ETF实时详情"""
        try:
            logger.info(f"开始获取ETF[{symbol}]实时详情")
            df = self.get_etf_spot_data()
            if df.empty:
                logger.warning(f"获取ETF[{symbol}]实时详情失败: 基础数据为空")
                return {}
            
            # 筛选指定代码的ETF数据，处理带有交易所前缀的情况
            etf_data = df[df["代码"].str.contains(symbol)]
            if etf_data.empty:
                logger.warning(f"获取ETF[{symbol}]实时详情失败: 未找到该代码的ETF数据")
                return {}
            
            # 转换为字典格式
            etf_dict = etf_data.iloc[0].to_dict()
            
            result = {
                "symbol": symbol,
                "name": etf_dict["名称"]
            }
            
            logger.info(f"成功获取ETF[{symbol}]实时详情: {result}")
            return result
        except Exception as e:
            logger.error(f"根据代码获取ETF[{symbol}]实时详情失败: {e}", exc_info=True)
            return {}
    
    def transform_etf_data_for_db(self, symbol: str, etf_data: Dict[str, Any]) -> Dict[str, Any]:
        """转换ETF数据为数据库存储格式"""
        # logger.info(f"开始转换ETF[{symbol}]数据为数据库存储格式")
        current_time = datetime.now()
        today = current_time.strftime("%Y-%m-%d")
        
        # 处理股票代码格式，只保留6位数
        processed_symbol = symbol[-6:] if len(symbol) > 6 else symbol
        
        # 转换成交量和成交额（ak.fund_etf_spot_em返回的是数值类型）
        volume = float(etf_data.get("成交量", 0))
        amount = float(etf_data.get("成交额", 0))
        
        # 处理涨跌幅字段，支持float和string类型
        pct_change_value = etf_data.get("涨跌幅", 0)
        if isinstance(pct_change_value, str):
            pct_change_value = float(pct_change_value.replace("%", ""))
        
        # 构建数据库存储格式
        result = {
            "symbol": processed_symbol,
            "market": "CN",  # 固定值为CN
            "trade_date": today,
            "period": "daily",  # 固定值为daily
            "data_source": "akshare",  # 固定值为akshare
            "created_at": current_time,
            "updated_at": current_time,
            "open": float(etf_data.get("开盘价", 0)),
            "high": float(etf_data.get("最高价", 0)),
            "low": float(etf_data.get("最低价", 0)),
            "close": float(etf_data.get("最新价", 0)),
            "volume": volume,
            "amount": amount,
            "change": float(etf_data.get("涨跌额", 0)),
            "pct_change": float(pct_change_value)
        }
        
        # logger.info(f"成功转换ETF[{symbol}]数据为数据库存储格式")
        return result
    
    def get_all_etf_data_for_db(self) -> List[Dict[str, Any]]:
        """获取所有ETF数据并转换为数据库存储格式"""
        try:
            logger.info("开始获取所有ETF数据并转换为数据库存储格式")
            df = self.get_etf_spot_data()
            if df.empty:
                logger.warning("ETF实时数据为空，无法转换为数据库存储格式")
                return []
            
            result = []
            for _, row in df.iterrows():
                etf_dict = row.to_dict()
                symbol = etf_dict["代码"]
                transformed_data = self.transform_etf_data_for_db(symbol, etf_dict)
                result.append(transformed_data)
            
            logger.info(f"成功转换所有ETF数据为数据库存储格式，共{len(result)}条记录")
            return result
        except Exception as e:
            logger.error(f"获取所有ETF数据并转换失败: {e}", exc_info=True)
            return []
    
    def get_all_etf_basic_info(self) -> pd.DataFrame:
        """获取所有ETF基本信息"""
        for attempt in range(self.retry_count):
            try:
                logger.info(f"开始获取所有ETF基本信息（第{attempt+1}/{self.retry_count}次尝试）")
                # 使用akshare获取ETF基本信息
                df = ak.fund_etf_category_sina(symbol="ETF基金")
                logger.info(f"成功获取ETF基本信息，共{len(df)}条记录")
                return df
            except Exception as e:
                logger.error(f"第{attempt+1}次获取ETF基本信息失败: {e}")
                if attempt < self.retry_count - 1:
                    logger.info(f"将在{self.retry_delay}秒后重试...")
                    time.sleep(self.retry_delay)
                else:
                    logger.error(f"所有尝试都失败了，获取ETF基本信息失败: {e}", exc_info=True)
                    return pd.DataFrame()
    
    def transform_etf_basic_info(self, etf_dict: Dict[str, Any]) -> Dict[str, Any]:
        """转换ETF基本信息为数据库存储格式"""
        from datetime import datetime
        # logger.info(f"开始转换ETF[{etf_dict.get('代码', '未知')}]基本信息为数据库存储格式")
        current_time = datetime.now()
        
        # 获取原始代码
        original_symbol = etf_dict.get("代码", "")
        # 去掉前缀，仅保留数字部分
        symbol = original_symbol[-6:] if len(original_symbol) > 6 else original_symbol
        
        result = {
            "symbol": symbol,
            "full_symbol": original_symbol,
            "name": etf_dict.get("名称", ""),
            "market": "CN",  # 固定值为CN
            "source": "akshare",  # 固定值为akshare
            "updated_at": current_time
        }
        
        # logger.info(f"成功转换ETF基本信息为数据库存储格式")
        return result
    
    def get_all_etf_basic_info_for_db(self) -> List[Dict[str, Any]]:
        """获取所有ETF基本信息并转换为数据库存储格式"""
        try:
            logger.info("开始获取所有ETF基本信息并转换为数据库存储格式")
            df = self.get_all_etf_basic_info()
            if df.empty:
                logger.warning("ETF基本信息为空，无法转换为数据库存储格式")
                return []
            
            result = []
            for _, row in df.iterrows():
                etf_dict = row.to_dict()
                transformed_data = self.transform_etf_basic_info(etf_dict)
                result.append(transformed_data)
            
            logger.info(f"成功转换所有ETF基本信息为数据库存储格式，共{len(result)}条记录")
            return result
        except Exception as e:
            logger.error(f"获取所有ETF基本信息并转换失败: {e}", exc_info=True)
            return []

# 创建全局ETF服务实例
etf_service = ETFService()