package com.jobportal.dto.response;

import com.jobportal.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for User entity
 * Excludes sensitive information like password
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Integer id;
    private String email;
    private String role;
    private LocalDateTime createdAt;

    public static UserResponseDto from(User user) {
        if (user == null) return null;
        return new UserResponseDto(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
