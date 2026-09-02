package com.tradingsystem.domain.DTO.request;

import com.tradingsystem.domain.enums.OrderSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequestShouldPassValidation() {
        PlaceOrderRequest request = new PlaceOrderRequest();

        request.setAccountId(1L);
        request.setSymbol("AAPL");
        request.setSide(OrderSide.BUY);
        request.setQuantity(10);
        request.setPrice(new BigDecimal("150.00"));
        request.setIdempotencyKey("ORDER123");

        Set<ConstraintViolation<PlaceOrderRequest>> violations =
                validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void accountIdShouldBePositive() {
        PlaceOrderRequest request = validRequest();

        request.setAccountId(0L);

        assertFalse(validator.validate(request).isEmpty());

        request.setAccountId(-1L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void symbolShouldNotBeBlank() {
        PlaceOrderRequest request = validRequest();

        request.setSymbol(null);
        assertFalse(validator.validate(request).isEmpty());

        request.setSymbol("");
        assertFalse(validator.validate(request).isEmpty());

        request.setSymbol(" ");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void symbolShouldAllowMaximumTwentyCharacters() {
        PlaceOrderRequest request = validRequest();

        request.setSymbol("ABCDEFGHIJKLMNOPQRST");
        assertTrue(validator.validate(request).isEmpty());

        request.setSymbol("ABCDEFGHIJKLMNOPQRSTU");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void sideShouldNotBeNull() {
        PlaceOrderRequest request = validRequest();

        request.setSide(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void quantityShouldBePositive() {
        PlaceOrderRequest request = validRequest();

        request.setQuantity(null);
        assertFalse(validator.validate(request).isEmpty());

        request.setQuantity(0);
        assertFalse(validator.validate(request).isEmpty());

        request.setQuantity(-1);
        assertFalse(validator.validate(request).isEmpty());

        request.setQuantity(1);
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void priceShouldBePositive() {
        PlaceOrderRequest request = validRequest();

        request.setPrice(null);
        assertFalse(validator.validate(request).isEmpty());

        request.setPrice(BigDecimal.ZERO);
        assertFalse(validator.validate(request).isEmpty());

        request.setPrice(new BigDecimal("-1.00"));
        assertFalse(validator.validate(request).isEmpty());

        request.setPrice(new BigDecimal("0.01"));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyShouldHaveEightToOneHundredCharacters() {
        PlaceOrderRequest request = validRequest();

        request.setIdempotencyKey(null);
        assertFalse(validator.validate(request).isEmpty());

        request.setIdempotencyKey("1234567");
        assertFalse(validator.validate(request).isEmpty());

        request.setIdempotencyKey("12345678");
        assertTrue(validator.validate(request).isEmpty());

        request.setIdempotencyKey("1234567890".repeat(10));
        assertTrue(validator.validate(request).isEmpty());

        request.setIdempotencyKey("1234567890".repeat(10) + "1");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void idempotencyKeyShouldNotBeBlank() {
        PlaceOrderRequest request = validRequest();

        request.setIdempotencyKey("");
        assertFalse(validator.validate(request).isEmpty());

        request.setIdempotencyKey("        ");
        assertFalse(validator.validate(request).isEmpty());
    }

    private PlaceOrderRequest validRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest();

        request.setAccountId(1L);
        request.setSymbol("AAPL");
        request.setSide(OrderSide.BUY);
        request.setQuantity(1);
        request.setPrice(new BigDecimal("100.00"));
        request.setIdempotencyKey("12345678");

        return request;
    }
}