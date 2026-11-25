package com.jobportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.entity.User;
import com.jobportal.exception.GlobalExceptionHandler;
import com.jobportal.repository.UserRepository;
import com.jobportal.security.JwtUtil;
import com.jobportal.security.dto.RegisterRequest;
import com.jobportal.service.AuthService;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises request binding, validation, the real registration service, and error mapping. */
@ExtendWith(MockitoExtension.class)
class AuthControllerRegistrationTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminRegistrationReturns400WithoutCreatingAccountOrToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(User.Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value(
                        "Public registration only supports CANDIDATE and COMPANY roles"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.token").doesNotExist());

        verifyNoInteractions(userRepository, passwordEncoder, jwtUtil);
    }

    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = {"CANDIDATE", "COMPANY"})
    void publicRolesCreateAccountsAndReceiveTokens(User.Role role) throws Exception {
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });
        when(jwtUtil.generateToken("new@example.com", role.name())).thenReturn("test-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(role, savedUser.getValue().getRole());
        assertEquals("new@example.com", savedUser.getValue().getEmail());
        assertEquals("encoded-password", savedUser.getValue().getPassword());
    }

    @Test
    void missingRoleReturns400WithoutCreatingAccountOrToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new@example.com", "password", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value("role: Role is required"));

        verifyNoInteractions(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void existingAdminCanStillLogIn() throws Exception {
        User admin = new User();
        admin.setId(1);
        admin.setEmail("admin@example.com");
        admin.setPassword("encoded-password");
        admin.setRole(User.Role.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(admin);
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken("admin@example.com", "ADMIN")).thenReturn("admin-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin@example.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("admin-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void swaggerRegistrationSchemaOnlyListsPublicRoles() {
        Schema<?> schema = ModelConverters.getInstance().read(RegisterRequest.class).get("RegisterRequest");
        assertEquals(List.of("CANDIDATE", "COMPANY"), schema.getProperties().get("role").getEnum());
    }

    private String registrationBody(User.Role role) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", "new@example.com",
                "password", "password123",
                "role", role.name()));
    }
}
