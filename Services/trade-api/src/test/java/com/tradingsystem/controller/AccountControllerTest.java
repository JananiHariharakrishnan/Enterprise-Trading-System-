package com.tradingsystem.controller;

import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.OrderService;
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
import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AccountController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getAccount_returnsAccount() throws Exception {

        AccountResponse response =
                new AccountResponse();

        response.setId(1001L);
        response.setAccountId("ACC-1001");
        response.setHolderName("Test User");
        response.setCashBalance(
                new BigDecimal("123.45")
        );
        response.setLastUpdated(
                OffsetDateTime.now()
        );

        when(accountService.getAccount(1001L))
                .thenReturn(response);

        mockMvc.perform(
                get("/api/v1/accounts/1001")
                        .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.holderName")
                        .value("Test User")
        )
        .andExpect(
                jsonPath("$.cashBalance")
                        .value(123.45)
        );
    }

    @Test
    void getAccount_unknownAccount_returnsAcc404()
            throws Exception {

        when(accountService.getAccount(999L))
                .thenThrow(
                        new AccountNotFoundException()
                );

        mockMvc.perform(
                get("/api/v1/accounts/999")
                        .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isNotFound())
        .andExpect(
                jsonPath("$.errorCode")
                        .value("ACC-404")
        );
    }

    @Test
    void getBalance_returnsOk() throws Exception {

        BalanceResponse response =
                new BalanceResponse();

        response.setCashBalance(
                new BigDecimal("500")
        );

        when(accountService.getBalance(1001L))
                .thenReturn(response);

        mockMvc.perform(
                get("/api/v1/accounts/1001/balance")
                        .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.cashBalance")
                        .value(500)
        );
    }
}