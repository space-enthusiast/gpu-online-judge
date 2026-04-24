package com.gpuoj.service;

import com.gpuoj.model.Problem;
import com.gpuoj.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepo;

    @Value("${app.problems.git-repo-url:}")
    private String gitRepoUrl;

    @Value("${app.problems.local-path}")
    private String localProblemsPath;

    public Flux<Problem> listProblems() {
        return problemRepo.findAll();
    }

    public Mono<Problem> getProblem(String id) {
        return problemRepo.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found")));
    }

    public Mono<String> syncProblems() {
        return Mono.fromCallable(() -> {
            // Pull from Git if URL is set, otherwise read from local path
            if (gitRepoUrl != null && !gitRepoUrl.isBlank()) {
                pullGitRepo();
            }
            return scanAndUpsert();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(count -> Mono.just("Synced " + count + " problems"));
    }

    private void pullGitRepo() {
        File repoDir = new File(localProblemsPath);
        try {
            if (new File(repoDir, ".git").exists()) {
                try (Git git = Git.open(repoDir)) {
                    git.pull().call();
                    log.info("Pulled problems repo");
                }
            } else {
                repoDir.mkdirs();
                Git.cloneRepository()
                        .setURI(gitRepoUrl)
                        .setDirectory(repoDir)
                        .call()
                        .close();
                log.info("Cloned problems repo");
            }
        } catch (Exception e) {
            log.error("Git operation failed", e);
            throw new RuntimeException("Git sync failed: " + e.getMessage());
        }
    }

    private int scanAndUpsert() throws IOException {
        Path problemsRoot = Path.of(localProblemsPath);
        if (!Files.exists(problemsRoot)) {
            return 0;
        }
        int count = 0;
        for (File dir : problemsRoot.toFile().listFiles(File::isDirectory)) {
            File yamlFile = new File(dir, "problem.yaml");
            if (!yamlFile.exists()) continue;
            try (FileInputStream fis = new FileInputStream(yamlFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(fis);
                Problem p = new Problem();
                p.setId((String) data.get("id"));
                p.setTitle((String) data.get("title"));
                p.setDifficulty(((String) data.getOrDefault("difficulty", "MEDIUM")).toUpperCase());
                p.setJudgeType(((String) data.getOrDefault("judge_type", "correctness")).toUpperCase());
                p.setTimeLimitMs((Integer) data.getOrDefault("time_limit_ms", 5000));
                p.setVramLimitMb((Integer) data.get("vram_limit_mb"));
                Object threshold = data.get("speedup_threshold");
                if (threshold instanceof Number) {
                    p.setSpeedupThreshold(((Number) threshold).doubleValue());
                }
                p.setSyncedAt(OffsetDateTime.now());
                problemRepo.save(p).block();
                count++;
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", dir.getName(), e.getMessage());
            }
        }
        return count;
    }
}
