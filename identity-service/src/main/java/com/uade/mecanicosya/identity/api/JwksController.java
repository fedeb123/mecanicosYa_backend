package com.uade.mecanicosya.identity.api;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * La clave pública con la que los otros servicios validan los tokens (JSON Web Key Set).
 */
@RestController
@Tag(name = "Auth")
public class JwksController {

    private final JWKSet publicKeys;

    public JwksController(JWKSet publicKeys) {
        this.publicKeys = publicKeys;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Public keys that verify the access tokens")
    public Map<String, Object> keys() {
        return publicKeys.toJSONObject();
    }
}
