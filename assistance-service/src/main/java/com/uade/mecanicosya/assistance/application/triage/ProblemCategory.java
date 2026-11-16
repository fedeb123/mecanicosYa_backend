package com.uade.mecanicosya.assistance.application.triage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo de problemas que entiende la clasificación. Cada uno trae el {@code problemType} que se guarda en el
 * pedido y la habilidad que tiene que tener el mecánico para recibir la oferta.
 */
public enum ProblemCategory {
    CHAIN("CHAIN", "CHAIN_REPAIR", "The chain broke, came off, is stuck or skips."),
    TIRE("TIRE", "TIRE_REPAIR", "Flat or punctured tire, damaged inner tube or tire, loses air."),
    WHEEL("WHEEL", "WHEEL_REPAIR", "Bent or wobbling wheel rim, broken spokes, loose hub or axle."),
    BRAKES("BRAKES", "BRAKE_REPAIR", "Brakes do not stop, worn pads, broken or loose brake cable, brake noise."),
    GEARS("GEARS", "GEAR_ADJUSTMENT", "Gears do not shift, derailleur bent or out of adjustment, gear cable."),
    ELECTRICAL("ELECTRICAL", "ELECTRICAL_REPAIR",
            "Battery, electric motor or controller of an e-bike, lights, motorcycle battery or starter."),
    ENGINE("ENGINE", "ENGINE_REPAIR",
            "Motorcycle combustion engine: does not start, stalls, overheats, smoke, oil, fuel, clutch."),
    OTHER("OTHER", "GENERAL_REPAIR", "Anything else, or not enough information to tell.");

    private final String problemType;
    private final String requiredSkill;
    private final String definition;

    ProblemCategory(String problemType, String requiredSkill, String definition) {
        this.problemType = problemType;
        this.requiredSkill = requiredSkill;
        this.definition = definition;
    }

    public String problemType() {
        return problemType;
    }

    public String requiredSkill() {
        return requiredSkill;
    }

    /** Definición en inglés que se le pasa al modelo en el prompt. */
    public String definition() {
        return definition;
    }

    public static Optional<ProblemCategory> fromProblemType(String problemType) {
        if (problemType == null) {
            return Optional.empty();
        }
        String normalized = problemType.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(category -> category.problemType.equals(normalized)).findFirst();
    }
}
