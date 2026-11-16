package com.uade.mecanicosya.identity.application;

import com.uade.mecanicosya.identity.domain.UserAccount;

/**
 * Lo que recibe la app al registrarse, iniciar sesión o renovar: el access token, el refresh token y el usuario.
 */
public record AuthSession(IssuedToken accessToken, String refreshToken, UserAccount user) {
}
