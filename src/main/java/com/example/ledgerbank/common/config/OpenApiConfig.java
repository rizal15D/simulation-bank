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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ledgerBankOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        components.addResponses("BadRequest", errorResponse("Request atau amount tidak valid."));
        components.addResponses("NotFound", errorResponse("Customer atau rekening tidak ditemukan."));
        components.addResponses("Conflict", errorResponse(
                "Konflik aturan bisnis: email sudah terdaftar, rekening tidak aktif, saldo tidak cukup, atau batas saldo terlampaui."));
        components.addResponses("InternalError", errorResponse("Kesalahan internal; detail server tidak dikirim ke client."));
        return new OpenAPI().components(components).info(new Info()
                .title("LedgerBank API")
                .version("v1")
                .description("""
                        Simulator core banking untuk pembelajaran. Mendukung customer, rekening IDR,
                        deposit, withdrawal, transfer internal atomik, dan histori transaksi.
                        Endpoint belum memakai authentication. Tombol Try it out menjalankan operasi
                        pada database aplikasi; setiap request uang yang berhasil membuat transaksi baru.
                        """));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}