# Java iPaAS 集成适配器服务说明文档

本项目是一个基于 Java 编写的集成适配器 Demo，主要用于演示如何通过 Apache Camel 进行路由配置、网关集成、日志记录以及与下游系统的交互。

## 1. 项目基础配置

该项目使用统一的 `8086` 端口进行服务暴露，集成了数据库、RocketMQ 以及日志上报功能。

| 模块         | 配置项               | 说明                          |
|--------------|----------------------|-------------------------------|
| **应用信息** | `spring.application.name` | 应用名称：`ipaas-gepics-adapterService` |
| **网络配置** | `server.port`          | 服务端口固定为 **8086**，地址绑定 `0.0.0.0` |
| **数据库**   | `spring.datasource.url` | Oracle 地址：`jdbc:oracle:thin:@//10.203.24.111:1528/QA058.SGM.COM` |
| **MQ 生产者**| `rocketmq.producer.group` | 生产者组名：`gepics_producer_group` |


## 2. 核心路由逻辑

该路由负责处理 `GET` 请求，并将其转发至下游服务。代码中包含了对网关透传参数的处理逻辑。

**路由流程图解：**
1. **接收请求**：监听 `rest:get:gepics01/XXX` 接口。
2. **参数提取**：从 HTTP Header 中提取 `client_id` 和 `trace_id`，用于链路追踪。
3. **日志记录**：使用 `logger` 组件将请求信息（含业务编号 SVCNO）发送至日志服务。

**代码关键片段：**
```java
@Component
public class Route01 extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // 1. 定义入口与业务编号
        from("rest:get:gepics01/XXX")
            .setProperty("SVCNO", constant("gepics01"))
            
            // 2. 网关参数处理逻辑
            .process(exchange -> {
                String clientId = exchange.getIn().getHeader("client_id", String.class);
                if (clientId != null) {
                    exchange.setProperty("X-FROM", "client_id:" + clientId);
                }
                
                String traceId = exchange.getIn().getHeader("trace_id", String.class);
                if (traceId != null) {
                    exchange.setProperty("X-TRACE-ID", traceId);
                }
            })
            
            // 3. 日志上报与下游转发
            .toD("logger:RestRequest?code=code1&from=${exchangeProperty.X-FROM}&to=toT&traceId=${exchangeProperty.X-TRACE-ID}")
            .removeHeader(Exchange.HTTP_URI)
            .to("{{api.gepics02.url}}") // 动态占位符引用配置
            .end();
    }
}
```
## 3. 详细参数配置表

以下是 `application.yml` 中各组件的详细参数配置，建议根据实际压测结果调整超时时间。

### 3.1 网络与 HTTP 组件配置
| 配置 Key                | 默认/设定值 | 描述                      |
|-------------------------|------------|---------------------------|
| `camel.component.http.connect-timeout` | 10000ms    | 连接超时时间          |
| `camel.component.http.so-timeout`      | 10000ms    | 读取超时时间          |
| `camel.component.http.connection-request-timeout` | 10000ms | 从连接池获取连接的超时时间 |

### 3.2 数据库连接池配置 (Hikari)
| 配置 Key                       | 默认/设定值 | 描述                      |
|--------------------------------|------------|---------------------------|
| `spring.datasource.hikari.maximum-pool-size` | 10         | 最大连接数            |
| `spring.datasource.hikari.minimum-idle`     | 1          | 最小空闲连接数        |
| `spring.datasource.hikari.connection-timeout` | 30000ms    | 等待连接分配的超时时间 |

### 3.3 RocketMQ 生产者配置
| 配置 Key                        | 默认/设定值 | 描述                      |
|---------------------------------|------------|---------------------------|
| `rocketmq.producer.send-msg-timeout` | 3000ms     | 发送消息超时时间         |
| `rocketmq.producer.retry-times-when-send-failed` | 2          | 同步发送失败重试次数  |
| `rocketmq.producer.max-message-size`      | 4194304 (4MB) | 单条消息最大大小      |

### 3.4 日志服务配置
| 配置 Key                | 默认/设定值       | 描述                          |
|-------------------------|------------------|-------------------------------|
| `ipaas.logger.httpPath`  | `http://127.0.0.1:8186/api/saveLog` | **必填**：日志保存接口地址 |
| `ipaas.logger.poolSize`  | 20               | 日志处理线程池大小             |
| `ipaas.logger.connectTimeout` | 1000ms         | 日志服务连接超时时间          |

### 3.5 下游系统配置
- **下游地址 (`api.gepics02.url`)**: `http://localhost:8086/gepics02/XXX`
- **参数说明**: URL 末尾的 `bridgeEndpoint=true` 用于防止 Camel 自动处理 HTTP 协议头，直接透传请求。
