package com.jobportal.controller;

import com.jobportal.dto.CreateUserDto;
import com.jobportal.dto.UpdateUserDto;
import jakarta.validation.Valid;
import com.jobportal.dto.response.UserResponseDto;
import com.jobportal.entity.User;
import com.jobportal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@io.swagger.v3.oas.annotations.tags.Tag(name = "User", description = "Users may read and update their own account without changing role. Listing, creating, deleting accounts, and changing roles require ADMIN.")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<UserResponseDto> getAllUsers() {
        access.requireAdmin();
        return userService.getAllUsers().stream()
            .map(UserResponseDto::from)
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireCurrentUser(id);
        User user = userService.getUserById(id);
        if (user != null) {
            return ResponseEntity.ok(UserResponseDto.from(user));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserDto createUserDto) {
        access.requireAdmin();
        User user = new User();
        user.setEmail(createUserDto.getEmail());
        user.setPassword(passwordEncoder.encode(createUserDto.getPassword())); // ENCODE PASSWORD
        user.setRole(User.Role.valueOf(createUserDto.getRole().toUpperCase()));
        User savedUser = userService.saveUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.from(savedUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(@jakarta.validation.constraints.Positive @PathVariable Integer id, @Valid @RequestBody UpdateUserDto userDetails) {
        access.requireCurrentUser(id);
        User user = userService.getUserById(id);
        if (user != null) {
            access.requireUnchangedRole(userDetails.getRole(), user.getRole());
            user.setEmail(userDetails.getEmail());
            // Only update password if provided and encode it
            if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
            }
            if (userDetails.getRole() != null) user.setRole(userDetails.getRole());
            return ResponseEntity.ok(UserResponseDto.from(userService.saveUser(user)));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireAdmin();
        User user = userService.getUserById(id);
        if (user != null) {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build(); // Return 204
        }
        return ResponseEntity.notFound().build();
    }
}