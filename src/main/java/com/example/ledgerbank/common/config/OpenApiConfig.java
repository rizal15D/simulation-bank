package com.example.ledgerbank.common.config;

import com.example.ledgerbank.common.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ledgerBankOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        components.addResponses("BadRequest", errorResponse("Request atau amount tidak valid."));
        components.addResponses("Unauthorized", errorResponse("Bearer token tidak ada, tidak valid, atau kedaluwarsa."));
        components.addResponses("Forbidden", errorResponse("Role atau ownership tidak mengizinkan operasi."));
        components.addResponses("NotFound", errorResponse("Customer atau rekening tidak ditemukan."));
        components.addResponses("Conflict", errorResponse(
                "Konflik aturan bisnis: email sudah terdaftar, rekening tidak aktif, saldo tidak cukup, atau batas saldo terlampaui."));
        components.addResponses("InternalError", errorResponse("Kesalahan internal; detail server tidak dikirim ke client."));
        components.addResponses("UnprocessableEntity", errorResponse("Request valid secara sintaks tetapi tidak dapat diproses."));
        components.addResponses("ServiceUnavailable", errorResponse("Redis atau dependency wajib sedang tidak tersedia."));
        components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("opaque Redis session token")
                .description("Token dari POST /api/v1/auth/login"));
        return new OpenAPI().components(components).info(new Info()
                .title("LedgerBank API")
                .version("v1")
                .description("""
                        Simulator core banking untuk pembelajaran. Endpoint banking memakai opaque
                        bearer token dari login. CUSTOMER hanya dapat mengakses rekening miliknya;
                        ADMIN memiliki akses baca operasional. Transfer wajib memakai Idempotency-Key.
                        """));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
