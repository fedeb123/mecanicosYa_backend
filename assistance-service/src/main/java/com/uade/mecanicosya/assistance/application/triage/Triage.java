package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;

/** Resultado de clasificar el texto de un pedido. */
public record Triage(ProblemCategory category, Urgency urgency, double confidence, TriageSource source) {

    public Triage {
        if (category == null || urgency == null || source == null) {
            throw new IllegalArgumentException("Category, urgency and source are required");
        }
        confidence = Math.round(Math.clamp(confidence, 0.0, 1.0) * 100) / 100.0;
    }

    public String problemType() {
        return category.problemType();
    }

    public String requiredSkill() {
        return category.requiredSkill();
    }
}
