package com.jobportal.dto;

import lombok.Data;

@Data
public class ResumeUploadDto {
    private Integer candidateId;
    // The file will be handled as MultipartFile in controller
}