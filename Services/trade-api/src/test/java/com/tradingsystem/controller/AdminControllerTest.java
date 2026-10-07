package com.tradingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.advice.GlobalExceptionHandler;
import com.tradingsystem.dto.request.CreateAccountRequest;
import com.tradingsystem.dto.request.UpdateStatusRequest;
import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.AdminUserSummary;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.model.Account;
import com.tradingsystem.security.AuthContextHolder;
import com.tradingsystem.service.AdminService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController controller;

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
    void getDashboardStats_asAdmin_returnsStats() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        AdminDashboardStats stats = new AdminDashboardStats(
                new BigDecimal("5000000.00"), 20, 18, 1, 1, 15, 5
        );
        when(adminService.getDashboardStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMoney").value(5000000.00))
                .andExpect(jsonPath("$.openAccounts").value(18))
                .andExpect(jsonPath("$.suspendedAccounts").value(1))
                .andExpect(jsonPath("$.closedAccounts").value(1))
                .andExpect(jsonPath("$.totalUsers").value(15))
                .andExpect(jsonPath("$.unmappedAccounts").value(5));
    }

    @Test
    void getDashboardStats_asCustomer_returnsForbidden() throws Exception {
        AuthContextHolder.setAccountId(1001L);
        AuthContextHolder.setRoles(List.of("CUSTOMER"));

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));

        verifyNoInteractions(adminService);
    }

    @Test
    void getAllUsers_asAdmin_returnsUserList() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        AdminUserSummary user = new AdminUserSummary();
        user.setUserId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setAccountReference("ETP-1001");
        user.setAccountStatus("ACTIVE");
        user.setCashBalance(new BigDecimal("10000.00"));
        user.setRoles(List.of("CUSTOMER"));

        when(adminService.getAllUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("john_doe"))
                .andExpect(jsonPath("$[0].accountReference").value("ETP-1001"))
                .andExpect(jsonPath("$[0].accountStatus").value("ACTIVE"));
    }

    @Test
    void getAllUsers_asNonAdmin_returnsForbidden() throws Exception {
        AuthContextHolder.setAccountId(1001L);
        AuthContextHolder.setRoles(List.of("CUSTOMER"));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminService);
    }

    @Test
    void getUnassignedAndDormantAccounts_asAdmin_returnsList() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        UnassignedAccountSummary summary = new UnassignedAccountSummary();
        summary.setAccountId(500L);
        summary.setAccountReference("ETP-22099");
        summary.setCashBalance(new BigDecimal("100000.00"));
        summary.setStatus("ACTIVE");
        summary.setUnassigned(true);

        when(adminService.getUnassignedAndDormantAccounts()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/admin/accounts/unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(500L))
                .andExpect(jsonPath("$[0].accountReference").value("ETP-22099"))
                .andExpect(jsonPath("$[0].unassigned").value(true));
    }

    @Test
    void updateUserStatus_asAdmin_updatesAndReturnsNoContent() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        UUID userId = UUID.randomUUID();
        UpdateStatusRequest req = new UpdateStatusRequest("SUSPENDED");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(adminService).updateUserAccountStatus(eq(userId), eq("SUSPENDED"));
    }

    @Test
    void updateAccountStatus_asAdmin_updatesAndReturnsNoContent() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        UpdateStatusRequest req = new UpdateStatusRequest("CLOSED");

        mockMvc.perform(put("/api/v1/admin/accounts/{accountId}/status", 1002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(adminService).updateAccountStatus(eq(1002L), eq("CLOSED"));
    }

    @Test
    void createUnmappedAccount_asAdmin_returnsCreated() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        CreateAccountRequest req = new CreateAccountRequest("ETP-POOL-100", new BigDecimal("50000.00"));
        Account created = new Account();
        created.setAccountId(999L);
        created.setAccountReference("ETP-POOL-100");
        created.setCashBalance(new BigDecimal("50000.00"));
        created.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(adminService.createUnmappedAccount(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(999L))
                .andExpect(jsonPath("$.accountReference").value("ETP-POOL-100"));
    }

    @Test
    void createBatchAccounts_asAdmin_returnsCreatedList() throws Exception {
        AuthContextHolder.setAccountId(1L);
        AuthContextHolder.setRoles(List.of("ADMIN"));

        CreateAccountRequest req = new CreateAccountRequest(5, new BigDecimal("100000.00"));
        Account a1 = new Account();
        a1.setAccountId(101L);
        a1.setAccountReference("ETP-22020");
        a1.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(adminService.createBatchAccounts(eq(5), any())).thenReturn(List.of(a1));

        mockMvc.perform(post("/api/v1/admin/accounts/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].accountId").value(101L))
                .andExpect(jsonPath("$[0].accountReference").value("ETP-22020"));
    }
}
