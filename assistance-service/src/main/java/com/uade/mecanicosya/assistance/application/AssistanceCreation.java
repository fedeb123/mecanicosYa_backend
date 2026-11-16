package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRequest;

/**
 * La asistencia creada, o la que ya existía con la misma {@code Idempotency-Key} ({@code replayed}).
 */
public record AssistanceCreation(AssistanceRequest assistance, boolean replayed) {
}
