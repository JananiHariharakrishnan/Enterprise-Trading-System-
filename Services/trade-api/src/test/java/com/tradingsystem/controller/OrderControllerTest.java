package com.tradingsystem.controller;

import com.tradingsystem.dto.response.OrderResponse;
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


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

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
    void placeOrder_validRequest_returnsCreated() throws Exception {
        OrderResponse resp = new OrderResponse();
        resp.setMessage("Order accepted");
        resp.setOrderId("0000-0000");

        when(orderService.placeOrder(any())).thenReturn(resp);

        String json = "{" +
            "\"accountId\":1001," +
            "\"symbol\":\"AAPL\"," +
            "\"side\":\"BUY\"," +
            "\"orderType\":\"LIMIT\"," +
            "\"quantity\":5," +
            "\"price\":190.00," +
            "\"idempotencyKey\":\"idem-12345678\"" +
            "}";

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Order accepted"));
    }

    @Test
    void placeOrder_invalidRequest_returns422() throws Exception {
        // Missing required fields (no price)
        String json = "{\"accountId\":1001,\"symbol\":\"AAPL\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":0,\"idempotencyKey\":\"idem-xx\"}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }
}
