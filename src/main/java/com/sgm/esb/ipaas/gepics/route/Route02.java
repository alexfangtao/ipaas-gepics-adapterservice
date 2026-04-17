package com.sgm.esb.ipaas.gepics.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

//一种业务类型对应一个route，route后面的编号对应接口编号
@Component
public class Route02 extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        //gepics02-接口编号，XXX-业务描述
        from("rest:get:gepics02/XXX")
                .process(p->{
                    Thread.sleep(20);
                })
                .setBody(simple("{\"status\":\"UP\"}"))
                .end();
    }
}
