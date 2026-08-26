import requests
import json
import csv
import time
from datetime import datetime

class LimitUpCrawler:
    def __init__(self):
        self.api_url = "https://push2.eastmoney.com/api/qt/clist/get"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        self.field_mapping = {
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
            "f13": "市场类型",
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
            "f115": "市盈率(静)"
        }
        
    def get_limit_up_stocks(self, page_size=50):
        """获取涨停股票数据"""
        all_stocks = []
        page = 1
        total_count = 0
        
        while True:
            params = {
                "pn": str(page),
                "pz": str(page_size),
                "po": "1",
                "np": "1",
                "ut": "b2884a393a59ad64002292a3e90d46a5",
                "fltt": "2",
                "invt": "2",
                "fid": "f3",
                "fs": "b:BK0815",
                "fields": "f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f62,f115",
                "_": str(int(time.time() * 1000))
            }
            
            try:
                response = requests.get(self.api_url, headers=self.headers, params=params, timeout=10)
                response.raise_for_status()  # 检查响应状态
                data = response.json()
                
                if data.get("rc") != 0:
                    print(f"API请求失败: {data.get('msg', '未知错误')}")
                    break
                
                if "data" not in data or "diff" not in data["data"]:
                    print("数据格式错误")
                    break
                
                stocks = data["data"]["diff"]
                if not stocks:
                    break
                
                all_stocks.extend(stocks)
                
                # 获取总数
                if total_count == 0:
                    total_count = data["data"].get("total", 0)
                
                # 检查是否需要继续分页
                if len(all_stocks) >= total_count:
                    break
                
                page += 1
                time.sleep(1)  # 防止请求过快被封
                
            except Exception as e:
                print(f"请求失败: {str(e)}")
                break
        
        return all_stocks
    
    def format_stock_data(self, stocks):
        """格式化股票数据"""
        formatted_stocks = []
        
        for stock in stocks:
            formatted_stock = {}
            for key, value in stock.items():
                if key in self.field_mapping:
                    field_name = self.field_mapping[key]
                    # 格式化特殊字段
                    if key == "f13":
                        formatted_stock[field_name] = "深市" if value == 0 else "沪市"
                    else:
                        formatted_stock[field_name] = value
            formatted_stocks.append(formatted_stock)
        
        return formatted_stocks
    
    def save_to_json(self, stocks, filename=None):
        """保存数据到JSON文件"""
        if filename is None:
            filename = f"limit_up_stocks_{datetime.now().strftime('%Y%m%d')}.json"
        
        formatted_stocks = self.format_stock_data(stocks)
        
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(formatted_stocks, f, ensure_ascii=False, indent=2)
        
        print(f"数据已保存到 {filename}")
    
    def save_to_csv(self, stocks, filename=None):
        """保存数据到CSV文件"""
        if filename is None:
            filename = f"limit_up_stocks_{datetime.now().strftime('%Y%m%d')}.csv"
        
        formatted_stocks = self.format_stock_data(stocks)
        
        if not formatted_stocks:
            print("没有数据可保存")
            return
        
        # 获取所有字段名
        fieldnames = list(formatted_stocks[0].keys())
        
        with open(filename, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(formatted_stocks)
        
        print(f"数据已保存到 {filename}")
    
    def run(self, output_format="json"):
        """运行爬虫"""
        print("开始获取涨停股票数据...")
        start_time = time.time()
        
        stocks = self.get_limit_up_stocks()
        
        if not stocks:
            print("未获取到涨停股票数据")
            return
        
        end_time = time.time()
        print(f"成功获取 {len(stocks)} 只涨停股票数据，耗时 {end_time - start_time:.2f} 秒")
        
        # 保存数据
        if output_format == "json":
            self.save_to_json(stocks)
        elif output_format == "csv":
            self.save_to_csv(stocks)
        else:
            print("不支持的输出格式")

if __name__ == "__main__":
    crawler = LimitUpCrawler()
    # 运行爬虫，保存为JSON格式
    crawler.run(output_format="json")
    # 也可以保存为CSV格式
    crawler.run(output_format="csv")
