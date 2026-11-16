package com.uade.mecanicosya.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private final UUID riderId = UUID.randomUUID();

    @Test
    void readsTheUserFromTheToken() {
        CurrentUser user = CurrentUser.from(decode(TestJwt.approvedMechanic(riderId)));

        assertThat(user.id()).isEqualTo(riderId);
        assertThat(user.role()).isEqualTo(Role.MECHANIC);
        assertThat(user.isApprovedMechanic()).isTrue();
    }

    @Test
    void aServiceTokenHasNoUserId() {
        CurrentUser service = CurrentUser.from(decode(TestJwt.service("dispatch-service")));

        assertThat(service.id()).isNull();
        assertThat(service.subject()).isEqualTo("dispatch-service");
        assertThat(service.isPrivileged()).isTrue();
    }

    @Test
    void aRiderCannotAskForAnotherRidersResource() {
        CurrentUser rider = CurrentUser.from(decode(TestJwt.rider(riderId)));

        assertThat(rider.resolveOwner(null)).isEqualTo(riderId);
        assertThat(rider.resolveOwner(riderId)).isEqualTo(riderId);
        assertThatThrownBy(() -> rider.resolveOwner(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdminCanAskForAnyUser() {
        CurrentUser admin = CurrentUser.from(decode(TestJwt.admin()));
        UUID other = UUID.randomUUID();

        assertThat(admin.resolveOwner(other)).isEqualTo(other);
    }

    @Test
    void aServiceMustSayWhichUserItIsAbout() {
        CurrentUser service = CurrentUser.from(decode(TestJwt.service("dispatch-service")));

        assertThatThrownBy(() -> service.resolveOwner(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAnApprovedMechanicGetsTheExtraAuthority() {
        JwtRoleConverter converter = new JwtRoleConverter();

        assertThat(converter.convert(decode(TestJwt.approvedMechanic(riderId))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_MECHANIC", TokenClaims.APPROVED_MECHANIC);
        assertThat(converter.convert(decode(TestJwt.pendingMechanic(riderId))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MECHANIC");
        assertThat(converter.convert(decode(TestJwt.token(riderId.toString(), Role.RIDER, TokenClaims.APPROVED))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_RIDER");
    }

    private static Jwt decode(String token) {
        return TestJwt.decoder().decode(token);
    }
}
