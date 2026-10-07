package com.tradingsystem.controller;

import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.response.AlertResponse;
import com.tradingsystem.exception.AlertLimitExceededException;
import com.tradingsystem.exception.AlertNotFoundException;
import com.tradingsystem.exception.AlertStateException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.AlertService;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AlertService alertService;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private AlertController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        lenient().when(currentUser.getAccountId()).thenReturn(1001L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createAlert_validRequest_returnsCreated() throws Exception {
        AlertResponse response = new AlertResponse();
        response.setId(1L);
        response.setSymbol("AAPL");
        response.setDirection("BELOW");
        response.setThreshold(new BigDecimal("150.00"));
        response.setStatus("ACTIVE");
        response.setCreatedOn(OffsetDateTime.now());
        response.setUpdatedOn(OffsetDateTime.now());

        when(alertService.createAlert(eq(1001L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"BELOW\",\"threshold\":150.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.direction").value("BELOW"))
                .andExpect(jsonPath("$.threshold").value(150.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createAlert_missingSymbol_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"BELOW\",\"threshold\":150.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void createAlert_invalidDirection_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"INVALID\",\"threshold\":150.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void createAlert_negativeThreshold_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"BELOW\",\"threshold\":-50.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void createAlert_instrumentNotFound_returns404() throws Exception {
        when(alertService.createAlert(eq(1001L), any()))
                .thenThrow(new InstrumentNotFoundException());

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"INVALID\",\"direction\":\"BELOW\",\"threshold\":150.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    void listAlerts_returnsOk() throws Exception {
        AlertResponse response = new AlertResponse();
        response.setId(1L);
        response.setSymbol("AAPL");
        response.setDirection("BELOW");
        response.setThreshold(new BigDecimal("150.00"));
        response.setStatus("ACTIVE");

        when(alertService.listAlerts(1001L, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void listAlerts_filteredByStatus_returnsOk() throws Exception {
        AlertResponse response = new AlertResponse();
        response.setId(2L);
        response.setSymbol("MSFT");
        response.setDirection("ABOVE");
        response.setThreshold(new BigDecimal("380.00"));
        response.setStatus("TRIGGERED");

        when(alertService.listAlerts(1001L, "TRIGGERED")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/alerts?status=TRIGGERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].status").value("TRIGGERED"));
    }

    @Test
    void getAlert_validId_returnsOk() throws Exception {
        AlertResponse response = new AlertResponse();
        response.setId(1L);
        response.setSymbol("AAPL");
        response.setDirection("BELOW");
        response.setThreshold(new BigDecimal("150.00"));
        response.setStatus("ACTIVE");

        when(alertService.getAlert(1001L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/alerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void getAlert_invalidId_returns404() throws Exception {
        when(alertService.getAlert(1001L, 999L))
                .thenThrow(new AlertNotFoundException());

        mockMvc.perform(get("/api/v1/alerts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ALT-404"));
    }

    @Test
    void cancelAlert_validId_returnsNoContent() throws Exception {
        doNothing().when(alertService).cancelAlert(1001L, 1L);

        mockMvc.perform(delete("/api/v1/alerts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelAlert_invalidId_returns404() throws Exception {
        doThrow(new AlertNotFoundException())
                .when(alertService).cancelAlert(1001L, 999L);

        mockMvc.perform(delete("/api/v1/alerts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ALT-404"));
    }

    @Test
    void cancelAlert_alreadyCancelled_returns409() throws Exception {
        doThrow(new AlertStateException("Alert is already cancelled"))
                .when(alertService).cancelAlert(1001L, 1L);

        mockMvc.perform(delete("/api/v1/alerts/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALT-409"))
                .andExpect(jsonPath("$.message").value("Alert is already cancelled"));
    }

    @Test
    void createAlert_limitExceeded_returns400() throws Exception {
        when(alertService.createAlert(eq(1001L), any()))
                .thenThrow(new AlertLimitExceededException(50));

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"direction\":\"BELOW\",\"threshold\":150.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ALT-400"))
                .andExpect(jsonPath("$.message").value(containsString("Maximum active alerts limit")));
    }
}
