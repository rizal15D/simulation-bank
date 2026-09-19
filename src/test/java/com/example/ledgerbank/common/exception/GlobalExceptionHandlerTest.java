package com.example.ledgerbank.common.exception;

import com.example.ledgerbank.auth.BearerTokenAuthenticationFilter;
import com.example.ledgerbank.common.HealthController;
import com.example.ledgerbank.customer.CustomerController;
import com.example.ledgerbank.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CustomerController.class, HealthController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.PrincipalResolverConfiguration.class)
class GlobalExceptionHandlerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CustomerService customers;
    @MockitoBean private BearerTokenAuthenticationFilter bearerTokens;

    @TestConfiguration
    static class PrincipalResolverConfiguration implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Test
    void routingMethodAndMediaErrorsKeepTheirHttpStatuses() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
        mvc.perform(delete("/api/v1/customers/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(post("/api/v1/customers").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidJsonValidationAndPathParametersReturnBadRequest() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/customers/invalid-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void businessExceptionsRetainTheirDocumentedCodeAndStatus() throws Exception {
        UUID missing = UUID.randomUUID();
        when(customers.get(null, missing)).thenThrow(new BusinessException(
                "CUSTOMER_NOT_FOUND", "Customer was not found", HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/customers/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Customer was not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedFailuresReturnSafeServerErrorWithoutInternalDetails() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customers.get(null, customerId)).thenThrow(new IllegalStateException("internal database connection detail"));
        mvc.perform(get("/api/v1/customers/" + customerId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("internal database connection detail"))));
    }
}
