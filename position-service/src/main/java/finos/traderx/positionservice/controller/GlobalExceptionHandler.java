package finos.traderx.positionservice.controller;

import java.util.UUID;
import finos.traderx.logging.SafeLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception error) {
        return serverError(error);
    }

    public static ResponseEntity<String> serverError(Exception error) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error [correlationId={}]: {}", correlationId, SafeLog.exception(error));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("X-Correlation-ID", correlationId)
            .body("Internal server error. Reference: " + correlationId);
    }

}
