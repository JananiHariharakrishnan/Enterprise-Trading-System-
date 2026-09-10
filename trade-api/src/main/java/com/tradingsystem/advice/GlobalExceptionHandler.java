package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.*;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

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

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(
            InvalidOrderException ex) {

        log.warn(
                "Order rejected because of invalid input"
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getCode(),
                ex.getMessage()
        );
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        log.warn(
                "Request validation failed"
        );

        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VAL-422",
                "Invalid input"
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
                "VAL-422",
                "Invalid input"
        );
    }

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