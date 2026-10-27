# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.7/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.7/maven-plugin/build-image.html)
* [OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.7/reference/web/servlet.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

---

## 打包、传输与 Docker 部署

### 打包（Windows）

环境要求：JDK 21、Maven（本机验证版本 3.6.3）。

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\StockAI
mvn clean package -DskipTests
# 产物：target\stockai-service-1.0.0.jar
```

### 需要传输的文件

| 上传 | 不上传 |
|---|---|
| `target/stockai-service-1.0.0.jar` | `src/`、`target/classes/`、`target/*.original`、`.mvn/`、`pom.xml`（目标机不需要重新编译） |

配置已打进 jar（`src/main/resources/application.yml`），部署时用环境变量覆盖，无需单独传输配置文件。

### 部署为 Docker 服务（Linux）

```bash
bash deploy-stockai.sh
```

常用环境变量（Spring Boot 宽松绑定，按需覆盖 jar 内 `application.yml`）：

| 变量 | 对应配置 | 说明 |
|---|---|---|
| `SERVER_PORT` | `server.port` | 默认 8888 |
| `SPRING_DATA_MONGODB_URI` | `spring.data.mongodb.uri` | 默认 `mongodb://localhost:27017/tradingagents`，容器内必须覆盖 |
| `SPRING_AI_OPENAI_API_KEY` | `spring.ai.openai.api-key` | 生产环境必须用自己的 Key |
| `SPRING_AI_OPENAI_BASE_URL` | `spring.ai.openai.base-url` | OpenAI 兼容地址 |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | `spring.ai.openai.chat.options.model` | 模型名 |
| `MONITOR_CRON` / `SCREENING_CRON` / `MARKET_REGIME_CRON` | 定时任务 | 可选 |
| `FEISHU_ENABLED` / `FEISHU_WEBHOOK_URL` / `FEISHU_SECRET` | 飞书推送 | 可选，默认关闭 |

> `application.yml` 中的默认 Key 仅建议本地使用，生产部署请用环境变量覆盖，并避免提交真实密钥。

### 验证

```bash
docker ps | grep stockai-service
docker logs -f stockai-service
curl -s -X POST http://localhost:8888/config/get \
  -H 'Content-Type: application/json' -d '{"userId":"test"}'
```

