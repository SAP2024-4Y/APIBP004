package com.jobportal.service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import com.jobportal.entity.Company;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    @Cacheable(value = "companies")
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    @Cacheable(value = "companies", key = "#id")
    public Company getCompanyById(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Company ID must be a positive number");
        }
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Company not found with ID: " + id + ". Please verify the company ID."));
    }

    public Company getCompanyByUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
        
        Company company = companyRepository.findByUserId(userId);
        if (company == null) {
            throw new ResourceNotFoundException(
                "No company profile found for user ID: " + userId + ". Please create a company profile first.");
        }
        return company;
    }

    @CacheEvict(value = "companies", allEntries = true)
    public Company saveCompany(Company company) {
        validateCompany(company);
        
        try {
            return companyRepository.save(company);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to save company profile. Please try again later.", e);
        }
    }

    @CacheEvict(value = "companies", allEntries = true)
    public void deleteCompany(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Company ID must be a positive number");
        }
        
        getCompanyById(id); // This will throw ResourceNotFoundException if not found
        
        try {
            companyRepository.deleteById(id);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to delete company with ID: " + id + ". Please try again later.", e);
        }
    }
    
    private void validateCompany(Company company) {
        if (company == null) {
            throw new ValidationException("Company data cannot be null");
        }
        if (company.getUserId() == null || company.getUserId() <= 0) {
            throw new ValidationException("Valid user ID is required for company profile");
        }
        if (company.getCompanyName() == null || company.getCompanyName().trim().isEmpty()) {
            throw new ValidationException("Company name is required and cannot be empty");
        }
        if (company.getCompanyName().length() < 2) {
            throw new ValidationException("Company name must be at least 2 characters long");
        }
    }
}