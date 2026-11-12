package com.jobportal.service;

import com.jobportal.entity.User;
import com.jobportal.exception.DuplicateResourceException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User not found with ID: " + id + ". Please check the user ID and try again."));
    }

    public User saveUser(User user) {
        validateUser(user);
        
        // Check for duplicate email
        if (user.getId() == null && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException(
                "A user with email '" + user.getEmail() + "' already exists. Please use a different email address.");
        }
        
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to save user. Please try again later.", e);
        }
    }

    public void deleteUser(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
        
        getUserById(id); // This will throw ResourceNotFoundException if not found
        
        try {
            userRepository.deleteById(id);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to delete user with ID: " + id + ". Please try again later.", e);
        }
    }

    public User findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email cannot be null or empty");
        }
        
        User user = userRepository.findByEmail(email.trim());
        if (user == null) {
            throw new ResourceNotFoundException(
                "No user found with email: " + email + ". Please check the email address.");
        }
        return user;
    }
    
    private void validateUser(User user) {
        if (user == null) {
            throw new ValidationException("User data cannot be null");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required and cannot be empty");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new ValidationException("Password is required and cannot be empty");
        }
        if (user.getRole() == null) {
            throw new ValidationException("User role is required");
        }
        if (!user.getEmail().contains("@")) {
            throw new ValidationException("Invalid email format. Email must contain '@' symbol");
        }
        if (user.getPassword().length() < 6) {
            throw new ValidationException("Password must be at least 6 characters long");
        }
    }
}