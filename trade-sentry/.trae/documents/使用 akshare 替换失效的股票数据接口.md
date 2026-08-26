# 使用 akshare 替换失效的股票数据接口

## 问题分析
当前 `data_aggregator.py` 文件中使用的多个第三方 API 都已失效，需要使用 akshare 作为替代方案来获取股票行情和涨停股票数据。

## 解决方案

### 步骤 1: 安装 akshare 库
- 在项目中安装 akshare 库，确保能够正常导入和使用

### 步骤 2: 创建 AkShareAPI 类
- 在 `data_fetcher` 目录中创建 `akshare_api.py` 文件
- 实现 `AkShareAPI` 类，包含以下方法：
  - `get_stock_price(code)`: 获取单个股票的实时价格
  - `get_limit_up_stocks()`: 获取涨停股票列表
  - `get_sectors()`: 获取板块列表

### 步骤 3: 修改 DataAggregator 类
- 在 `data_aggregator.py` 中导入 `AkShareAPI`
- 在 `__init__` 方法中初始化 `akshare` 实例
- 更新 API 优先级配置，将 akshare 添加到数据源列表中
- 确保 akshare 作为主要数据源

### 步骤 4: 调整数据格式转换
- 确保 `AkShareAPI` 返回的数据格式与现有的 `UnifiedStockData`、`UnifiedLimitUpStock` 和 `UnifiedSector` 模型兼容
- 实现必要的数据格式转换逻辑

### 步骤 5: 测试和验证
- 运行测试代码，验证数据获取是否正常
- 确保涨停股票数据能够正确获取
- 检查是否需要调整 API 调用频率以避免限制

### 步骤 6: 优化和完善
- 根据测试结果，调整代码以提高稳定性和性能
- 确保错误处理和故障转移机制正常工作
- 添加必要的日志记录

## 预期结果
- 能够正常获取股票实时价格数据
- 能够正常获取涨停股票列表
- 能够正常获取板块数据
- 系统稳定性和可靠性得到保障
- 避免 API 调用频率限制问题