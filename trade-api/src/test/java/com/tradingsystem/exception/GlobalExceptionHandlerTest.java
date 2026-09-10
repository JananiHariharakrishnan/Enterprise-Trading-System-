package com.tradingsystem.exception;

import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();


    @Test
    void accountNotFound_mapsToAcc404() {

        ResponseEntity<ErrorResponse> response =
                handler.handleAccountNotFound(
                        new AccountNotFoundException()
                );

        assertError(
                response,
                HttpStatus.NOT_FOUND,
                "ACC-404",
                "Account not found"
        );
    }


    @Test
    void accountNotActive_mapsToAcc403() {

        ResponseEntity<ErrorResponse> response =
                handler.handleAccountNotActive(
                        new AccountNotActiveException()
                );

        assertError(
                response,
                HttpStatus.FORBIDDEN,
                "ACC-403",
                "Account not active"
        );
    }


    @Test
    void instrumentNotFound_mapsToIns404() {

        ResponseEntity<ErrorResponse> response =
                handler.handleInstrumentNotFound(
                        new InstrumentNotFoundException()
                );

        assertError(
                response,
                HttpStatus.NOT_FOUND,
                "INS-404",
                "Instrument not found"
        );
    }


    @Test
    void insufficientFunds_mapsToOrd400() {

        ResponseEntity<ErrorResponse> response =
                handler.handleInsufficientFunds(
                        new InsufficientFundsException()
                );

        assertError(
                response,
                HttpStatus.BAD_REQUEST,
                "ORD-400",
                "Insufficient funds"
        );
    }


    @Test
    void insufficientHoldings_mapsToOrd409() {

        ResponseEntity<ErrorResponse> response =
                handler.handleInsufficientHoldings(
                        new InsufficientHoldingsException()
                );

        assertError(
                response,
                HttpStatus.CONFLICT,
                "ORD-409",
                "Insufficient holdings"
        );
    }


    @Test
    void duplicateOrder_mapsToOrd409() {

        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateOrder(
                        new DuplicateOrderException()
                );

        assertError(
                response,
                HttpStatus.CONFLICT,
                "ORD-409",
                "Duplicate order"
        );
    }


    @Test
    void invalidOrder_mapsToVal422() {

        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidOrder(
                        new InvalidOrderException()
                );

        assertError(
                response,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VAL-422",
                "Invalid order"
        );
    }


    @Test
    void authenticationFailure_mapsToAuth401() {

        ResponseEntity<ErrorResponse> response =
                handler.handleAuthentication(
                        new TradingAuthenticationException()
                );

        assertError(
                response,
                HttpStatus.UNAUTHORIZED,
                "AUTH-401",
                "Unauthorised"
        );
    }


    @Test
    void errorResponse_doesNotExposeInternalDetails() {

        ResponseEntity<ErrorResponse> response =
                handler.handleAccountNotFound(
                        new AccountNotFoundException()
                );

        String body =
                response.getBody().getMessage();

        assertFalse(
                body.contains("Exception")
        );

        assertFalse(
                body.contains("SELECT")
        );

        assertFalse(
                body.contains("accountId")
        );

        assertFalse(
                body.contains("12345")
        );
    }


    private void assertError(
            ResponseEntity<ErrorResponse> response,
            HttpStatus status,
            String code,
            String message) {

        assertEquals(
                status,
                response.getStatusCode()
        );

        assertNotNull(
                response.getBody()
        );

        assertEquals(
                code,
                response.getBody().getErrorCode()
        );

        assertEquals(
                message,
                response.getBody().getMessage()
        );
    }
}