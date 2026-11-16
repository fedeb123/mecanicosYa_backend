package com.uade.mecanicosya.identity.application;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
