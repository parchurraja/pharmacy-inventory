package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.AuthDTO;
import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<MedicineDTO.ApiResponse<AuthDTO.AuthResponse>> login(
            @Valid @RequestBody AuthDTO.LoginRequest request) {
        AuthDTO.AuthResponse response = authService.login(request);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Authentication successful", response));
    }

    @PostMapping("/register")
    public ResponseEntity<MedicineDTO.ApiResponse<AuthDTO.AuthResponse>> register(
            @Valid @RequestBody AuthDTO.RegisterRequest request) {
        AuthDTO.AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success("User registered successfully", response));
    }
}
