package com.jobportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.dto.CreateJobDto;
import com.jobportal.dto.UpdateJobDto;
import com.jobportal.entity.Job;
import com.jobportal.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;

/** Same non-null field semantics for both transports; never mutates a cached read object. */
@Component
@RequiredArgsConstructor
public class JobInputMapper {
    private final ObjectMapper json;
    public Job create(CreateJobDto input) {
        Job result = new Job();
        result.setCompanyId(input.getCompanyId());
        return apply(result, json.convertValue(input, UpdateJobDto.class));
    }
    public Job create(Map<String, Object> input) { return create(json.convertValue(input, CreateJobDto.class)); }
    public Job update(Job current, UpdateJobDto input) {
        Job result = new Job();
        BeanUtils.copyProperties(current, result);
        return apply(result, input);
    }
    public Job update(Job current, Map<String, Object> input) { return update(current, json.convertValue(input, UpdateJobDto.class)); }
    private Job apply(Job job, UpdateJobDto input) {
        if (input.getTitle() != null) job.setTitle(input.getTitle());
        if (input.getDescription() != null) job.setDescription(input.getDescription());
        if (input.getSkillsRequired() != null) job.setSkillsRequired(input.getSkillsRequired());
        if (input.getSalaryRange() != null) job.setSalaryRange(input.getSalaryRange());
        if (input.getLocation() != null) job.setLocation(input.getLocation());
        if (input.getEmploymentType() != null) job.setEmploymentType(input.getEmploymentType());
        if (input.getCategory() != null) job.setCategory(input.getCategory());
        if (input.getStatus() != null) job.setStatus(input.getStatus());
        if (input.getMinSalary() != null) job.setMinSalary(input.getMinSalary());
        if (input.getMaxSalary() != null) job.setMaxSalary(input.getMaxSalary());
        if (input.getLatitude() != null) job.setLatitude(input.getLatitude());
        if (input.getLongitude() != null) job.setLongitude(input.getLongitude());
        if (input.getStartsAt() != null) job.setStartsAt(localTime(input.getStartsAt()));
        if (input.getEndsAt() != null) job.setEndsAt(localTime(input.getEndsAt()));
        return job;
    }
    private LocalDateTime localTime(String value) {
        return com.jobportal.time.UtcTime.parse(value);
    }
}
