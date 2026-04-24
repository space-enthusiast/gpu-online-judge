package com.gpuoj.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("submissions")
public class Submission {
    @Id
    private UUID id;
    private UUID userId;
    private String problemId;
    private String sourceCode;
    private String language = "CUDA";
    private String status = "PENDING";
    private String verdict;
    private Integer wallTimeMs;
    private Integer peakVramMb;
    private Double speedup;
    private String stdout;
    private String stderr;
    private OffsetDateTime submittedAt;
}
