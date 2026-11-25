package com.jobportal.controller;

import com.jobportal.entity.User.Role;
import com.jobportal.exception.ValidationException;
import org.springframework.security.access.AccessDeniedException;

import com.jobportal.entity.Company;
import com.jobportal.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Company", description = "Company profiles are public. Profile creation binds to a COMPANY user; writes require the owner or ADMIN. Only ADMIN may set approval.")
@RestController
@RequestMapping("/api/v1/companies")
@CrossOrigin(origins = "*")
public class CompanyController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private CompanyService companyService;

    @GetMapping
    public List<Company> getAllCompanies() {
        return companyService.getAllCompanies();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable Integer id) {
        Company company = companyService.getCompanyById(id);
        return ResponseEntity.ok(company);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Company> getCompanyByUserId(@PathVariable Integer userId) {
        access.requireCurrentUser(userId);
        Company company = companyService.getCompanyByUserId(userId);
        return ResponseEntity.ok(company);
    }

    @PostMapping
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        if (company.getId() != null) throw new ValidationException("New company must not specify an ID");
        company.setUserId(access.profileOwner(company.getUserId(), Role.COMPANY));
        if (!access.currentActor().isAdmin()) company.setApproved(false);
        Company savedCompany = companyService.saveCompany(company);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCompany); // Return 201
    }

    @PutMapping("/{id}")
    public ResponseEntity<Company> updateCompany(@PathVariable Integer id, @RequestBody Company companyDetails) {
        access.requireCompanyOwner(id);
        Company company = companyService.getCompanyById(id);
        if (companyDetails.getUserId() != null && !companyDetails.getUserId().equals(company.getUserId())) {
            throw new AccessDeniedException("Company ownership cannot be changed");
        }
        company.setCompanyName(companyDetails.getCompanyName());
        company.setIndustry(companyDetails.getIndustry());
        company.setAddress(companyDetails.getAddress());
        if (access.currentActor().isAdmin() && companyDetails.getApproved() != null)
            company.setApproved(companyDetails.getApproved());
        return ResponseEntity.ok(companyService.saveCompany(company));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Integer id) {
        access.requireCompanyOwner(id);
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build(); // Return 204
    }
}
