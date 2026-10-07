package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.*;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR_CODE = "VAL-422";
    private static final String INVALID_INPUT_MESSAGE = "Invalid input";

    // =========================================================
    // ACCOUNT EXCEPTIONS
    // =========================================================

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex) {

        log.warn(
                "Account not found: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(
            AccountNotActiveException ex) {

        log.warn(
                "Account access rejected: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.FORBIDDEN,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // INSTRUMENT EXCEPTIONS
    // =========================================================

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(
            InstrumentNotFoundException ex) {

        log.warn(
                "Instrument lookup failed: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // WATCHLIST EXCEPTIONS
    // =========================================================

    @ExceptionHandler(WatchlistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWatchlistNotFound(
            WatchlistNotFoundException ex) {

        log.warn(
                "Watchlist lookup failed: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(WatchlistConflictException.class)
    public ResponseEntity<ErrorResponse> handleWatchlistConflict(
            WatchlistConflictException ex) {

        log.warn(
                "Watchlist conflict: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // ORDER BUSINESS EXCEPTIONS
    // =========================================================

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex) {

        log.warn(
                "Order rejected because of insufficient funds"
        );

        return error(
                HttpStatus.BAD_REQUEST,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(
            InsufficientHoldingsException ex) {

        log.warn(
                "Order rejected because of insufficient holdings"
        );

        return error(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(
            DuplicateOrderException ex) {

        log.warn(
                "Order rejected because of duplicate idempotency key"
        );

        return error(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(OrderStateConflictException.class)
    public ResponseEntity<ErrorResponse> handleOrderStateConflict(
            OrderStateConflictException ex) {

        log.warn(
                "Order state conflict: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex) {

        log.warn(
                "Order not found: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.NOT_FOUND,
                "ORD-404",
                "Order not found"
        );
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(
            InvalidOrderException ex) {

        log.warn(
                "Order rejected because of invalid input: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    @ExceptionHandler(TradingAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            TradingAuthenticationException ex) {

        log.warn(
                "Authentication failed: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.UNAUTHORIZED,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // BEAN VALIDATION
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        log.warn(
                "Request validation failed"
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                VALIDATION_ERROR_CODE,
                INVALID_INPUT_MESSAGE
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        log.warn(
                "Request constraint validation failed"
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                VALIDATION_ERROR_CODE,
                INVALID_INPUT_MESSAGE
        );
    }

    // =========================================================
    // INVALID / MALFORMED REQUEST BODY
    // =========================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex) {

        log.warn(
                "Invalid request payload: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                VALIDATION_ERROR_CODE,
                INVALID_INPUT_MESSAGE
        );
    }

    // =========================================================
    // ILLEGAL ARGUMENT
    // =========================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn(
                "Invalid argument: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                VALIDATION_ERROR_CODE,
                INVALID_INPUT_MESSAGE
        );
    }

    // =========================================================
    // ILLEGAL STATE
    // =========================================================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex) {

        log.error(
                "Illegal state: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SYS-500",
                "Internal server error"
        );
    }

    // =========================================================
    // DATABASE CONSTRAINT VIOLATIONS
    // =========================================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(
            DataIntegrityViolationException ex) {

        log.warn(
                "Database constraint violation: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.CONFLICT,
                "ORD-409",
                "Order already exists or violates a database constraint"
        );
    }

    // =========================================================
    // PORTFOLIO MODULE (portfolio-api.yaml)
    // =========================================================

    @ExceptionHandler(PricingUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePricingUnavailable(
            PricingUnavailableException ex) {

        log.warn("Pricing unavailable: {}", ex.getMessage());

        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccountAccessDenied(
            AccountAccessDeniedException ex) {

        log.warn("Account access denied: {}", ex.getMessage());

        return error(
                HttpStatus.FORBIDDEN,
                ex.getCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidPortfolioInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPortfolioInput(
            InvalidPortfolioInputException ex) {

        log.warn("Invalid portfolio input: {}", ex.getMessage());

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // TRADING DOMAIN EXCEPTION
    // =========================================================

    @ExceptionHandler(TradingDomainException.class)
    public ResponseEntity<ErrorResponse> handleTradingDomain(
            TradingDomainException ex) {

        log.error(
                "Unhandled trading domain exception: {}",
                ex.getMessage()
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getCode(),
                ex.getMessage()
        );
    }

    // =========================================================
    // GENERIC FALLBACK
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex) {

        log.error(
                "Unexpected internal server error",
                ex
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SYS-500",
                "Internal server error"
        );
    }

    // =========================================================
    // COMMON ERROR RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String errorCode,
            String message) {

        return ResponseEntity
                .status(status)
                .body(
                        new ErrorResponse(
                                errorCode,
                                message
                        )
                );
    }
}