import requests
import json

# API URL和参数
api_url = "https://push2.eastmoney.com/api/qt/clist/get"
params = {
    "pn": "1",
    "pz": "10",
    "po": "1",
    "np": "1",
    "ut": "b2884a393a59ad64002292a3e90d46a5",
    "fltt": "2",
    "invt": "2",
    "fid": "f3",
    "fs": "b:BK0815",
    "fields": "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f62,f115,f128,f136,f152",
    "_": "1620000000000"
}

# 发送请求
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
}

response = requests.get(api_url, headers=headers, params=params)
data = response.json()

# 打印数据结构
print("API响应数据结构:")
print(json.dumps(data, ensure_ascii=False, indent=2))

# 分析字段含义
print("\n分析字段含义:")
if "data" in data and "diff" in data["data"]:
    stock = data["data"]["diff"][0] if data["data"]["diff"] else {}
    print("股票数据字段:")
    field_mapping = {
        "f1": "未知",
        "f2": "最新价",
        "f3": "涨跌幅",
        "f4": "涨跌额",
        "f5": "成交量(手)",
        "f6": "成交额(元)",
        "f7": "振幅",
        "f8": "换手率",
        "f9": "市盈率(TTM)",
        "f10": "量比",
        "f12": "股票代码",
        "f13": "市场类型(0:深市,1:沪市)",
        "f14": "股票名称",
        "f15": "最高价",
        "f16": "最低价",
        "f17": "今开",
        "f18": "昨收",
        "f20": "总市值",
        "f21": "流通市值",
        "f23": "市净率",
        "f24": "涨停价",
        "f25": "跌停价",
        "f62": "主力净流入",
        "f115": "市盈率(静)",
        "f128": "未知",
        "f136": "未知",
        "f152": "未知"
    }
    
    for key, value in stock.items():
        if key in field_mapping:
            print(f"{key}: {value} ({field_mapping[key]})")
        else:
            print(f"{key}: {value} (未知字段)")

# 计算涨停股票数量
if "data" in data and "total" in data["data"]:
    print(f"\n涨停股票总数: {data['data']['total']}")
