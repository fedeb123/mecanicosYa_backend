package com.uade.mecanicosya.dispatch.application;

import java.util.UUID;

/**
 * Le avisa a assistance-service qué mecánico tomó el pedido. Con la cola de mensajes, este puerto pasa a publicar
 * un evento en lugar de hacer una llamada REST.
 */
public interface AssistanceAssignmentPort {

    void assign(UUID assistanceId, UUID mechanicId);
}
