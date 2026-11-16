package com.uade.mecanicosya.mechanics.infrastructure.messaging;

import com.uade.mecanicosya.mechanics.application.MechanicFacade;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AssistanceEventsListenerTest {

    private final MechanicFacade facade = mock(MechanicFacade.class);
    private final AssistanceEventsListener listener = new AssistanceEventsListener(facade, JsonMapper.builder().build());

    @Test
    void aRatingIsAddedToTheMechanic() {
        UUID assistanceId = UUID.randomUUID();
        UUID mechanicId = UUID.randomUUID();

        listener.onAssistanceEvent("""
                {"type":"ASSISTANCE_RATED","assistanceId":"%s","mechanicId":"%s","score":5}
                """.formatted(assistanceId, mechanicId));

        verify(facade).rate(mechanicId, assistanceId, 5);
    }

    @Test
    void otherAssistanceEventsAreIgnored() {
        listener.onAssistanceEvent("""
                {"type":"ASSISTANCE_REQUESTED","assistanceId":"%s","requiredSkill":"CHAIN_REPAIR"}
                """.formatted(UUID.randomUUID()));

        verifyNoInteractions(facade);
    }

    @Test
    void aRatingWithoutMechanicIsRejected() {
        assertThatThrownBy(() -> listener.onAssistanceEvent("""
                {"type":"ASSISTANCE_RATED","assistanceId":"%s","score":5}
                """.formatted(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
