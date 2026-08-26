# AGENTS.md - StockAI 开发规范

> 本文件面向 AI 编程助手，包含项目构建、测试、代码风格等开发规范。

---

## 一、项目概览

本工作区包含多个独立项目：

| 项目 | 技术栈 | 路径 |
|------|--------|------|
| **StockAI** | Java 21 + Spring Boot 3.5 + MongoDB | `./StockAI/` |
| **StockAI-Console** | Vue 3 + Vite + Element Plus | `./StockAI-Console/` |
| **etf-service** | Python 3 + FastAPI + MongoDB | `./etf-service/` |
| **trade-sentry** | Vue 3 + FastAPI + MySQL/Redis | `./trade-sentry/` |

---

## 二、构建与测试命令

### 2.1 StockAI (Java/Spring Boot)

```bash
cd StockAI

# 构建
./mvnw clean package          # 打包
./mvnw clean package -DskipTests  # 跳过测试打包

# 运行
./mvnw spring-boot:run       # 开发模式运行
java -jar target/stockai-service-1.0.0.jar  # 生产运行

# 测试
./mvnw test                           # 运行所有测试
./mvnw test -Dtest=StockServiceTest   # 运行单个测试类
./mvnw test -Dtest=*ServiceTest       # 运行匹配模式的测试类

# 代码检查
./mvnw checkstyle:check                # Checkstyle 检查
./mvnw spotless:apply                  # 代码格式化 (如果配置了spotless)

# Maven 代理配置 (如有需要)
# 修改 .mvn/jvm.config 或 settings.xml
```

### 2.2 StockAI-Console (Vue 3/Vite)

```bash
cd StockAI-Console

# 安装依赖
npm install

# 开发
npm run dev              # 启动开发服务器 (端口 8889)
npm run build            # 生产构建
npm run preview          # 预览构建结果

# 测试 (如有)
npm run test             # 运行测试
npm run test:unit        # 单元测试

# 注意: 该项目暂无 ESLint/TypeScript 严格配置
```

### 2.3 trade-sentry/frontend (Vue 3 + TypeScript/Vite)

```bash
cd trade-sentry/frontend

# 安装依赖
npm install

# 开发
npm run dev               # 启动开发服务器 (端口 3000)
npm run build             # TypeScript 检查 + 生产构建
npm run preview            # 预览构建结果

# 代码质量
npm run lint              # ESLint 检查 + 自动修复
npm run format            # Prettier 格式化

# 运行单个测试 (如配置了 vitest)
npx vitest run --watch=false path/to/spec.ts
npxvitest related --watch=false src/views/market/index.vue  # 关联测试
```

### 2.4 trade-sentry/backend (Python/FastAPI)

```bash
cd trade-sentry/backend

# 建议使用虚拟环境
python -m venv venv
source venv/bin/activate  # Linux/Mac
venv\Scripts\activate     # Windows

# 安装依赖
pip install -r requirements.txt

# 运行
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 测试
pytest                                    # 运行所有测试
pytest tests/test_api.py                  # 运行单个测试文件
pytest tests/ -k "test_get"              # 运行匹配关键字的测试
pytest --cov=app --cov-report=html        # 覆盖率报告

# API 文档
# 启动服务后访问 http://localhost:8000/docs (Swagger)
# 或 http://localhost:8000/redoc (ReDoc)
```

### 2.5 etf-service (Python/FastAPI)

```bash
cd etf-service

# 安装依赖
pip install -r requirements.txt

# 运行
python -m app.main                        # 启动服务 (端口 8887)
uvicorn app.main:app --reload --port 8887

# 定时任务 (APScheduler)
# 在 app/tasks/etf_task.py 中配置
```

---

## 三、代码风格规范

### 3.1 通用规范

- **字符编码**: UTF-8
- **换行符**: LF (Unix style)
- **缩进**: 2 空格 (Java/JS/TS) 或 4 空格 (Python)
- **一行最大长度**: 120 字符 (Java/TS), 120 字符 (Python)
- **禁止**: 魔法数字、硬编码敏感信息、console.debug

### 3.2 TypeScript/Vue (trade-sentry/frontend)

#### 导入顺序
```typescript
// 1. Vue 核心
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'

// 2. 第三方库
import { ElMessage } from 'element-plus'
import axios from 'axios'

// 3. 内部模块 (@ 别名指向 src/)
import { get, post } from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { User, Position } from '@/types'

// 4. 相对路径
import MyComponent from './components/MyComponent.vue'
```

