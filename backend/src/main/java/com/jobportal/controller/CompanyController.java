package com.jobportal.controller;

import com.jobportal.entity.Company;
import com.jobportal.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@CrossOrigin(origins = "*")
public class CompanyController {

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

    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Company> getCompanyByUserId(@PathVariable Integer userId) {
        // TODO: Add ownership check - users can only see their own company
        Company company = companyService.getCompanyByUserId(userId);
        return ResponseEntity.ok(company);
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PostMapping
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        Company savedCompany = companyService.saveCompany(company);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCompany); // Return 201
    }

    @PreAuthorize("hasRole('COMPANY')")
    @PutMapping("/{id}")
    public ResponseEntity<Company> updateCompany(@PathVariable Integer id, @RequestBody Company companyDetails) {
        // TODO: Add ownership check - companies can only update their own profile
        Company company = companyService.getCompanyById(id);
        company.setCompanyName(companyDetails.getCompanyName());
        company.setIndustry(companyDetails.getIndustry());
        company.setAddress(companyDetails.getAddress());
        // Note: approved status should only be changed by admins
        return ResponseEntity.ok(companyService.saveCompany(company));
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Integer id) {
        // TODO: Add ownership check
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build(); // Return 204
    }
}