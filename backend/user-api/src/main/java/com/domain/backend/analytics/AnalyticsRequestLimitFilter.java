package com.domain.backend.analytics;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound JSON allocation even for chunked requests, before deserialization. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AnalyticsRequestLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BYTES = 64 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST")
                || !request.getRequestURI().equals(request.getContextPath() + "/api/analytics/events/batch");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getContentLengthLong() > MAX_BYTES) { response.sendError(413); return; }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) { response.sendError(413); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
}
