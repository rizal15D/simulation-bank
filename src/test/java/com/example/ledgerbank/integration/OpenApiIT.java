package com.example.ledgerbank.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class OpenApiIT {
    @Autowired private Environment environment;
    @Autowired private ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void openApiDescribesAllBankingOperationsAndActualResponseContracts() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode document = json.readTree(response.body());
        assertTrue(document.path("openapi").asText().startsWith("3."));
        assertEquals("LedgerBank API", document.path("info").path("title").asText());
        Map<String, String> operations = Map.of(
                "/api/v1/health", "get",
                "/api/v1/customers", "post",
                "/api/v1/customers/{customerId}", "get",
                "/api/v1/accounts", "post",
                "/api/v1/accounts/{accountId}", "get",
                "/api/v1/customers/{customerId}/accounts", "get",
                "/api/v1/accounts/{accountId}/deposit", "post",
                "/api/v1/accounts/{accountId}/withdraw", "post",
                "/api/v1/accounts/{accountId}/transactions", "get",
                "/api/v1/transfers", "post");
        assertEquals(operations.size(), document.path("paths").size());
        operations.forEach((path, method) -> {
            JsonNode operation = document.path("paths").path(path).path(method);
            assertFalse(operation.isMissingNode(), path);
            assertFalse(operation.path("summary").asText().isBlank(), path);
            assertEquals(1, operation.path("tags").size(), path);
            String status = method.equals("post") ? "201" : "200";
            assertTrue(operation.path("responses").has(status), path + " must document HTTP " + status);
            assertTrue(operation.path("responses").path(status).has("content"), path);
        });
        JsonNode customerCreated = document.path("paths").path("/api/v1/customers")
                .path("post").path("responses").path("201");
        assertEquals("#/components/schemas/CustomerResponse", customerCreated.findValue("$ref").asText());
        JsonNode transfer = document.path("paths").path("/api/v1/transfers").path("post");
        assertEquals("#/components/responses/Conflict", transfer.path("responses").path("409").path("$ref").asText());
        JsonNode schemas = document.path("components").path("schemas");
        for (String property : List.of("code", "message", "timestamp")) {
            assertTrue(schemas.path("ApiError").path("properties").has(property), property);
        }
        assertEquals("Budi Santoso", schemas.path("CustomerCreateRequest").path("properties")
                .path("fullName").path("example").asText());
        assertTrue(schemas.path("AmountRequest").path("required").toString().contains("amount"));
        assertEquals("number", schemas.path("AmountRequest").path("properties").path("amount").path("type").asText());
        assertTrue(schemas.path("AmountRequest").path("properties").path("amount").path("exclusiveMinimum").asBoolean());
        assertEquals("number", schemas.path("TransferResponse").path("properties").path("amount").path("type").asText());
        assertEquals("number", schemas.path("AccountResponse").path("properties").path("balance").path("type").asText());
        JsonNode pagination = document.path("paths").path("/api/v1/accounts/{accountId}/transactions")
                .path("get").path("parameters");
        JsonNode size = null;
        for (JsonNode parameter : pagination) {
            if (parameter.path("name").asText().equals("size")) { size = parameter; }
        }
        assertNotNull(size);
        assertEquals("integer", size.path("schema").path("type").asText());
        assertEquals(1, size.path("schema").path("minimum").asInt());
        assertEquals(100, size.path("schema").path("maximum").asInt());
        assertEquals(20, size.path("schema").path("default").asInt());
    }

    @Test
    void swaggerUiAssetsAndConfigurationLoadTheLocalApiDefinition() throws Exception {
        HttpResponse<String> page = get("/swagger-ui.html");
        assertEquals(200, page.statusCode(), page.body());
        assertTrue(page.uri().getPath().endsWith("/swagger-ui/index.html"));
        assertTrue(page.body().contains("Swagger UI"));
        assertEquals(200, get("/swagger-ui/swagger-ui-bundle.js").statusCode());
        assertEquals(200, get("/swagger-ui/swagger-ui.css").statusCode());
        HttpResponse<String> config = get("/v3/api-docs/swagger-config");
        assertEquals(200, config.statusCode(), config.body());
        assertEquals("/v3/api-docs", json.readTree(config.body()).path("url").asText());
        assertFalse(config.body().contains("petstore"));
        HttpResponse<String> yaml = get("/v3/api-docs.yaml");
        assertEquals(200, yaml.statusCode());
        assertTrue(yaml.body().contains("LedgerBank API"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
        return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
