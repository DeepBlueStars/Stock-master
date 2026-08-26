"""
测试 akshare 库是否正常工作
"""
import akshare as ak

# 测试获取股票实时数据
print("=== 测试获取股票实时数据 ===")
try:
    # 测试新浪数据源
    stock_data = ak.stock_zh_a_spot_sina(symbol="000001")
    print(f"新浪数据源测试成功:")
    print(stock_data)
except Exception as e:
    print(f"新浪数据源测试失败: {e}")

try:
    # 测试东方财富数据源
    stock_data = ak.stock_zh_a_spot_em()
    print(f"\n东方财富数据源测试成功:")
    print(f"成功获取 {len(stock_data)} 只股票的数据")
    print("前5只股票:")
    print(stock_data.head())
except Exception as e:
    print(f"\n东方财富数据源测试失败: {e}")

# 测试获取涨停股票
print("\n=== 测试获取涨停股票 ===")
try:
    stock_data = ak.stock_zh_a_spot_em()
    limit_up_stocks = stock_data[stock_data['涨跌幅'] >= 9.9]
    print(f"找到 {len(limit_up_stocks)} 只涨停股票")
    if not limit_up_stocks.empty:
        print("前3只涨停股票:")
        print(limit_up_stocks[['代码', '名称', '最新价', '涨跌幅']].head(3))
except Exception as e:
    print(f"获取涨停股票失败: {e}")

# 测试获取板块数据
print("\n=== 测试获取板块数据 ===")
try:
    sector_data = ak.stock_board_industry_name_ths()
    print(f"成功获取 {len(sector_data)} 个板块的数据")
    print("前5个板块:")
    print(sector_data.head())
except Exception as e:
    print(f"获取板块数据失败: {e}")
