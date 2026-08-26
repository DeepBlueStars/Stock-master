import sys
from pathlib import Path

# 将项目根目录添加到Python路径
sys.path.append(str(Path(__file__).parent))

from app.database.mongo import mongo_client
from app.services.etf_service import etf_service

print("开始清理和重新插入ETF基本信息数据")

# 1. 删除所有数据
print("\n1. 删除etf_basic_info集合中的所有数据")
try:
    result = mongo_client.etf_basic_info.delete_many({})
    print(f"成功删除{result.deleted_count}条数据")
except Exception as e:
    print(f"删除数据失败: {e}")
    sys.exit(1)

# 2. 重新插入数据
print("\n2. 重新获取并插入ETF基本信息数据")
try:
    # 获取所有ETF基本信息并转换为数据库存储格式
    all_etf_basic_info = etf_service.get_all_etf_basic_info_for_db()
    print(f"获取到{len(all_etf_basic_info)}条ETF基本信息")
    
    # 插入数据
    success_count = 0
    for etf_basic in all_etf_basic_info:
        if mongo_client.upsert_etf_basic_info(etf_basic):
            success_count += 1
    
    print(f"成功插入{success_count}条数据")
except Exception as e:
    print(f"插入数据失败: {e}")
    sys.exit(1)

# 3. 验证数据
print("\n3. 验证数据")
try:
    total_count = mongo_client.etf_basic_info.count_documents({})
    print(f"总数据量：{total_count}")
    
    # 查询前3条数据
    print("前3条数据：")
    for i, doc in enumerate(mongo_client.etf_basic_info.find().limit(3)):
        print(f"{i+1}. symbol={doc['symbol']}, full_symbol={doc['full_symbol']}, name={doc['name']}")
    
    # 检查是否有重复数据
    print("\n检查重复数据：")
    duplicate_symbols = mongo_client.etf_basic_info.aggregate([
        {"$group": {"_id": "$symbol", "count": {"$sum": 1}}},
        {"$match": {"count": {"$gt": 1}}}
    ])
    duplicates = list(duplicate_symbols)
    if duplicates:
        print(f"发现重复数据：{duplicates}")
    else:
        print("没有发现重复数据")
    
    print("\n清理和重新插入数据完成！")
except Exception as e:
    print(f"验证数据失败: {e}")
    sys.exit(1)