#### 命名规范
| 类型 | 规范 | 示例 |
|------|------|------|
| 组件文件 | PascalCase | `StockCard.vue`, `UserProfile.vue` |
| 组件名 | PascalCase | `StockCard`, `UserProfile` |
| 组合式函数 | camelCase, `use` 前缀 | `useWebSocket`, `useMarketData` |
| Store | `use` 前缀 + `Store` 后缀 | `useUserStore`, `useMarketStore` |
| 接口/类型 | PascalCase, 可加前缀 | `User`, `Position`, `AIScore` |
| 枚举 | PascalCase, 成员 camelCase | `BoardType`, `TradeType` |
| 布尔变量 | `is`/`has`/`can` 前缀 | `isLoading`, `hasError` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `API_TIMEOUT` |

#### 类型定义示例
```typescript
// 好
interface User {
  id: number
  username: string
  email?: string
}

// 好 - 使用联合类型限制值
type BoardType = 'first' | 'second' | 'third' | 'high'
type TradeType = 'buy' | 'sell'

// 好 - 避免 any，使用 unknown 配合类型守卫
async function fetchData(url: string): Promise<unknown> {
  const res = await fetch(url)
  return res.json()
}
```

#### 组件规范
```vue
<!-- 1. 使用 <script setup lang="ts"> -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

// 2. Props 使用 withDefaults 和 defineProps
interface Props {
  title: string
  count?: number
}
const props = withDefaults(defineProps<Props>(), {
  count: 0
})

// 3. Emits 使用 defineEmits
const emit = defineEmits<{
  (e: 'update', value: number): void
  (e: 'delete'): void
}>()

// 4. 响应式变量使用 ref/reactive
const loading = ref(false)
const data = reactive({ ... })

// 5. computed 明确类型
const doubleCount = computed<number>(() => props.count * 2)
</script>

<!-- 6. 样式使用 scoped -->
<style scoped lang="scss">
// 使用 CSS 变量而非硬编码颜色
.my-class {
  color: var(--color-text-primary);
  background: var(--color-surface);
}
</style>
```

### 3.3 Java (StockAI)

#### 导入规范
```java
// 1. Java 标准库
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

// 2. Spring 框架
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;

// 3. 第三方库 (Lombok, Jackson 等)
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

// 4. 本项目
import com.xian.stockai.po.Position;
import com.xian.stockai.service.StockService;
```

#### 命名规范
| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `StockController`, `PositionService` |
| 方法名 | camelCase | `getStockByCode`, `calculateReturn` |
| 变量名 | camelCase | `stockCode`, `totalAmount` |
| 常量 | UPPER_SNAKE_CASE | `STATE_SUCCESS`, `MAX_RETRY_COUNT` |
| 包名 | 全小写 | `com.xian.stockai.controller` |

#### 类结构顺序
```java
@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
@Slf4j
public class StockController {
    // 1. 常量 (public static final)
    // 2. 成员变量 (private final, @Autowired)
    // 3. 构造方法 (Lombok @RequiredArgsConstructor)
    
    private final StockService stockService;

    // 4. 公开方法 (HTTP endpoints)
    @GetMapping("/{code}")
    public Result<Stock> getStock(@PathVariable String code) {
        // ...
    }
    
    // 5. 私有方法
    private void validateInput(Stock stock) {
        // ...
    }
}
```

#### 返回值规范
```java
// 统一使用 Result<T> 包装响应
public class Result<T> {
    public static final int STATE_SUCCESS = 0;
    public static final int STATE_FAIL = 1;
    
    static public <T> Result<T> success(T data) {
        return new Result<>(STATE_SUCCESS, null, data);
    }
    
    static public <T> Result<T> fail(String message) {
        return new Result<>(STATE_FAIL, message, null);
    }
}

// Controller 使用示例
@PostMapping("/buy")
public Result<Void> buy(@RequestBody StockTrade trade) {
    return stockService.buy(trade);
}
```

### 3.4 Python (etf-service / trade-sentry/backend)

#### 导入规范
```python
# 1. 标准库
import logging
from typing import Dict, List, Any
from datetime import datetime

# 2. 第三方库
import akshare as ak
import pandas as pd
from fastapi import APIRouter, HTTPException

# 3. 本项目 (相对导入)
from .services.etf_service import etf_service
from ..models.user import UserModel
```

#### 命名规范
| 类型 | 规范 | 示例 |
|------|------|------|
| 模块名 | 全小写，下划线分隔 | `etf_service.py`, `user_model.py` |
| 类名 | PascalCase | `ETFService`, `UserModel` |
| 函数名 | snake_case | `get_etf_info`, `calculate_pnl` |
| 变量名 | snake_case | `stock_code`, `total_amount` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `API_TIMEOUT` |
| 私有成员 | `_single_underscore` | `_internal_method()` |

#### 类型注解
```python
# 函数注解
def get_etf_info(symbol: str) -> Dict[str, Any]:
    """获取 ETF 信息"""
    ...

# 避免使用 any，使用 Any 或具体类型
from typing import Any, Dict, List

def process_data(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    ...
```

