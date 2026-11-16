package com.uade.mecanicosya.assistance.api;

import com.uade.mecanicosya.assistance.application.triage.Triage;
import com.uade.mecanicosya.assistance.application.triage.TriageService;
import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clasifica un texto sin crear un pedido; la app lo usa para sugerir el tipo de problema antes de enviarlo. */
@RestController
@RequestMapping("/api/triage")
@Tag(name = "Triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping
    @Operation(summary = "Classify the description of a problem into a problem type, required skill and urgency",
            description = "Uses the Claude API when ANTHROPIC_API_KEY is set and falls back to keywords "
                    + "when it is not set or does not answer. Only the description and the vehicle type are sent.")
    public TriageResponse classify(@Valid @RequestBody TriageRequest request) {
        return TriageResponse.from(triageService.classify(request.description(), request.vehicleType()));
    }

    public record TriageRequest(@NotBlank @Size(max = 1000) String description, VehicleType vehicleType) {
    }

    public record TriageResponse(
            String category,
            String problemType,
            String requiredSkill,
            Urgency urgency,
            double confidence,
            TriageSource source
    ) {
        static TriageResponse from(Triage triage) {
            return new TriageResponse(
                    triage.category().name(),
                    triage.problemType(),
                    triage.requiredSkill(),
                    triage.urgency(),
                    triage.confidence(),
                    triage.source()
            );
        }
    }
}
