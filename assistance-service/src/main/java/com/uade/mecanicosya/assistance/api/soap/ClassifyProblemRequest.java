package com.uade.mecanicosya.assistance.api.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

/** Pedido SOAP de clasificación; el esquema está en {@code ws/triage.xsd}. */
@XmlRootElement(name = "ClassifyProblemRequest", namespace = TriageSoapEndpoint.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"description", "vehicleType"})
public class ClassifyProblemRequest {

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE, required = true)
    private String description;

    @XmlElement(namespace = TriageSoapEndpoint.NAMESPACE)
    private String vehicleType;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }
}
