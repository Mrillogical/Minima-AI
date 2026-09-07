package com.minima-AI.analytics.common.security;

import com.minima-AI.analytics.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeaderAuthFilterTests {

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Mock
    JsonMapper jsonMapper;

    @InjectMocks
    HeaderAuthFilter filter;

    @BeforeEach
    void setUp() throws Exception {

        filter = new HeaderAuthFilter(jsonMapper);
        SecurityContextHolder.clearContext();

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
    }

    @Test
    void bypassesAuth_forActuatorPaths() throws Exception {

        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getHeader("X-User-Id");
    }

    @Test
    void bypassesAuth_forSwaggerPaths() throws Exception {

        when(request.getRequestURI()).thenReturn("/swagger-ui.html");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejects_whenHeaderMissing() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/analytics/1");
        when(request.getHeader("X-User-Id")).thenReturn(null);

        when(jsonMapper.writeValueAsString(any(ErrorResponse.class)))
                .thenReturn("""
                {"message":"Missing X-User-Id header"}
                """);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejects_whenHeaderBlank() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/analytics/1");
        when(request.getHeader("X-User-Id")).thenReturn("   ");

        when(jsonMapper.writeValueAsString(any(ErrorResponse.class)))
                .thenReturn("""
                {"message":"Missing X-User-Id header"}
                """);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejects_whenHeaderNotValidUuid() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/analytics/1");
        when(request.getHeader("X-User-Id")).thenReturn("not-a-uuid");

        when(jsonMapper.writeValueAsString(any(ErrorResponse.class)))
                .thenReturn("""
                {"message":"Invalid X-User-Id header"}
                """);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void authenticates_andContinuesChain_whenHeaderValid() throws Exception {
        UUID userId = UUID.randomUUID();

        when(request.getRequestURI()).thenReturn("/api/v1/analytics/1");
        when(request.getHeader("X-User-Id")).thenReturn(userId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void clearsSecurityContext_afterRequest() throws Exception {

        UUID userId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/analytics/1");
        when(request.getHeader("X-User-Id")).thenReturn(userId.toString());

        filter.doFilterInternal(request, response, filterChain);

        // filter clears context in `finally` â€” nothing should leak to next test/thread
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

