package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.TradingAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerMoreTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuthentication_returns401() {
        TradingAuthenticationException ex = new TradingAuthenticationException("bad token");
        var resp = handler.handleAuthentication(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        ErrorResponse body = resp.getBody();
        assertEquals("AUTH-401", body.getErrorCode());
    }

    @Test
    void handleDatabaseError_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("dup");
        var resp = handler.handleDatabaseError(ex);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        ErrorResponse body = resp.getBody();
        assertEquals("ORD-409", body.getErrorCode());
    }

    @Test
    void handleIllegalArgument_returns422() {
        var resp = handler.handleIllegalArgument(new IllegalArgumentException("x"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
        assertEquals("VAL-422", resp.getBody().getErrorCode());
    }

    @Test
    void handleIllegalState_returns500() {
        var resp = handler.handleIllegalState(new IllegalStateException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("SYS-500", resp.getBody().getErrorCode());
    }
}
