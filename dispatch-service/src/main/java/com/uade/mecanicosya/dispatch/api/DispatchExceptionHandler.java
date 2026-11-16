package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.AssistanceClosedException;
import com.uade.mecanicosya.dispatch.application.AssistanceServiceUnavailableException;
import com.uade.mecanicosya.dispatch.application.DispatchNotFoundException;
import com.uade.mecanicosya.dispatch.application.MechanicBusyException;
import com.uade.mecanicosya.dispatch.application.MechanicDirectoryUnavailableException;
import com.uade.mecanicosya.dispatch.application.OfferAlreadyTakenException;
import com.uade.mecanicosya.dispatch.application.OfferExpiredException;
import com.uade.mecanicosya.dispatch.application.OfferNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cada error que la app tiene que distinguir lleva un {@code code} estable además del texto.
 */
@RestControllerAdvice
public class DispatchExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchExceptionHandler.class);

    @ExceptionHandler(DispatchNotFoundException.class)
    ProblemDetail notFound(DispatchNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Dispatch not found");
        return problem;
    }

    @ExceptionHandler(OfferNotFoundException.class)
    ProblemDetail offerNotFound(OfferNotFoundException exception) {
        return coded(HttpStatus.NOT_FOUND, "Offer not found", "NO_OFFER", exception);
    }

    @ExceptionHandler(OfferAlreadyTakenException.class)
    ProblemDetail alreadyTaken(OfferAlreadyTakenException exception) {
        return coded(HttpStatus.CONFLICT, "Assistance already taken", "ALREADY_TAKEN", exception);
    }

    @ExceptionHandler(OfferExpiredException.class)
    ProblemDetail expired(OfferExpiredException exception) {
        return coded(HttpStatus.CONFLICT, "Offer expired", "OFFER_EXPIRED", exception);
    }

    @ExceptionHandler(MechanicBusyException.class)
    ProblemDetail busy(MechanicBusyException exception) {
        return coded(HttpStatus.CONFLICT, "Mechanic busy", "MECHANIC_BUSY", exception);
    }

    @ExceptionHandler(AssistanceClosedException.class)
    ProblemDetail closed(AssistanceClosedException exception) {
        return coded(HttpStatus.CONFLICT, "Assistance closed", "ASSISTANCE_CLOSED", exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflict with the current state");
        return problem;
    }

    @ExceptionHandler(MechanicDirectoryUnavailableException.class)
    ProblemDetail mechanicServiceUnavailable(MechanicDirectoryUnavailableException exception) {
        LOGGER.warn("Dispatch could not reach mechanic-service", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "mechanic-service is not available; retry the dispatch later"
        );
        problem.setTitle("Mechanic directory unavailable");
        return problem;
    }

    @ExceptionHandler(AssistanceServiceUnavailableException.class)
    ProblemDetail assistanceServiceUnavailable(AssistanceServiceUnavailableException exception) {
        LOGGER.warn("Dispatch could not tell assistance-service about the assignment", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "assistance-service is not available; the offer is still open and can be accepted again"
        );
        problem.setTitle("Assistance service unavailable");
        return problem;
    }

    private static ProblemDetail coded(HttpStatus status, String title, String code, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
