package com.jobportal.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.exception.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Test void configuredLimitAndForwardedHeaderPolicyMatchTheDocumented429() throws Exception {
        RateLimitFilter filter=new RateLimitFilter(new ApiErrorWriter(json),2);
        AtomicInteger forwarded=new AtomicInteger();
        for(int i=0;i<3;i++) {
            MockHttpServletRequest request=new MockHttpServletRequest("GET","/health"); request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For","198.51.100."+i);
            MockHttpServletResponse response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->forwarded.incrementAndGet());
            assertEquals("2",response.getHeader("RateLimit-Limit"));
            assertEquals(Integer.toString(Math.max(0,1-i)),response.getHeader("RateLimit-Remaining"));
            assertNotNull(response.getHeader("RateLimit-Reset"));
            if(i==2) {
                assertEquals(429,response.getStatus()); assertTrue(Long.parseLong(response.getHeader("Retry-After"))>0);
                var body=json.readTree(response.getContentAsString());
                assertEquals("RATE_LIMITED",body.path("code").asText()); assertEquals(429,body.path("status").asInt());
                assertEquals("/health",body.path("path").asText()); assertTrue(body.path("details").isArray());
                assertEquals("Too many requests. Limit: 2 per minute.",body.path("message").asText());
            }
        }
        assertEquals(2,forwarded.get());
        MockHttpServletRequest other=new MockHttpServletRequest(); other.setRemoteAddr("192.0.2.2");
        MockHttpServletResponse response=new MockHttpServletResponse(); filter.doFilter(other,response,(req,res)->forwarded.incrementAndGet());
        assertEquals(200,response.getStatus()); assertEquals(3,forwarded.get());
    }

    @Test void concurrentRequestsCannotExceedTheConfiguredQuota() throws Exception {
        RateLimitFilter filter=new RateLimitFilter(new ApiErrorWriter(json),10);
        AtomicInteger passed=new AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Callable<Integer>>();
            for(int i=0;i<40;i++) tasks.add(()-> {
                MockHttpServletRequest request=new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.1");
                MockHttpServletResponse response=new MockHttpServletResponse();
                filter.doFilter(request,response,(req,res)->passed.incrementAndGet()); return response.getStatus();
            });
            int rejected=0; for(Future<Integer> result:executor.invokeAll(tasks)) if(result.get()==429) rejected++;
            assertEquals(10,passed.get()); assertEquals(30,rejected);
        }
    }
}
