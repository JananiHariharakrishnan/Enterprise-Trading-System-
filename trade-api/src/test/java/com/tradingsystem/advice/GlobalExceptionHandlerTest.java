package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.DuplicateOrderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAccountNotFound_returns404() {
        AccountNotFoundException ex = new AccountNotFoundException();
        var resp = handler.handleAccountNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        ErrorResponse body = resp.getBody();
        assertEquals("ACC-404", body.getErrorCode());
    }

    @Test
    void handleDuplicateOrder_returnsConflict() {
        DuplicateOrderException ex = new DuplicateOrderException();
        var resp = handler.handleDuplicateOrder(ex);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        ErrorResponse body = resp.getBody();
        assertEquals("ORD-409", body.getErrorCode());
    }
}
