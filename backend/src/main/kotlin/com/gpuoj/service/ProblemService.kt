package com.gpuoj.service

import com.gpuoj.model.Problem
import com.gpuoj.repository.ProblemRepository
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.yaml.snakeyaml.Yaml
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

@Service
class ProblemService(
    private val problemRepo: ProblemRepository,
    private val template: R2dbcEntityTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.problems.git-repo-url:}")
    private lateinit var gitRepoUrl: String

    @Value("\${app.problems.local-path}")
    private lateinit var localProblemsPath: String

    fun listProblems(): Flux<Problem> = problemRepo.findAllOrdered()

    fun getProblem(id: String): Mono<Problem> =
        problemRepo.findById(id)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found")))

    fun syncProblems(): Mono<String> {
        return Mono.fromCallable {
            if (gitRepoUrl.isNotBlank()) pullGitRepo()
            scanAndUpsert()
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { count -> Mono.just("Synced $count problems") }
    }

    private fun pullGitRepo() {
        val repoDir = File(localProblemsPath)
        try {
            if (File(repoDir, ".git").exists()) {
                Git.open(repoDir).use { git ->
                    git.pull().call()
                    log.info("Pulled problems repo")
                }
            } else {
                repoDir.mkdirs()
                Git.cloneRepository()
                    .setURI(gitRepoUrl)
                    .setDirectory(repoDir)
                    .call()
                    .close()
                log.info("Cloned problems repo")
            }
        } catch (e: Exception) {
            log.error("Git operation failed", e)
            throw RuntimeException("Git sync failed: ${e.message}")
        }
    }

    private fun scanAndUpsert(): Int {
        val problemsRoot = Path.of(localProblemsPath)
        if (!Files.exists(problemsRoot)) return 0

        var count = 0
        for (dir in problemsRoot.toFile().listFiles(File::isDirectory) ?: emptyArray()) {
            val yamlFile = File(dir, "problem.yaml")
            if (!yamlFile.exists()) continue
            try {
                FileInputStream(yamlFile).use { fis ->
                    val data: Map<String, Any> = Yaml().load(fis)
                    val problem = Problem(
                        id = data["id"] as? String,
                        title = data["title"] as? String,
                        difficulty = ((data["difficulty"] as? String) ?: "MEDIUM").uppercase(),
                        judgeType = ((data["judge_type"] as? String) ?: "correctness").uppercase(),
                        timeLimitMs = (data["time_limit_ms"] as? Int) ?: 5000,
                        vramLimitMb = data["vram_limit_mb"] as? Int,
                        speedupThreshold = (data["speedup_threshold"] as? Number)?.toDouble(),
                        syncedAt = OffsetDateTime.now()
                    )
                    val exists = problemRepo.existsById(problem.id!!).block() ?: false
                    if (exists) {
                        problemRepo.save(problem).block()
                    } else {
                        template.insert(problem).block()
                    }
                    count++
                }
            } catch (e: Exception) {
                log.warn("Failed to parse {}: {}", dir.name, e.message)
            }
        }
        return count
    }
}
