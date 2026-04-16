package com.pch.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pch.auth.config.SecurityConfig;
import com.pch.auth.dto.LoginRequest;
import com.pch.auth.dto.SignupRequest;
import com.pch.auth.dto.TokenResponse;
import com.pch.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/register — 201 Created")
    void register_201() throws Exception {
        // given
        SignupRequest request = new SignupRequest("test@test.com", "Password1!", "홍길동");
        TokenResponse tokenResponse = new TokenResponse("access", "refresh", 1L, "test@test.com", "홍길동");
        given(authService.register(any(SignupRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login — 200 OK")
    void login_200() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@test.com", "Password1!");
        TokenResponse tokenResponse = new TokenResponse("access", "refresh", 1L, "test@test.com", "홍길동");
        given(authService.login(any(LoginRequest.class), anyString())).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    @DisplayName("POST /api/auth/register — 이메일 형식 오류 시 400")
    void register_invalid_email_400() throws Exception {
        // given
        SignupRequest request = new SignupRequest("not-an-email", "Password1!", "홍길동");

        // when & then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
