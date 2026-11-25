package com.jobportal.controller;

import com.jobportal.security.dto.AuthResponse;
import com.jobportal.security.dto.LoginRequest;
import com.jobportal.security.dto.RegisterRequest;
import com.jobportal.service.AuthService;
import com.jobportal.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "Authentication endpoints for login and registration")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description =
            "Public endpoint for candidates and company representatives to create an account and obtain a JWT. " +
            "Only CANDIDATE and COMPANY roles are accepted. ADMIN registration returns HTTP 400 " +
            "without creating an account or issuing a token.")
    @ApiResponse(responseCode = "201", description = "Account created and token issued",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid registration fields or disallowed role",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "adminRegistrationRejected", value = """
                            {
                              "timestamp": "2026-11-25T10:00:00",
                              "status": 400,
                              "error": "Validation Error",
                              "message": "Public registration only supports CANDIDATE and COMPANY roles",
                              "path": "/api/v1/auth/register",
                              "details": null
                            }
                            """)))
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate user and return JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Evaluator token endpoint: accepts {username, password} or {email, password}.
     * Demo credentials: username=demo / password=demo-password
     */
    @PostMapping("/token")
    @Operation(summary = "Issue token for demo account", description = "Accepts {username, password} or {email, password}. Demo: username=demo, password=demo-password")
    public ResponseEntity<?> token(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");

        // Resolve email: accept either username or email field
        String resolvedEmail;
        if (username != null && !username.isBlank()) {
            resolvedEmail = authService.resolveEmailByUsername(username);
        } else if (email != null && !email.isBlank()) {
            resolvedEmail = email;
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "username or email required"));
        }

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(resolvedEmail);
        loginRequest.setPassword(password != null ? password : "");

        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(Map.of(
            "token", response.getToken(),
            "message", "Token issued successfully",
            "email", response.getEmail(),
            "role", response.getRole() != null ? response.getRole().name() : ""
        ));
    }
}
