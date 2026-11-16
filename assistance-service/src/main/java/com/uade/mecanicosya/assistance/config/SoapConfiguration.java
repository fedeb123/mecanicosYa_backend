package com.uade.mecanicosya.assistance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

/**
 * Servicio SOAP contract-first: el contrato es {@code ws/triage.xsd} y el WSDL se publica en
 * {@code /ws/triage.wsdl}. Los mensajes se validan contra el esquema; uno inválido recibe un SOAP Fault de
 * cliente sin llegar al endpoint. Spring Boot registra el servlet de Spring WS en {@code spring.webservices.path}
 * (sin {@code @EnableWs} propio, para no apagar esa autoconfiguración).
 */
@Configuration(proxyBeanMethods = false)
public class SoapConfiguration implements WsConfigurer {

    @Bean
    XsdSchema triageSchema() {
        return new SimpleXsdSchema(new ClassPathResource("ws/triage.xsd"));
    }

    /** El nombre del bean es el del WSDL: {@code /ws/triage.wsdl}. */
    @Bean(name = "triage")
    DefaultWsdl11Definition triageWsdl(XsdSchema triageSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("TriagePort");
        definition.setServiceName("TriageService");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace("http://mecanicosya.com/ws/triage");
        definition.setSchema(triageSchema);
        return definition;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        PayloadValidatingInterceptor validator = new PayloadValidatingInterceptor();
        validator.setXsdSchema(triageSchema());
        validator.setValidateRequest(true);
        validator.setValidateResponse(true);
        interceptors.add(validator);
    }
}
