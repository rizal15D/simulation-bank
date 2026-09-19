package com.example.ledgerbank.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Registrasi, login, dan penghentian session Redis")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthSessionService sessions;

    public AuthController(AuthService auth, AuthSessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @Operation(summary = "Registrasi customer", description = "Membuat customer dan user CUSTOMER. Password disimpan sebagai hash BCrypt.", responses = {
            @ApiResponse(responseCode = "201", description = "User dan customer dibuat", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")})
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = auth.register(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + user.customerId())).body(user);
    }

    @Operation(summary = "Login", description = "Mengembalikan opaque bearer token yang hanya disimpan dalam bentuk hash key di Redis.", responses = {
            @ApiResponse(responseCode = "200", description = "Login berhasil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")})
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @Operation(summary = "Logout", description = "Menghapus session bearer token dari Redis.",
            security = @SecurityRequirement(name = "bearerAuth"), responses = {
            @ApiResponse(responseCode = "204", description = "Session dihapus"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        sessions.delete(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().build();
    }
}
