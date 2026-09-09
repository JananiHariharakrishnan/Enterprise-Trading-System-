package com.tradingsystem.advice;

import com.tradingsystem.dto.response.ErrorResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.exception.InsufficientFundsException;
import com.tradingsystem.exception.InsufficientHoldingsException;
import com.tradingsystem.exception.InvalidOrderException;
import com.tradingsystem.exception.TradingAuthenticationException;
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
                "ACC-404",
                "Account not found"
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
                "ACC-403",
                "Account not active"
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
                "INS-404",
                "Instrument not found"
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
                "ORD-400",
                "Insufficient funds"
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
                "ORD-409",
                "Insufficient holdings"
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
                "ORD-409",
                "Duplicate order"
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
                "VAL-422",
                "Invalid input"
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
                "AUTH-401",
                "Unauthorised"
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