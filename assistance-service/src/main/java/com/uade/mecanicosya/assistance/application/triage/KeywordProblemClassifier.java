package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clasificador de respaldo: busca palabras clave en el texto, sin red ni dependencias. Es el que se usa cuando no hay
 * API key de Anthropic o la API no responde. No entiende negaciones ni contexto ("no es la cadena" cuenta como
 * cadena); para eso está el modelo.
 */
public class KeywordProblemClassifier implements ProblemClassifier {

    private static final Map<ProblemCategory, List<String>> KEYWORDS = new EnumMap<>(Map.of(
            ProblemCategory.CHAIN, List.of("cadena", "eslabon"),
            ProblemCategory.TIRE, List.of(
                    "pinch", "goma", "cubierta", "camara", "desinfl", "sin aire", "pierde aire", "perdiendo aire",
                    "neumatico", "rueda baja", "clavo", "vidrio"
            ),
            ProblemCategory.WHEEL, List.of(
                    "rayo", "llanta", "rueda doblada", "rueda torcida", "rueda chueca", "aro ", "maza", "eje ",
                    "rueda floja", "rueda se mueve", "descentrad", "rueda bailando"
            ),
            ProblemCategory.BRAKES, List.of("freno", "frena", "pastilla", "zapata", "disco"),
            ProblemCategory.GEARS, List.of(
                    "cambio", "descarrilador", "pinon", "cassette", "marcha", "no pasa", "salta la cadena"
            ),
            ProblemCategory.ELECTRICAL, List.of(
                    "bateria", "cargador", "no carga", "electric", "controlador", "display", "pantalla", "luz",
                    "luces", "fusible", "sin asistencia", "burro de arranque"
            ),
            ProblemCategory.ENGINE, List.of(
                    "se apaga", "se para", "se plant", "humo", "aceite", "nafta", "combustible", "recalient",
                    "temperatura", "embrague", "bujia", "carburador", "se ahoga", "pistonea", "se muere"
            )
    ));

    /** Frases que según el vehículo apuntan al motor a combustión (moto) o al eléctrico (bici eléctrica). */
    private static final List<String> POWER_TERMS = List.of("motor", "no arranca", "no prende", "no enciende", "acelera");

    private static final List<String> HIGH_URGENCY = List.of(
            "autopista", "ruta", "general paz", "panamericana", "noche", "oscur", "peligr", "insegur", "accidente",
            "choque", "choco", "chocaron", "me cai", "caida", "golpe", "herid", "lastim", "sangr", "no frena",
            "sin freno", "humo", "fuego", "incendi", "quemado", "chispa", "zona fea", "urgente", "emergencia",
            "auxilio", "puente"
    );

    private static final List<String> LOW_URGENCY = List.of(
            "ruido", "chirri", "a veces", "de vez en cuando", "puedo seguir", "sigo andando", "todavia anda",
            "anda igual", "un poco", "leve", "cuando puedan", "no es urgente", "sin apuro", "revis",
            "mantenimiento", "ajust"
    );

    @Override
    public Triage classify(String description, VehicleType vehicleType) {
        String text = " " + normalize(description) + " ";
        Map<ProblemCategory, Integer> hits = new EnumMap<>(ProblemCategory.class);
        KEYWORDS.forEach((category, words) -> hits.put(category, count(text, words)));
        int powerHits = count(text, POWER_TERMS);
        if (powerHits > 0) {
            ProblemCategory power = vehicleType == VehicleType.MOTORCYCLE ? ProblemCategory.ENGINE
                    : vehicleType == VehicleType.E_BIKE ? ProblemCategory.ELECTRICAL
                    : null;
            if (power != null) {
                hits.merge(power, powerHits, Integer::sum);
            }
        }
        // "salta la cadena" o "se sale la cadena al cambiar" son de cambios, no de cadena rota.
        if (hits.get(ProblemCategory.GEARS) > 0 && hits.get(ProblemCategory.CHAIN) > 0 && text.contains("salta")) {
            hits.merge(ProblemCategory.CHAIN, -1, Integer::sum);
        }

        ProblemCategory best = ProblemCategory.OTHER;
        int bestHits = 0;
        for (ProblemCategory category : ProblemCategory.values()) {
            int categoryHits = hits.getOrDefault(category, 0);
            if (categoryHits > bestHits) {
                best = category;
                bestHits = categoryHits;
            }
        }
        double confidence = bestHits == 0 ? 0.2 : Math.min(0.9, 0.55 + 0.15 * bestHits);
        return new Triage(best, urgency(text), confidence, TriageSource.KEYWORDS);
    }

    private static Urgency urgency(String text) {
        if (text.contains(" no es urgente")) {
            return Urgency.LOW;
        }
        if (count(text, HIGH_URGENCY) > 0) {
            return Urgency.HIGH;
        }
        if (count(text, LOW_URGENCY) > 0) {
            return Urgency.LOW;
        }
        return Urgency.MEDIUM;
    }

    /** Cuántas de las palabras aparecen en el texto, cada una al principio de una palabra. */
    private static int count(String text, List<String> words) {
        int total = 0;
        for (String word : words) {
            if (text.contains(" " + word)) {
                total++;
            }
        }
        return total;
    }

    /** Minúsculas, sin tildes y con un solo espacio entre palabras. */
    static String normalize(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").trim();
    }
}
