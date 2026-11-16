package com.uade.mecanicosya.assistance.api.soap;

import com.uade.mecanicosya.assistance.application.triage.Triage;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

/** Respuesta SOAP de clasificación: los mismos campos que devuelve {@code POST /api/triage}. */
@XmlRootElement(name = "ClassifyProblemResponse", namespace = TriageSoapEndpoint.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"category", "problemType", "requiredSkill", "urgency", "confidence", "source"})
public class ClassifyProblemResponse {

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String category;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String problemType;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String requiredSkill;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String urgency;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE)
    private double confidence;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String source;

    static ClassifyProblemResponse from(Triage triage) {
        ClassifyProblemResponse response = new ClassifyProblemResponse();
        response.category = triage.category().name();
        response.problemType = triage.problemType();
        response.requiredSkill = triage.requiredSkill();
        response.urgency = triage.urgency().name();
        response.confidence = triage.confidence();
        response.source = triage.source().name();
        return response;
    }

    public String getCategory() {
        return category;
    }

    public String getProblemType() {
        return problemType;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public String getUrgency() {
        return urgency;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }
}
