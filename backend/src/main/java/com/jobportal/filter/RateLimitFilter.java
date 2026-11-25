package com.jobportal.filter;

import com.jobportal.exception.ApiErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {
    private final ApiErrorWriter errors;
    private final int limit;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();
    private record Window(long started, long count) {}
    public RateLimitFilter(ApiErrorWriter errors, @Value("$" + "{app.ratelimit.requests-per-minute:60}") int limit) {
        if (limit < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.errors = errors;
        this.limit = limit;
    }
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        long now = Instant.now().toEpochMilli();
        Window window = counters.compute(request.getRemoteAddr(), (ip, old) ->
                old == null || now - old.started() >= 60_000 ? new Window(now, 1) : new Window(old.started(), old.count() + 1));
        long reset = (window.started() + 60_000 + 999) / 1000;
        response.setHeader("RateLimit-Limit", Integer.toString(limit));
        response.setHeader("RateLimit-Remaining", Long.toString(Math.max(0, limit - window.count())));
        response.setHeader("RateLimit-Reset", Long.toString(reset));
        if (window.count() > limit) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, reset - now / 1000)));
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Limit: " + limit + " per minute.");
            return;
        }
        chain.doFilter(req, res);
    }
}
