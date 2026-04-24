package com.gpuoj.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultMessage {
    private String submissionId;
    private String verdict;
    private String stdout;
    private String stderr;
    private Integer wallTimeMs;
    private Integer peakVramMb;
    private Double speedup;
    private String compileError;
}
