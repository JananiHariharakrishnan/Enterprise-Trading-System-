package com.tradingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.request.WalletDepositRequest;
import com.tradingsystem.dto.response.WalletDepositResponse;
import com.tradingsystem.exception.InvalidWalletPasswordException;
import com.tradingsystem.model.Account;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void getWalletDetails_authenticated_returnsAccount() throws Exception {
        AuthContextHolder.setAccountId(1001L);

        Account account = new Account();
        account.setAccountId(1001L);
        account.setAccountReference("ETP-1001");
        account.setCashBalance(new BigDecimal("15000.00"));
        account.setBuyingPower(new BigDecimal("15000.00"));
        account.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(walletService.getWalletDetails(1001L)).thenReturn(account);

        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001L))
                .andExpect(jsonPath("$.accountReference").value("ETP-1001"))
                .andExpect(jsonPath("$.cashBalance").value(15000.00));
    }

    @Test
    void getWalletDetails_unauthenticated_returnsUnauthorized() throws Exception {
        AuthContextHolder.clear();

        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));

        verifyNoInteractions(walletService);
    }

    @Test
    void depositFunds_validRequest_returnsDepositResponse() throws Exception {
        AuthContextHolder.setAccountId(1001L);

        WalletDepositRequest req = new WalletDepositRequest(new BigDecimal("500.00"), "secretPass");
        WalletDepositResponse resp = new WalletDepositResponse(
                1001L, "ETP-1001", new BigDecimal("1000.00"),
                new BigDecimal("500.00"), new BigDecimal("1500.00"),
                new BigDecimal("1500.00"), "Funds deposited successfully."
        );

        when(walletService.depositFunds(eq(1001L), any(WalletDepositRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/wallet/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001L))
                .andExpect(jsonPath("$.depositedAmount").value(500.00))
                .andExpect(jsonPath("$.newCashBalance").value(1500.00));
    }

    @Test
    void depositFunds_invalidPassword_returnsUnauthorized() throws Exception {
        AuthContextHolder.setAccountId(1001L);

        WalletDepositRequest req = new WalletDepositRequest(new BigDecimal("500.00"), "wrongPass");
        when(walletService.depositFunds(eq(1001L), any(WalletDepositRequest.class)))
                .thenThrow(new InvalidWalletPasswordException());

        mockMvc.perform(post("/api/v1/wallet/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }
}
