package com.uade.mecanicosya.assistance.application;

import com.uade.mecanicosya.assistance.domain.AssistanceRepository;
import com.uade.mecanicosya.assistance.domain.StatusChange;
import com.uade.mecanicosya.assistance.domain.StatusChangeRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StatusHistoryRecorder {

    private final StatusChangeRepository statusChangeRepository;
    private final AssistanceRepository assistanceRepository;

    public StatusHistoryRecorder(
            StatusChangeRepository statusChangeRepository,
            AssistanceRepository assistanceRepository
    ) {
        this.statusChangeRepository = statusChangeRepository;
        this.assistanceRepository = assistanceRepository;
    }

    @EventListener
    public void onStatusChanged(AssistanceStatusChangedEvent event) {
        statusChangeRepository.save(new StatusChange(
                UUID.randomUUID(),
                assistanceRepository.getReferenceById(event.assistanceId()),
                event.fromStatus(),
                event.toStatus(),
                event.occurredAt()
        ));
    }
}
