package com.uade.mecanicosya.dispatch.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OfferAnswerRequest(@NotNull UUID mechanicId) {
}
