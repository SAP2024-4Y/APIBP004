package com.jobportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.DataSeeder;
import com.jobportal.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:rate-limit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF", "app.email.enabled=false",
        "app.ratelimit.requests-per-minute=2"
})
class RateLimitHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @MockBean DataSeeder seeder;
    @MockBean EmailService email;

    @Test void quotaIsSharedAcrossRoutesAndErrorsAndPreservesCorsHeaders() throws Exception {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        HttpHeaders preflight=new HttpHeaders();
        preflight.setOrigin("https://client.example");
        preflight.setAccessControlRequestMethod(HttpMethod.GET);
        ResponseEntity<String> options=http.exchange("/api/v1/users",HttpMethod.OPTIONS,new HttpEntity<>(preflight),String.class);
        assertEquals(200,options.getStatusCode().value());
        assertNull(options.getHeaders().getFirst("RateLimit-Remaining"));

        HttpHeaders headers=new HttpHeaders(); headers.setOrigin("https://client.example");
        ResponseEntity<String> unauthorized=http.exchange("/api/v1/users",HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertEquals(401,unauthorized.getStatusCode().value());
        assertEquals("1",unauthorized.getHeaders().getFirst("RateLimit-Remaining"));
        ResponseEntity<String> health=http.exchange("/health",HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertEquals(200,health.getStatusCode().value());
        assertEquals("0",health.getHeaders().getFirst("RateLimit-Remaining"));

        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> limited=http.exchange("/graphql/query",HttpMethod.POST,new HttpEntity<>("{\"query\":\"{ jobs { edges { cursor } } }\"}",headers),String.class);
        assertEquals(429,limited.getStatusCode().value(),limited.getBody());
        assertEquals("https://client.example",limited.getHeaders().getAccessControlAllowOrigin());
        assertTrue(limited.getHeaders().getAccessControlExposeHeaders().contains("Retry-After"));
        assertTrue(Integer.parseInt(limited.getHeaders().getFirst("Retry-After"))>0);
        var error=json.readTree(limited.getBody());
        assertEquals("RATE_LIMITED",error.path("code").asText());
        assertEquals("/graphql/query",error.path("path").asText());
        assertEquals(429,error.path("status").asInt());
        assertTrue(error.path("details").isArray());
    }
}
