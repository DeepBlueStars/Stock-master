"""
飞书通知服务
实现飞书机器人消息发送功能
"""
import json
import requests
import time
from datetime import datetime
from typing import Optional, Dict, List

from app.config import settings


class FeishuService:
    """飞书通知服务"""
    
    def __init__(self):
        # 飞书机器人webhook地址
        self.webhook_url = "https://open.feishu.cn/open-apis/bot/v2/hook/c386516d-c3f1-4d9a-bb76-59e51d09ffb8"
        self.timeout = 10  # 超时时间
        self.retry_count = 3  # 重试次数
        self.retry_interval = 2  # 重试间隔
        
        print("[FeishuService] 初始化完成")
    
    def send_message(self, msg_type: str, content: Dict) -> bool:
        """发送飞书消息
        
        Args:
            msg_type: 消息类型，如 "text"
            content: 消息内容
            
        Returns:
            bool: 是否发送成功
        """
        payload = {
            "msg_type": msg_type,
            "content": content
        }
        
        headers = {
            "Content-Type": "application/json"
        }
        
        for i in range(self.retry_count):
            try:
                response = requests.post(
                    self.webhook_url,
                    json=payload,
                    headers=headers,
                    timeout=self.timeout
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 0:
                        print(f"[FeishuService] 消息发送成功")
                        return True
                    else:
                        print(f"[FeishuService] 消息发送失败: {result.get('msg')}")
                else:
                    print(f"[FeishuService] HTTP请求失败: {response.status_code}")
                    
            except Exception as e:
                print(f"[FeishuService] 发送消息异常: {e}")
            
            if i < self.retry_count - 1:
                print(f"[FeishuService] 正在重试 ({i+1}/{self.retry_count})...")
                time.sleep(self.retry_interval)
        
        return False
    
    def send_buy_notification(self, stock: Dict, signal: str) -> bool:
        """发送买入机会通知
        
        Args:
            stock: 股票信息
            signal: 买入信号类型
            
        Returns:
            bool: 是否发送成功
        """
        # 消息类型映射
        signal_map = {
            'first_board': '首板机会',
            'second_board': '二板机会',
            'third_board': '三板机会',
            'dragon_second_wave': '龙头股二波机会'
        }
        
        signal_name = signal_map.get(signal, '买入机会')
        
        # 构建消息内容
        text = f"🚨 **{signal_name}**\n"
        text += f"📈 **股票**: {stock.get('name')} ({stock.get('code')})\n"
        text += f"💰 **当前价**: {stock.get('current_price', 0):.2f}元\n"
        text += f"📊 **涨跌幅**: +{stock.get('change_rate', 0):.2f}%\n"
        text += f"🏷️ **板类型**: {self._get_board_type_name(stock.get('board_type', 'first'))}\n"
        text += f"⭐ **AI评分**: {stock.get('ai_score', 0)}\n"
        text += f"🏭 **所属板块**: {stock.get('sector', '未知')}\n"
        text += f"💪 **板块强度**: {stock.get('sector_strength', 0):.1f}/10\n"
        text += f"📰 **涨停原因**: {stock.get('reason', '未知')}\n"
        text += f"⏰ **涨停时间**: {stock.get('limit_up_time', '未知')}\n"
        text += f"💥 **封单金额**: {stock.get('seal_amount', 0):,.0f}元\n"
        text += f"📊 **量比**: {stock.get('volume_ratio', 0):.2f}\n"
        text += f"🔄 **换手率**: {stock.get('turnover_rate', 0):.2f}%\n"
        text += f"💼 **流通市值**: {stock.get('market_cap', 0):,.0f}元\n"
        text += f"\n📋 **操作建议**:\n"
        
        # 根据信号类型给出具体建议
        if signal == 'first_board':
            text += "- 建议扫板或排板买入\n"
            text += "- 关注板块效应\n"
        elif signal == 'second_board':
            text += "- 确认龙头地位，可大胆参与\n"
            text += "- 注意封单强度\n"
        elif signal == 'third_board':
            text += "- 高风险高回报，需有板块支撑\n"
            text += "- 建议排板为主\n"
        elif signal == 'dragon_second_wave':
            text += "- 龙头股断板后回调，关注二波机会\n"
            text += "- 建议尾盘买入\n"
        
        text += f"\n⚠️ **风险提示**: 打板交易风险极高，请严格控制仓位！\n"
        text += f"🕒 **通知时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        
        content = {
            "text": text
        }
        
        return self.send_message("text", content)
    
    def send_sell_notification(self, stock: Dict, signal: str) -> bool:
        """发送卖出提醒通知
        
        Args:
            stock: 股票信息
            signal: 卖出信号类型
            
        Returns:
            bool: 是否发送成功
        """
        # 消息类型映射
        signal_map = {
            'morning_open': '开盘提醒',
            'mid_morning_small': '午盘前提醒（小市值）',
            'mid_morning_large': '午盘前提醒（大市值）',
            'afternoon': '午后提醒',
            'closing': '尾盘提醒',
            'board_broken': '炸板提醒'
        }
        
        signal_name = signal_map.get(signal, '卖出提醒')
        
        # 构建消息内容
        text = f"📢 **{signal_name}**\n"
        text += f"📉 **股票**: {stock.get('name')} ({stock.get('code')})\n"
        text += f"💰 **当前价**: {stock.get('current_price', 0):.2f}元\n"
        text += f"📊 **涨跌幅**: {stock.get('change_rate', 0):.2f}%\n"
        text += f"🏭 **所属板块**: {stock.get('sector', '未知')}\n"
        text += f"\n📋 **操作建议**:\n"
        
        # 根据信号类型给出具体建议
        if signal == 'morning_open':
            text += "- 建议卖出50%仓位\n"
            text += "- 锁定部分利润\n"
        elif signal == 'mid_morning_small':
            text += "- 小市值股票建议清仓\n"
            text += "- 避免午后波动\n"
        elif signal == 'mid_morning_large':
            text += "- 大市值股票建议减仓\n"
            text += "- 保留底仓观察\n"
        elif signal == 'afternoon':
            text += "- 未封板股票建议清仓\n"
            text += "- 避免尾盘跳水\n"
        elif signal == 'closing':
            text += "- 非涨停股票建议强制清仓\n"
            text += "- 控制隔夜风险\n"
        elif signal == 'board_broken':
            text += "- 股票炸板，建议立即卖出\n"
            text += "- 避免次日低开\n"
        
        text += f"\n⚠️ **风险提示**: 严格执行卖出纪律，不要犹豫！\n"
        text += f"🕒 **通知时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        
        content = {
            "text": text
        }
        
        return self.send_message("text", content)
    
    def send_stop_loss_notification(self, position: Dict) -> bool:
        """发送止损预警通知
        
        Args:
            position: 持仓信息
            
        Returns:
            bool: 是否发送成功
        """
        # 计算亏损比例
        current_price = position.get('current_price', 0)
        avg_cost = position.get('avg_cost', 1)
        loss_rate = (current_price - avg_cost) / avg_cost * 100 if avg_cost > 0 else 0
        
        # 构建消息内容
        text = f"🚨 **止损预警**\n"
        text += f"📉 **股票**: {position.get('stock_name', '未知')} ({position.get('stock_code', '未知')})\n"
        text += f"💰 **当前价**: {current_price:.2f}元\n"
        text += f"💲 **成本价**: {avg_cost:.2f}元\n"
        text += f"📊 **亏损比例**: {loss_rate:.2f}%\n"
        text += f"📋 **操作建议**:\n"
        text += "- 立即卖出，执行止损纪律\n"
        text += "- 不要幻想反弹\n"
        text += "- 控制风险，保存实力\n"
        text += f"\n⚠️ **紧急提示**: 已触及-6%止损线，请立即执行！\n"
        text += f"🕒 **通知时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        
        content = {
            "text": text
        }
        
        return self.send_message("text", content)
    
    def send_market_sentiment_notification(self, sentiment: Dict) -> bool:
        """发送市场情绪通知
        
        Args:
            sentiment: 市场情绪信息
            
        Returns:
            bool: 是否发送成功
        """
        # 情绪等级映射
        temp_map = {
            'cold': '冷淡',
            'neutral': '中性',
            'warm': '温和',
            'hot': '火爆'
        }
        
        cycle_map = {
            'cold': '冰点期',
            'recovery': '复苏期',
            'high_tide': '高潮期'
        }
        
        # 构建消息内容
        text = f"📊 **市场情绪分析**\n"
        text += f"📅 **日期**: {sentiment.get('date', '')}\n"
        text += f"🌡️ **市场温度**: {temp_map.get(sentiment.get('market_temperature'), '未知')}\n"
        text += f"🔄 **情绪周期**: {cycle_map.get(sentiment.get('cycle_phase'), '未知')}\n"
        text += f"⭐ **情绪评分**: {sentiment.get('sentiment_score', 0):.1f}/10\n"
        text += f"📈 **上涨家数**: {sentiment.get('up_count', 0)}\n"
        text += f"📉 **下跌家数**: {sentiment.get('down_count', 0)}\n"
        text += f"🚀 **涨停家数**: {sentiment.get('limit_up_count', 0)}\n"
        text += f"💥 **跌停家数**: {sentiment.get('limit_down_count', 0)}\n"
        text += f"🏆 **最高连板**: {sentiment.get('max_board_height', 0)}板\n"
        text += f"\n📋 **操作建议**:\n"
        
        # 根据情绪给出建议
        score = sentiment.get('sentiment_score', 5)
        if score >= 7:
            text += "- 情绪高涨，可积极参与打板\n"
            text += "- 关注龙头股机会\n"
        elif score >= 5:
            text += "- 情绪中性，可适度参与\n"
            text += "- 控制仓位，精选个股\n"
        else:
            text += "- 情绪低迷，建议谨慎操作\n"
            text += "- 轻仓试错或空仓观望\n"
        
        text += f"\n🕒 **通知时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        
        content = {
            "text": text
        }
        
        return self.send_message("text", content)
    
    def _get_board_type_name(self, board_type: str) -> str:
        """获取板类型名称
        
        Args:
            board_type: 板类型代码
            
        Returns:
            str: 板类型名称
        """
        board_map = {
            'first': '首板',
            'second': '二板',
            'third': '三板',
            'high': '高位板'
        }
        return board_map.get(board_type, '未知')


# 全局飞书服务实例
feishu_service = FeishuService()
