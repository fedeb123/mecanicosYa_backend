package com.uade.mecanicosya.dispatch.api;

import java.util.UUID;

/**
 * El cuerpo es opcional: el mecánico sale del token. Si viene {@code mechanicId}, tiene que ser el del token.
 */
public record OfferAnswerRequest(UUID mechanicId) {
}
