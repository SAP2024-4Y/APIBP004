package com.jobportal.service;

import com.jobportal.entity.User;
import com.jobportal.exception.DuplicateResourceException;
import com.jobportal.exception.UnauthorizedException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.UserRepository;
import com.jobportal.security.JwtUtil;
import com.jobportal.security.dto.AuthResponse;
import com.jobportal.security.dto.LoginRequest;
import com.jobportal.security.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() != User.Role.CANDIDATE && request.getRole() != User.Role.COMPANY) {
            throw new ValidationException(
                    "Public registration only supports CANDIDATE and COMPANY roles");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .message("User registered successfully")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail());
        if (user == null) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole())
                .userId(user.getId())
                .message("Login successful")
                .build();
    }

    /**
     * Resolve an email address from a username. The demo account uses username="demo"
     * which maps to email="demo@jobportal.com". For regular users, username IS the email.
     */
    public String resolveEmailByUsername(String username) {
        if ("demo".equalsIgnoreCase(username)) {
            return "demo@jobportal.com";
        }
        // treat username as email for other users
        return username;
    }
}
