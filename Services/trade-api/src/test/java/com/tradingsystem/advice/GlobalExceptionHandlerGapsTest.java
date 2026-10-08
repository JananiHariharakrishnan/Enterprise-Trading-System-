package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.AccountAccessDeniedException;
import com.tradingsystem.exception.AdminAccessDeniedException;
import com.tradingsystem.exception.AlertLimitExceededException;
import com.tradingsystem.exception.AlertNotFoundException;
import com.tradingsystem.exception.AlertStateException;
import com.tradingsystem.exception.InvalidPortfolioInputException;
import com.tradingsystem.exception.InvalidWalletPasswordException;
import com.tradingsystem.exception.OrderNotFoundException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.exception.PricingUnavailableException;
import com.tradingsystem.exception.TradingDomainException;
import com.tradingsystem.exception.WatchlistConflictException;
import com.tradingsystem.exception.WatchlistNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerGapsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleWatchlistAndAlertExceptions() {
        assertError(handler.handleWatchlistNotFound(new WatchlistNotFoundException()),
                HttpStatus.NOT_FOUND, "WAT-404", "Watchlist not found");
        assertError(handler.handleWatchlistConflict(new WatchlistConflictException("duplicate list")),
                HttpStatus.CONFLICT, "WAT-409", "duplicate list");
        assertError(handler.handleAlertNotFound(new AlertNotFoundException()),
                HttpStatus.NOT_FOUND, "ALT-404", "Alert not found");
        assertError(handler.handleAlertStateException(new AlertStateException("bad transition")),
                HttpStatus.CONFLICT, "ALT-409", "bad transition");
        assertError(handler.handleAlertLimitExceeded(new AlertLimitExceededException(5)),
                HttpStatus.BAD_REQUEST, "ALT-400", "Maximum active alerts limit (5) exceeded for this account");
    }

    @Test
    void handleOrderStateAndValidationExceptions() {
        assertError(handler.handleOrderStateConflict(new OrderStateConflictException("busy")),
                HttpStatus.CONFLICT, "ORD-409", "busy");
        assertError(handler.handleOrderNotFound(new OrderNotFoundException("hidden detail")),
                HttpStatus.NOT_FOUND, "ORD-404", "Order not found");

        MethodArgumentNotValidException invalid = mock(MethodArgumentNotValidException.class);
        when(invalid.getMessage()).thenReturn("field missing");
        assertError(handler.handleValidation(invalid),
                HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");

        assertError(handler.handleConstraintViolation(new ConstraintViolationException("bad", Collections.emptySet())),
                HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");

        HttpMessageNotReadableException unreadable = mock(HttpMessageNotReadableException.class);
        when(unreadable.getMessage()).thenReturn("malformed json");
        assertError(handler.handleInvalidJson(unreadable),
                HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    @Test
    void handlePortfolioAuthAndFallbackExceptions() {
        assertError(handler.handlePricingUnavailable(new PricingUnavailableException()),
                HttpStatus.SERVICE_UNAVAILABLE, "MKT-503", "Pricing unavailable");
        assertError(handler.handleAccountAccessDenied(new AccountAccessDeniedException()),
                HttpStatus.FORBIDDEN, "ACC-403", "Account not accessible");
        assertError(handler.handleAdminAccessDenied(new AdminAccessDeniedException()),
                HttpStatus.FORBIDDEN, "AUTH-403", "Access denied: Administrator privileges required");
        assertError(handler.handleInvalidWalletPassword(new InvalidWalletPasswordException()),
                HttpStatus.UNAUTHORIZED, "AUTH-401", "Password confirmation failed: Invalid password");
        assertError(handler.handleInvalidPortfolioInput(new InvalidPortfolioInputException()),
                HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");

        TradingDomainException domain = new TradingDomainException("DOM-500", "unmapped domain failure") {
        };
        assertError(handler.handleTradingDomain(domain),
                HttpStatus.INTERNAL_SERVER_ERROR, "DOM-500", "unmapped domain failure");

        assertError(handler.handleGenericException(new RuntimeException("secret stack")),
                HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500", "Internal server error");
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code, String message) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().getErrorCode());
        assertEquals(message, response.getBody().getMessage());
    }
}
