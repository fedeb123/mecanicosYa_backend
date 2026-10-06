package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;

import java.util.List;

public interface MechanicDirectory {

    List<MechanicCandidate> findCandidates(String skill, String vehicleType);
}

