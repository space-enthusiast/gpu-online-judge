package com.gpuoj.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMessage {
    private String submissionId;
    private String problemId;
    private String sourceCode;        // base64-encoded
    private String testCasesPath;
    private String judgeType;
    private int timeLimitMs;
    private Integer vramLimitMb;
    private Double speedupThreshold;
}
