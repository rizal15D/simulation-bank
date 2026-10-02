package com.example.ledgerbank.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void safeClientCorrelationIdIsPresentInResponseAndMdcDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-request_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response, (incoming, outgoing) ->
                observed.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("client-request_123", observed.get());
        assertEquals("client-request_123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void unsafeClientValueIsReplacedRatherThanReflected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe value with spaces and\r\nnew line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (incoming, outgoing) -> { });

        String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertTrue(generated.matches("[0-9a-f-]{36}"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
