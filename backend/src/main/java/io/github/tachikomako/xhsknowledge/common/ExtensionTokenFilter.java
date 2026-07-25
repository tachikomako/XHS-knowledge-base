package io.github.tachikomako.xhsknowledge.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ExtensionTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public ExtensionTokenFilter(
            @Value("${xhs.extension-token}") String expectedToken,
            ObjectMapper objectMapper
    ) {
        if (expectedToken.isBlank()) {
            throw new IllegalStateException("xhs.extension-token must not be blank");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/imports/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] providedToken = request.getHeader("X-Extension-Token") == null
                ? new byte[0]
                : request.getHeader("X-Extension-Token").getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedToken, providedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), new ApiError(
                    "INVALID_EXTENSION_TOKEN",
                    "Extension token is missing or invalid",
                    UUID.randomUUID().toString(),
                    OffsetDateTime.now()
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
