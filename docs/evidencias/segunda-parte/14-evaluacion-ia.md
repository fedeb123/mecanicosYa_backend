# Evaluación de la clasificación

Salida de `TriageEvaluationTest` con la clave real de Anthropic, corrida el 15/11/2026 en una Mac con:

```bash
mvn -pl assistance-service -am test -Dtest=TriageEvaluationTest -Dtriage.eval=true -Dsurefire.failIfNoSpecifiedTests=false
```

`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 93.88 s`

Modelo `claude-opus-5-5`, esfuerzo `low`, 40 casos.

| Clasificador | Categoría | Urgencia |
|---|---|---|
| Claude | 40/40 (100%) | 37/40 (93%) |
| Palabras clave | 33/40 (83%) | 38/40 (95%) |

Latencia de Claude: mediana 1928 ms, p95 2362 ms. Llamadas fallidas: 0.

## Errores de Claude

- #12 el motor deja de ayudar en las subidas, como que corta la asistencia: esperaba ELECTRICAL/MEDIUM, dio ELECTRICAL/LOW
- #28 el cargador no carga la batería, la luz queda en rojo: esperaba ELECTRICAL/LOW, dio ELECTRICAL/MEDIUM
- #32 no es la cadena, es el piñón de atrás que patina cuando hago fuerza: esperaba GEARS/MEDIUM, dio GEARS/LOW
