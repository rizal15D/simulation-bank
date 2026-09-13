package com.example.ledgerbank.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "Status aplikasi")
@RestController
public class HealthController {
    @Operation(summary = "Memeriksa status aplikasi", responses = {
            @ApiResponse(responseCode = "200", description = "Aplikasi berjalan: status UP", useReturnTypeSchema = true)})
    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
