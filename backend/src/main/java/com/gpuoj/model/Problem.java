package com.gpuoj.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("problems")
public class Problem {
    @Id
    private String id;
    private String title;
    private String difficulty;
    private String judgeType;
    private int timeLimitMs;
    private Integer vramLimitMb;
    private Double speedupThreshold;
    private String gitCommitHash;
    private OffsetDateTime syncedAt;
}
