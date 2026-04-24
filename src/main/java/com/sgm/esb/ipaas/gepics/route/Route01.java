package com.sgm.esb.ipaas.gepics.route;

import com.sgm.esb.ipaas.log.LogConstant;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
//一种业务类型对应一个route，route后面的编号对应接口编号
@Component
public class Route01 extends RouteBuilder {
    @Override
    public void configure() throws Exception {

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
