package com.sgm.esb.ipaas.gepics.route;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
//一种业务类型对应一个route，route后面的编号对应接口编号
@Component
public class Route01 extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        //gepics01-接口编号，XXX-业务描述
        from("rest:get:gepics01/XXX").setProperty("SVCNO",  constant("gepics01"))
                .process(p->{
                    //根据实际场景确定传递的值
                    p.setProperty("X-TRACE-ID", "123123123");
                    //如果通过网关暴露接口，可从请求头中获取client_id设置为from按照client_id:xxx拼接,保存日志时会从ITAM系统获取应用的英文短名称
                    //from = "client_id:6Y5T9tFeRRVqNNf9l7BebRa9pLv2P7LX6CMfh4q6QxA2Q1zepqKSp4Wathz"
                    p.setProperty("from", "client_id:6Y5T9tFeRRVqNNf9l7BebRa9pLv2P7LX6CMfh4q6QxA2Q1zepqKSp4Wathz");
                })
                .toD("logger:RestRequest?code=code1&from=${exchangeProperty.from}&to=toT&traceId=${exchangeProperty.X-TRACE-ID}")
                .removeHeader(Exchange.HTTP_URI)
                .toD("logger:CallHttp?code=code2&from=ipaas-gepics-adapterService&to=toT&traceId=${exchangeProperty.X-TRACE-ID}")
                .to("http://localhost:8086/api/gepics02/XXX")
                .end();
    }
}
