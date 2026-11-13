package com.jobportal.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final int REQUESTS_PER_MINUTE = 60;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, long[]> ipCounters = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        String ip = getClientIp(httpReq);
        long now = Instant.now().toEpochMilli();

        long[] windowData = ipCounters.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing[0]) > WINDOW_MS) {
                return new long[]{now, 1};
            }
            existing[1]++;
            return existing;
        });

        long windowStart = windowData[0];
        long count = windowData[1];
        long remaining = Math.max(0, REQUESTS_PER_MINUTE - count);
        long resetEpochSec = (windowStart + WINDOW_MS) / 1000;

        httpRes.setHeader("RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
        httpRes.setHeader("RateLimit-Remaining", String.valueOf(remaining));
        httpRes.setHeader("RateLimit-Reset", String.valueOf(resetEpochSec));

        if (count > REQUESTS_PER_MINUTE) {
            httpRes.setStatus(429);
            httpRes.setContentType("application/json");
            httpRes.setHeader("Retry-After", String.valueOf(resetEpochSec - (now / 1000)));
            httpRes.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests. Limit: "
                + REQUESTS_PER_MINUTE + " per minute.\"}");
            return;
        }

        chain.doFilter(req, res);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
