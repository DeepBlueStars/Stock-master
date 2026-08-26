## 实现计划

### 1. 修改MongoDB操作类

* 在`mongo.py`中新增`upsert_etf_basic_info`方法，用于更新或插入ETF基本信息

* 方法使用`symbol`作为唯一标识，实现upsert操作

### 2. 修改ETF服务类

* 在`etf_service.py`中新增`get_all_etf_basic_info`方法，用于获取所有ETF基本信息

* 新增`transform_etf_basic_info`方法，将akshare返回的数据转换为数据库存储格式

### 3. 修改定时任务

* 在`etf_task.py`中新增`update_etf_basic_info`函数，实现ETF基本信息的更新逻辑

* 添加每天凌晨0点执行的定时任务，使用`CronTrigger`替代`IntervalTrigger`

### 4. 确保任务启动

* 在`start_scheduler`函数中添加新的定时任务

### 5. 测试验证

* 确保定时任务正确执行

* 验证数据成功写入MongoDB

* 验证没有重复数据

## 技术要点

* 使用APScheduler的`CronTrigger`实现每天凌晨0点执行

* 使用MongoDB的upsert操作确保数据唯一性

* 使用akshare的`fund_etf_category_sina`获取ETF基本信息

* 数据转换确保字段符合要求：id, symbol, market, source, name, updated\_at

