# ETF定时任务实现计划

## 1. 需求分析

* 每天凌晨0点执行定时任务

* 使用akshare获取所有中国国内ETF基本信息

* 保存到MongoDB的etf\_basic\_info集合

* 保证集合中没有重复的ETF数据，根据symbol判断

* 集合字段：id, symbol, market（固定CN）, source（固定akshare）, name, updated\_at，如果akshare的接口没有的自动则不存

## 2. 实现步骤

### 2.1 修改数据库操作类（app/database/mongo.py）

* 新增`upsert_etf_basic_info`方法，用于更新或插入ETF基本信息

* 实现根据symbol字段的upsert操作

### 2.2 修改ETF服务类（app/services/etf\_service.py）

* 新增`get_all_etf_basic_info`方法，使用akshare的`fund_etf_spot_em()`接口获取所有ETF数据

* 新增`transform_etf_basic_info`方法，将获取的数据转换为数据库存储格式

### 2.3 修改定时任务类（app/tasks/etf\_task.py）

* 新增`update_etf_basic_info`函数，实现ETF基本信息更新逻辑

* 配置每天凌晨0点执行的定时任务

* 添加到现有的调度器中

## 3. 技术细节

* 使用apscheduler的CronTrigger实现每天凌晨0点执行

* 使用MongoDB的update\_one方法，设置upsert=True实现去重

* 使用akshare的fund\_etf\_spot\_em()接口获取ETF基本信息

* 数据转换时确保字段符合要求：symbol, market, source, name, updated\_at

## 4. 测试验证

* 运行服务，检查定时任务是否正常启动

* 手动触发任务，验证数据是否正确保存到MongoDB

* 检查集合中是否存在重复数据

## 5. 文件修改清单

* app/database/mongo.py：新增upsert\_etf\_basic\_info方法

* app/services/etf\_service.py：新增get\_all\_etf\_basic\_info和transform\_etf\_basic\_info方法

* app/tasks/etf\_task.py：新增update\_etf\_basic\_info函数和定时任务配置

