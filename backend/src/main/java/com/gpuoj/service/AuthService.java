package com.gpuoj.service;

import com.gpuoj.dto.AuthResponse;
import com.gpuoj.dto.LoginRequest;
import com.gpuoj.dto.RegisterRequest;
import com.gpuoj.model.User;
import com.gpuoj.repository.UserRepository;
import com.gpuoj.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public Mono<AuthResponse> register(RegisterRequest req) {
        return userRepo.existsByUsername(req.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Username taken"));
                    }
                    return userRepo.existsByEmail(req.getEmail());
                })
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Email taken"));
                    }
                    User user = new User();
                    user.setUsername(req.getUsername());
                    user.setEmail(req.getEmail());
                    user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
                    user.setRole("USER");
                    return userRepo.save(user);
                })
                .map(user -> new AuthResponse(
                        jwtUtil.generate(user.getUsername(), user.getRole()),
                        user.getUsername(),
                        user.getRole()
                ));
    }

    public Mono<AuthResponse> login(LoginRequest req) {
        return userRepo.findByUsername(req.getUsername())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
                    }
                    return Mono.just(new AuthResponse(
                            jwtUtil.generate(user.getUsername(), user.getRole()),
                            user.getUsername(),
                            user.getRole()
                    ));
                });
    }
}
