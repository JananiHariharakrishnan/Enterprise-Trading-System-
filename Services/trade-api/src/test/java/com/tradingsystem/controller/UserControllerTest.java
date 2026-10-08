package com.tradingsystem.controller;

import com.tradingsystem.model.User;
import com.tradingsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    @Test
    void getAvailableAccount_foundAndMissing() throws Exception {
        when(userService.getAvailableAccountId()).thenReturn(42L);
        mockMvc.perform(get("/api/v1/users/available-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(42));

        when(userService.getAvailableAccountId()).thenReturn(null);
        mockMvc.perform(get("/api/v1/users/available-account"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_createdConflictAndNullLookup() throws Exception {
        User user = sampleUser();
        when(userService.getUserByUsername("ada")).thenReturn(user);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"accountId\":3,\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ada"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phoneNo").value("555"))
                .andExpect(jsonPath("$.accountId").value(3))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));

        verify(userService).createUser(eq("ada"), eq(3L), any(String[].class));

        doThrow(new IllegalStateException("taken")).when(userService).createUser(any(), any(), any());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"accountId\":3,\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.doNothing().when(userService).createUser(any(), any(), any());
        when(userService.getUserByUsername("ada")).thenReturn(null);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"accountId\":3,\"roles\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getByIdUsernameAndAccount_foundAndMissing() throws Exception {
        User user = sampleUser();
        UUID id = user.getId();
        when(userService.getUserById(id)).thenReturn(user);
        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        when(userService.getUserById(id)).thenReturn(null);
        mockMvc.perform(get("/api/v1/users/" + id)).andExpect(status().isNotFound());

        when(userService.getUserByUsername("ada")).thenReturn(user);
        mockMvc.perform(get("/api/v1/users/by-username/ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ada"));

        when(userService.getUserByUsername("missing")).thenReturn(null);
        mockMvc.perform(get("/api/v1/users/by-username/missing")).andExpect(status().isNotFound());

        when(userService.getUserByAccountId(3L)).thenReturn(user);
        mockMvc.perform(get("/api/v1/users/by-account/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(3));

        when(userService.getUserByAccountId(9L)).thenReturn(null);
        mockMvc.perform(get("/api/v1/users/by-account/9")).andExpect(status().isNotFound());
    }

    @Test
    void updateProfile_successAndFailure() throws Exception {
        when(userService.updateUserProfile(any(), eq("Ada"), eq("Lovelace"), eq("ada@example.com"), eq("555")))
                .thenReturn(sampleUser());

        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(put("/api/v1/users/" + id + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@example.com\",\"phoneNo\":\"555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        when(userService.updateUserProfile(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("write failed"));
        mockMvc.perform(put("/api/v1/users/" + id + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.c\",\"phoneNo\":\"1\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteOrphanCredentials_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/users/orphan-credentials"))
                .andExpect(status().isOk());
        verify(userService).deleteOrphanCredentials();
    }

    private static User sampleUser() {
        User user = new User();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setAccountId(3L);
        user.setUsername("ada");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.com");
        user.setPhoneNo("555");
        user.setRoles(List.of("CUSTOMER"));
        user.setCreatedOn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