#### 日志规范
```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def my_function():
    logger.info("开始处理...")
    logger.warning("数据为空")
    logger.error(f"处理失败: {e}", exc_info=True)
```

### 3.5 错误处理规范

#### TypeScript/Vue
```typescript
// 使用 try-catch，错误信息使用 ElMessage 显示
try {
  const res = await get<User>('/api/user')
  return res
} catch (error) {
  console.error('获取用户信息失败', error)
  ElMessage.error('获取用户信息失败，请稍后重试')
  return null
}

// 区分不同错误类型
if (error.response?.status === 401) {
  // 处理未授权
  router.push('/login')
} else if (error.response?.status === 403) {
  ElMessage.error('没有权限')
}
```

#### Java
```java
// 使用 try-catch，异常信息记录日志
try {
    return stockService.getStock(symbol);
} catch (Exception e) {
    log.error("获取股票信息失败: symbol={}", symbol, e);
    return Result.fail("获取股票信息失败: " + e.getMessage());
}
```

#### Python
```python
try:
    result = etf_service.get_etf_info(symbol)
    if not result:
        raise HTTPException(status_code=404, detail="ETF not found")
    return result
except Exception as e:
    logger.error(f"获取ETF信息失败: {e}", exc_info=True)
    raise HTTPException(status_code=500, detail="Internal server error")
```

---

## 四、Git 提交规范

```
feat: 添加涨停股票监控功能
fix: 修复持仓显示错误
docs: 更新 API 文档
style: 调整表格样式
refactor: 重构行情数据获取逻辑
test: 添加风控模块单元测试
chore: 更新依赖包
```

---

## 五、现有 AI 助手指南

### trade-sentry/AGENT.MD

trade-sentry 项目有详细的 [AGENT.MD](./trade-sentry/AGENT.MD)，包含：
- 前端组件库选择 (Element Plus, VueUse, ECharts)
- UI/UX 设计规范 (深色主题, 涨跌颜色)
- 组件封装要求
- 状态管理 (Pinia)
- API 调用规范
- 后端技术栈 (FastAPI, SQLAlchemy, Redis)
- 数据库规范
- 测试要求

开发 trade-sentry 项目时请优先参考该文件。

---

## 六、快速参考

| 操作 | 命令 |
|------|------|
| 前端 lint | `npm run lint -- --fix` |
| 前端类型检查 | `npx vue-tsc --noEmit` |
| Java 构建 | `./mvnw clean package` |
| Java 测试 | `./mvnw test -Dtest=ClassName` |
| Python 格式化 | `black .` / `ruff check --fix .` |
| 运行所有测试 | `pytest` (Python) / `./mvnw test` (Java) |

<comet-ambient-resume>
<!-- Managed by Comet. Edits inside this block may be replaced by comet init/update. -->
<!-- Contract: comet.resume_probe.v2 -->

## Comet Ambient Resume

在这个仓库中，开始处理需要改动或调查的任务前，如果可能存在活跃 Comet workflow，把当前用户请求传入只读探针：`comet resume-probe . --stdin --json`。

- 如果用户通过宿主明确调用任意 Comet Skill（例如 `@comet`、`/comet`、`@comet-native` 或 `/comet-hotfix`），显式调用优先于本恢复协议；不要运行 resume probe，直接进入被调用的 Skill。
- 如果用户通过宿主明确调用的是非 Comet 的 Skill 或斜杠命令，任务意图已由该调用明确：不要运行 resume probe，直接执行该 Skill。
- 如果你正在 Comet 流程内（包括正在等待用户回复你在流程中提出的问题），不要运行 resume probe；把这类回复（例如方案/选项选择）当作当前 change 的继续，直接按用户的选择推进。
- 只信任返回的 `workflow`、`skill` 和 `entrySource`；它们只由项目配置或无配置兼容回退决定。不得扫描或切换另一套 workflow。
- 如果 probe 返回 `auto_resume`，简短说明选中的 active change，并进入 `nextCommand` 指向的永久入口。不要把状态命令当作恢复入口直接推进。
- 如果 probe 返回 `ask_user`，只问一个简短问题并等待用户回复。
- 如果当前请求未明确调用 Comet Skill，且 probe 返回 `out_of_scope` 或 `none`，不要进入 Comet workflow。
- `out_of_scope` 或 `none` 只表示不要因为这个新请求进入 Comet workflow；它绝不表示要暂停或退出一个已在进行的 Comet 流程。
- 如果配置或状态无效且没有 `nextCommand`，停止并报告原因；不要猜测另一个 workflow。
- 不能只因为存在 active change 就把无关任务挂到该 change。Native 的未提交改动由 Native 入口检查，不由探针自动归因。
</comet-ambient-resume>
