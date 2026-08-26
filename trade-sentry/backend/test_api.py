"""
测试数据获取模块
验证所有API能正常工作
"""
import asyncio
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from data_fetcher import (
    SinaAPI, EastMoneyAPI, TencentAPI, NetEaseAPI,
    data_aggregator
)


async def test_sina_api():
    """测试新浪财经API"""
    print("\n" + "="*50)
    print("Testing Sina API")
    print("="*50)

    async with SinaAPI() as api:
        result = await api.get_stock_price("000001")
        if result:
            print(f"[PASS] Sina API OK: {result.name} ({result.code})")
            print(f"       Price: {result.current_price}, Change: {result.change_rate}%")
            return True
        else:
            print("[FAIL] Sina API failed")
            return False


async def test_eastmoney_api():
    """测试东方财富API"""
    print("\n" + "="*50)
    print("Testing EastMoney API")
    print("="*50)

    async with EastMoneyAPI() as api:
        result = await api.get_stock_price("000001")
        if result:
            print(f"[PASS] EastMoney API OK: {result.name} ({result.code})")
            print(f"       Price: {result.current_price}, Change: {result.change_rate}%")
        else:
            print("[FAIL] EastMoney API failed")

        print("\n   Testing limit up stocks...")
        limit_up = await api.get_limit_up_stocks()
        if limit_up:
            print(f"   Found {len(limit_up)} limit up stocks")
            for stock in limit_up[:3]:
                print(f"   - {stock.name}: {stock.change_rate}%")
        else:
            print("   No limit up stocks data")

        print("\n   Testing sectors...")
        sectors = await api.get_sectors()
        print(f"   Found {len(sectors)} sectors")
        if sectors:
            for sector in sectors[:3]:
                print(f"   - {sector.name}: {sector.change_rate}%")

        return len(limit_up) > 0 or len(sectors) > 0


async def test_tencent_api():
    """测试腾讯财经API"""
    print("\n" + "="*50)
    print("Testing Tencent API")
    print("="*50)

    async with TencentAPI() as api:
        result = await api.get_stock_price("000001")
        if result:
            print(f"[PASS] Tencent API OK: {result.name} ({result.code})")
            print(f"       Price: {result.current_price}, Change: {result.change_rate}%")
            return True
        else:
            print("[FAIL] Tencent API failed")
            return False


async def test_netease_api():
    """测试网易财经API"""
    print("\n" + "="*50)
    print("Testing NetEase API")
    print("="*50)

    async with NetEaseAPI() as api:
        result = await api.get_stock_price("000001")
        if result:
            print(f"[PASS] NetEase API OK: {result.name} ({result.code})")
            print(f"       Price: {result.current_price}, Change: {result.change_rate}%")
            return True
        else:
            print("[FAIL] NetEase API failed")
            return False


async def test_aggregator():
    """测试数据聚合器"""
    print("\n" + "="*50)
    print("Testing Data Aggregator")
    print("="*50)

    # 初始化所有API会话
    await data_aggregator.sina._init_session()
    await data_aggregator.eastmoney._init_session()
    await data_aggregator.tencent._init_session()
    await data_aggregator.netease._init_session()

    try:
        print("\n   Testing single stock query...")
        result = await data_aggregator.get_stock_price("000001")
        if result:
            print(f"[PASS] Aggregator OK: {result.name} ({result.code})")
            print(f"       Price: {result.current_price}")
            print(f"       Source: {result.source}")
        else:
            print("[FAIL] Aggregator failed")

        print("\n   Testing limit up stocks...")
        limit_up = await data_aggregator.get_limit_up_stocks()
        print(f"   Found {len(limit_up)} limit up stocks")
        if limit_up:
            for stock in limit_up[:3]:
                print(f"   - {stock.name}: {stock.change_rate}%")

        print("\n   Testing market overview...")
        overview = await data_aggregator.get_market_overview()
        print(f"   Limit up count: {overview['market_summary']['limit_up_count']}")
        print(f"   Market temp: {overview['market_summary']['market_temperature']}")

        print("\n   API Health Status:")
        for api_name, health in data_aggregator.api_health.items():
            print(f"   - {api_name}: {health:.2f}")

        return True
    finally:
        # 关闭所有API会话
        await data_aggregator.sina._close_session()
        await data_aggregator.eastmoney._close_session()
        await data_aggregator.tencent._close_session()
        await data_aggregator.netease._close_session()


async def main():
    """主测试函数"""
    print("\n" + "="*50)
    print("Starting API Tests")
    print("="*50)

    results = []

    # 测试各API
    results.append(("Sina", await test_sina_api()))
    results.append(("EastMoney", await test_eastmoney_api()))
    results.append(("Tencent", await test_tencent_api()))
    results.append(("NetEase", await test_netease_api()))
    results.append(("Aggregator", await test_aggregator()))

    # 打印测试结果汇总
    print("\n" + "="*50)
    print("Test Results Summary")
    print("="*50)
    for name, success in results:
        status = "PASS" if success else "FAIL"
        print(f"{name}: {status}")

    all_passed = all(success for _, success in results)
    print("\n" + "="*50)
    if all_passed:
        print("All tests PASSED!")
    else:
        print("Some tests FAILED")
    print("="*50)


if __name__ == "__main__":
    asyncio.run(main())
