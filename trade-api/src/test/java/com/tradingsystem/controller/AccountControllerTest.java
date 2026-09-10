package com.tradingsystem.controller;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.OrderService;
import com.tradingsystem.advice.GlobalExceptionHandler;
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
public class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AccountController controller;

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

    @Test
    void getAccount_returnsAccount() throws Exception {
        AccountResponse resp = new AccountResponse();
        resp.setId(1001L);
        resp.setAccountId("ACC-1001");
        resp.setHolderName("Test User");
        resp.setCashBalance(new BigDecimal("123.45"));
        resp.setLastUpdated(OffsetDateTime.now());

        when(accountService.getAccount(1001L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/accounts/1001").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Test User"))
                .andExpect(jsonPath("$.cashBalance").value(123.45));
    }

    @Test
    void getAccount_unknownAccount_returnsAcc404() throws Exception {
        when(accountService.getAccount(999L)).thenThrow(new AccountNotFoundException());

        mockMvc.perform(get("/api/v1/accounts/999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACC-404"));
    }
}
