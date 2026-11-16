package com.uade.mecanicosya.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pasa el claim {@code role} a la authority {@code ROLE_<rol>} y suma {@code MECHANIC_APPROVED} cuando el
 * mecánico ya fue aprobado.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String claim = jwt.getClaimAsString(TokenClaims.ROLE);
        if (claim == null) {
            return List.of();
        }
        Role role;
        try {
            role = Role.valueOf(claim);
        } catch (IllegalArgumentException unknown) {
            return List.of();
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.authority()));
        if (role == Role.MECHANIC && TokenClaims.APPROVED.equals(jwt.getClaimAsString(TokenClaims.VERIFICATION_STATUS))) {
            authorities.add(new SimpleGrantedAuthority(TokenClaims.APPROVED_MECHANIC));
        }
        return authorities;
    }
}
