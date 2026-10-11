package com.uade.mecanicosya.assistance.application;

public interface DispatchPort {

    DispatchOutcome dispatch(DispatchCommand command);
}

