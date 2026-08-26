"""
测试飞书通知功能
"""
from app.services.feishu_service import feishu_service
from app.services.notification_cache import notification_cache


def test_feishu_notification():
    """测试飞书通知"""
    print("开始测试飞书通知功能...")
    
    # 测试市场情绪通知
    sentiment_data = {
        "date": "2026-02-08",
        "market_temperature": "hot",
        "cycle_phase": "high_tide",
        "sentiment_score": 8.5,
        "up_count": 2800,
        "down_count": 1200,
        "limit_up_count": 85,
        "limit_down_count": 5,
        "max_board_height": 6
    }
    
    print("测试市场情绪通知...")
    success = feishu_service.send_market_sentiment_notification(sentiment_data)
    print(f"市场情绪通知发送{'成功' if success else '失败'}")
    
    # 测试买入机会通知
    stock_data = {
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
    }
    
    print("测试买入机会通知...")
    # 清除缓存，确保可以发送
    notification_cache.clear_all_cache()
    
    # 检查是否已通知（应该未通知）
    has_notified = notification_cache.has_notified_today("000063", "buy")
    print(f"发送前 - 今天是否已通知: {'是' if has_notified else '否'}")
    
    # 发送通知并标记为已通知
    success = feishu_service.send_buy_notification(stock_data, "second_board")
    notification_cache.mark_as_notified("000063", "buy", stock_data)
    print(f"买入机会通知发送{'成功' if success else '失败'}")
    
    # 测试缓存机制
    print("测试缓存机制...")
    has_notified = notification_cache.has_notified_today("000063", "buy")
    print(f"发送后 - 今天是否已通知: {'是' if has_notified else '否'}")
    
    # 测试重复通知（应该被缓存阻止）
    print("测试重复通知...")
    if not has_notified:
        success = feishu_service.send_buy_notification(stock_data, "second_board")
        print(f"重复通知发送{'成功' if success else '失败'}")
    else:
        print("重复通知被缓存阻止，未发送")
    
    print("飞书通知功能测试完成！")


if __name__ == "__main__":
    test_feishu_notification()
