package com.uade.mecanicosya.shared.messaging;

import com.uade.mecanicosya.shared.observability.CorrelationIdFilter;

/**
 * Headers que acompañan a cada mensaje en Kafka.
 */
public final class MessageHeaders {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String CORRELATION_ID = CorrelationIdFilter.HEADER;

    private MessageHeaders() {
    }
}
