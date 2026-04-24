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
    public void configure() throws Exception {{

        //gepics01-接口编号，XXX-业务描述
        from("rest:get:gepics01/XXX").setProperty(LogConstant.svcNo,  constant("gepics01"))
                .doTry()
                .process(exchange -> {
                    //如果通过网关暴露接口，可从请求头中获取client_id设置为from按照client_id:xxx拼接,保存日志时会从ITAM系统获取应用的英文短名称
                    //样例 from = "client_id:6Y5T9tFeRRVqNNf9l7BebRa9pLv2P7LX6CMfh4q6QxA2Q1zepqKSp4Wathz"
                    String clientId = exchange.getIn().getHeader("client_id", String.class);
                    if (clientId != null && !clientId.isEmpty()) {
                        exchange.setProperty("X-FROM", "client_id:" + clientId);
                    }

                    // 如果通过网关暴露接口，也可从请求头中获取trace_id，保证链路追踪闭环
                    String traceId = exchange.getIn().getHeader("trace_id", String.class);
                    if (traceId != null && !traceId.isEmpty()) {
                        exchange.setProperty(LogConstant.traceId, traceId);
                    }
                })
                .toD("ipaas-logger:RestRequest?code=code1&fromApp=${exchangeProperty.X-FROM}&toApp=toT")
                .removeHeaders("CamelHttp*")
                .to("{{api.gepics02.url}}")
                .doCatch(Exception.class)
                //全局捕获异常，异常响应及9100日志记录根据业务实际需要调整组装。
                .log("Caught: ${exception.message}")
                .toD("ipaas-logger:exception?code=code9&fromApp=${exchangeProperty.X-FROM}&toApp=toT")
                .setHeader("CamelHttpResponseCode", constant(500))
                .setBody().simple("{\"error\": \"${exception.message}\"}")
                .end()
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
| 配置 Key                       | 默认/设定值  | 描述                      |
|--------------------------------|---------|---------------------------|
| `spring.datasource.hikari.maximum-pool-size` | 10      | 最大连接数            |
| `spring.datasource.hikari.minimum-idle`     | -1      | 最小空闲连接数        |
| `spring.datasource.hikari.connection-timeout` | 30000ms | 等待连接分配的超时时间 |

### 3.3 RocketMQ 生产者配置
| 配置 Key                        | 默认/设定值 | 描述                      |
|---------------------------------|------------|---------------------------|
| `rocketmq.producer.send-msg-timeout` | 3000ms     | 发送消息超时时间         |
| `rocketmq.producer.retry-times-when-send-failed` | 2          | 同步发送失败重试次数  |
| `rocketmq.producer.max-message-size`      | 4194304 (4MB) | 单条消息最大大小      |

### 3.4 日志服务配置
| 配置 Key                | 默认/设定值                              | 描述              |
|-------------------------|-------------------------------------|-----------------|
| `ipaas.logger.httpPath`  | `http://127.0.0.1:8186/api/saveLog` | **必填**：日志保存接口地址 |
| `ipaas.logger.poolSize`  | 20                                  | 日志处理线程池大小       |
| `ipaas.logger.connectTimeout` | 1000ms                              | 日志服务连接超时时间      |
| `ipaas.logger.maxPoolSize` | 20                                  | 日志处理线程池最大线程数    |
| `ipaas.logger.maxQueueSize` | 2000                                | 日志保存线程池最大队列      |
| `ipaas.logger.maxTotalConnections` | 200                                 | 日志保存http客户端最大连接数      |
| `ipaas.logger.connectionsPerRoute` | 20                                  | 日志保存http客户端单个域名连接数      |
| `ipaas.logger.policy` | 3                                   | 日志组件线程池拒绝策略配置（1：Abort 2：Discard 3：DiscardOldest 4：CallerRuns）      |
| `ipaas.logger.responseTimeout` | 1000ms                              | 日志服务响应超时时间      |
| `ipaas.logger.limitSize` | 1048576                              | 日志body字段压缩字符数配置      |
| `ipaas.logger.forceMemoryOnlyStreamCaching` | true                              | streamcache是否强制使用纯内存，默认true，如果为false则使用磁盘缓存，需要根据报文大小及并发数评估磁盘所需的空间      |

### 3.5 下游系统配置
- **下游地址 (`api.gepics02.url`)**: `http://localhost:8086/gepics02/XXX`
- **参数说明**: URL 末尾的 `bridgeEndpoint=true` 用于防止 Camel 自动处理 HTTP 协议头，直接透传请求。
