# data-ingestion Specification

## Purpose
多数据源接入与数据统一格式能力：以 akshare 为主数据源、baostock、tushare 与东方财富/新浪财经等免费源为备用源，采集失败时自动切换备用源，并将各数据源数据统一转换为标准格式后存储到新建集合，供后续分析与展示使用。

## Requirements

### Requirement: 支持主备多数据源接入

系统 SHALL 支持多个免费数据源接入，akshare 为主源，baostock、tushare、东方财富、新浪财经等为备用源；主源失败时 MUST 自动切换到备用源继续采集。

#### Scenario: 主源失败自动切换备用源

- **WHEN** akshare 主源请求失败或返回空数据
- **THEN** 系统自动切换到备用数据源继续采集，采集结果正常返回

### Requirement: 数据统一格式处理

系统 SHALL 将各数据源的数据在入库前统一转换为标准格式，标准格式 MUST 包含标的代码、交易日期、开盘价、最高价、最低价、收盘价、成交量、成交额、数据来源与采集时间等字段。

#### Scenario: 不同数据源数据统一格式

- **WHEN** 从任意数据源采集到数据
- **THEN** 系统将该数据转换为统一标准格式后再入库，字段口径一致

### Requirement: 数据源字段差异映射与清洗

系统 SHALL 在数据源适配器内对字段差异做映射与清洗，MUST 屏蔽各数据源的字段命名与口径差异。

#### Scenario: 字段映射清洗

- **WHEN** 某数据源的字段命名或口径与标准格式不一致
- **THEN** 系统在适配器内完成字段映射与清洗，输出统一格式数据

### Requirement: 数据源密钥通过环境变量配置

系统 SHALL 将数据源所需密钥或 token（如 tushare token）通过环境变量或 `.env` 文件配置（`os.getenv` 读取），MUST NOT 硬编码在代码中。

#### Scenario: 密钥配置

- **WHEN** 数据源需要密钥或 token
- **THEN** 系统从环境变量或 `.env` 文件读取密钥，不在代码中硬编码

### Requirement: 使用新建集合存储数据

系统 SHALL 将新采集的数据存储到新建的 MongoDB 集合，MUST NOT 改造或复用已有集合（如 `market_quotes`、`etf_daily_quotes`、`etf_basic_info`）。

#### Scenario: 新建集合存储

- **WHEN** 数据采集完成
- **THEN** 系统将数据写入新建的集合，不改动已有集合
