package com.uade.mecanicosya.shared.messaging;

import com.uade.mecanicosya.shared.observability.CorrelationIdFilter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Pone en el MDC el identificador de correlación que viene en el header del mensaje, para que los logs del
 * consumidor queden unidos a los del request HTTP que originó el evento.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(
            ConsumerRecord<Object, Object> record,
            Consumer<Object, Object> consumer
    ) {
        Header header = record.headers().lastHeader(MessageHeaders.CORRELATION_ID);
        if (header == null) {
            header = record.headers().lastHeader(MessageHeaders.EVENT_ID);
        }
        if (header != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, new String(header.value(), StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
}
