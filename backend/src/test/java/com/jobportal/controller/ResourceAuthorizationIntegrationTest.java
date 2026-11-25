package com.jobportal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.DataSeeder;
import com.jobportal.entity.*;
import com.jobportal.filter.RateLimitFilter;
import com.jobportal.repository.*;
import com.jobportal.security.JwtUtil;
import com.jobportal.service.EmailService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:authorization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF", "app.email.enabled=false"
})
class ResourceAuthorizationIntegrationTest {
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JwtUtil jwt;
    @Autowired private UserRepository users;
    @Autowired private CandidateRepository candidates;
    @Autowired private CompanyRepository companies;
    @Autowired private JobRepository jobs;
    @Autowired private ApplicationRepository applications;
    @Autowired private ResumeRepository resumes;
    @Autowired private InterviewSlotRepository slots;
    @Autowired private CacheManager caches;
    @MockBean private DataSeeder seeder;
    @MockBean private EmailService email;
    @MockBean private RateLimitFilter rateLimit;

    private User candidateUser, otherCandidateUser, companyUser, otherCompanyUser, admin;
    private Candidate candidate, otherCandidate;
    private Company company, otherCompany;
    private Job job, otherJob;
    private Application application;
    private Resume resume;
    private InterviewSlot slot;

    @BeforeEach
    void seed() throws Exception {
        // The JDK client exposes 401 responses without HttpURLConnection's streaming retry exception.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        doAnswer(call -> {
            ((FilterChain) call.getArgument(2)).doFilter(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(rateLimit).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        slots.deleteAll(); resumes.deleteAll(); applications.deleteAll(); jobs.deleteAll();
        candidates.deleteAll(); companies.deleteAll(); users.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        candidateUser = user("candidate@example.com", User.Role.CANDIDATE);
        otherCandidateUser = user("other-candidate@example.com", User.Role.CANDIDATE);
        companyUser = user("company@example.com", User.Role.COMPANY);
        otherCompanyUser = user("other-company@example.com", User.Role.COMPANY);
        admin = user("admin@example.com", User.Role.ADMIN);
        candidate = candidate(candidateUser);
        otherCandidate = candidate(otherCandidateUser);
        company = company(companyUser);
        otherCompany = company(otherCompanyUser);
        job = job(company);
        otherJob = job(otherCompany);
        application = new Application(); application.setCandidateId(candidate.getId()); application.setJobId(job.getId());
        application = applications.save(application);
        resume = new Resume(); resume.setCandidateId(candidate.getId()); resume.setFilePath("/static/resumes/sample.pdf");
        resume = resumes.save(resume);
        slot = new InterviewSlot(); slot.setJobId(job.getId()); slot.setCandidateId(candidate.getId());
        slot.setStartsAt(LocalDateTime.of(2027, 1, 10, 10, 0)); slot.setEndsAt(slot.getStartsAt().plusHours(1));
        slot = slots.save(slot);
    }

    @Test
    void restJobMutationsRequireRoleAndOwnership() throws Exception {
        Map<String, Object> body = jobBody(company.getId());
        assertStatus(401, request(HttpMethod.POST, "/api/v1/jobs", null, body));
        assertStatus(403, request(HttpMethod.POST, "/api/v1/jobs", candidateUser, body));
        assertStatus(403, request(HttpMethod.POST, "/api/v1/jobs", otherCompanyUser, body));
        assertStatus(201, request(HttpMethod.POST, "/api/v1/jobs", companyUser, body));
        assertStatus(403, request(HttpMethod.PUT, "/api/v1/jobs/" + job.getId(), otherCompanyUser, body));
        assertStatus(403, request(HttpMethod.DELETE, "/api/v1/jobs/" + job.getId(), otherCompanyUser, null));
        assertEquals("Backend Engineer", jobs.findById(job.getId()).orElseThrow().getTitle());
        assertStatus(200, request(HttpMethod.PUT, "/api/v1/jobs/" + job.getId(), companyUser, body));
        assertStatus(204, request(HttpMethod.DELETE, "/api/v1/jobs/" + otherJob.getId(), admin, null));
    }

    @Test
    void candidateProfilesCannotBeReadOrModifiedByAnotherCandidate() throws Exception {
        String path = "/api/v1/candidates/" + candidate.getId();
        assertStatus(401, request(HttpMethod.GET, path, null, null));
        assertStatus(403, request(HttpMethod.GET, path, otherCandidateUser, null));
        assertStatus(200, request(HttpMethod.GET, path, candidateUser, null));
        assertStatus(200, request(HttpMethod.GET, path, companyUser, null));
        assertStatus(403, request(HttpMethod.GET, path, otherCompanyUser, null));
        assertStatus(403, request(HttpMethod.PUT, path, otherCandidateUser, Map.of("fullName", "Changed Name")));
        assertStatus(403, request(HttpMethod.PUT, path, candidateUser, Map.of("fullName", "Changed Name", "userId", otherCandidateUser.getId())));
        assertEquals(candidateUser.getId(), candidates.findById(candidate.getId()).orElseThrow().getUserId());
        assertStatus(403, request(HttpMethod.GET, "/api/v1/candidates", candidateUser, null));
        assertStatus(200, request(HttpMethod.GET, "/api/v1/candidates", admin, null));
        assertStatus(403, request(HttpMethod.DELETE, path, otherCandidateUser, null));
    }

    @Test
    void companyProfileOwnershipAndApprovalCannotBeForged() throws Exception {
        String path = "/api/v1/companies/" + company.getId();
        assertStatus(403, request(HttpMethod.PUT, path, otherCompanyUser, Map.of("companyName", "Stolen")));
        assertStatus(403, request(HttpMethod.DELETE, path, otherCompanyUser, null));
        assertStatus(403, request(HttpMethod.GET, "/api/v1/companies/user/" + companyUser.getId(), otherCompanyUser, null));
        assertStatus(403, request(HttpMethod.POST, "/api/v1/companies", otherCompanyUser,
                Map.of("userId", companyUser.getId(), "companyName", "Forged")));
        assertStatus(400, request(HttpMethod.POST, "/api/v1/companies", companyUser,
                Map.of("id", company.getId(), "userId", companyUser.getId(), "companyName", "Overwrite")));
        assertStatus(403, request(HttpMethod.PUT, path, companyUser,
                Map.of("userId", otherCompanyUser.getId(), "companyName", "Forged")));
        User newcomer = user("new-company@example.com", User.Role.COMPANY);
        ResponseEntity<String> created = request(HttpMethod.POST, "/api/v1/companies", newcomer,
                Map.of("companyName", "New Company", "approved", true));
        assertStatus(201, created);
        assertFalse(json.readTree(created.getBody()).path("approved").asBoolean());
        assertEquals(newcomer.getId().intValue(), json.readTree(created.getBody()).path("userId").asInt());
        assertStatus(204, request(HttpMethod.DELETE, "/api/v1/companies/" + otherCompany.getId(), admin, null));
    }

    @Test
    void candidateCreationBindsTheAuthenticatedUserAndRejectsOwnerOverrides() throws Exception {
        User newcomer = user("new-candidate@example.com", User.Role.CANDIDATE);
        assertStatus(403, request(HttpMethod.POST, "/api/v1/candidates", newcomer,
                Map.of("userId", otherCandidateUser.getId(), "fullName", "New Candidate")));
        assertStatus(400, request(HttpMethod.POST, "/api/v1/candidates", newcomer,
                Map.of("id", candidate.getId(), "fullName", "Overwrite")));
        assertStatus(403, request(HttpMethod.POST, "/api/v1/candidates", companyUser,
                Map.of("fullName", "Wrong Role")));
        ResponseEntity<String> created = request(HttpMethod.POST, "/api/v1/candidates", newcomer,
                Map.of("fullName", "New Candidate"));
        assertStatus(200, created);
        assertEquals(newcomer.getId().intValue(), json.readTree(created.getBody()).path("userId").asInt());
    }

    @Test
    void resumeUploadRejectsAnotherCandidatesIdBeforeWritingAFile() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwt.generateToken(otherCandidateUser.getEmail(), "CANDIDATE"));
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add("candidateId", candidate.getId().toString());
        body.add("file", new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override public String getFilename() { return "test.pdf"; }
        });
        long count = resumes.count();
        ResponseEntity<String> response = http.postForEntity("/api/v1/resumes/upload", new HttpEntity<>(body, headers), String.class);
        assertStatus(403, response);
        assertEquals(count, resumes.count());
    }

    @Test
    void applicationReadsAndMutationsAreScopedToCandidateAndEmployer() throws Exception {
        String path = "/api/v1/applications/" + application.getId();
        assertStatus(403, request(HttpMethod.GET, path, otherCandidateUser, null));
        assertStatus(403, request(HttpMethod.GET, path, otherCompanyUser, null));
        assertStatus(200, request(HttpMethod.GET, path, candidateUser, null));
        assertStatus(200, request(HttpMethod.GET, path, companyUser, null));
        assertStatus(403, request(HttpMethod.PUT, path + "/status", otherCompanyUser, "REVIEWED"));
        assertStatus(403, request(HttpMethod.PUT, path + "/status", candidateUser, "HIRED"));
        assertStatus(200, request(HttpMethod.PUT, path + "/status", companyUser, "REVIEWED"));
        assertStatus(403, request(HttpMethod.DELETE, path, otherCandidateUser, null));
        assertStatus(204, request(HttpMethod.DELETE, path, candidateUser, null));
    }

    @Test
    void applicationSubmissionDerivesIdentityAndRejectsAnExistingId() throws Exception {
        assertStatus(403, request(HttpMethod.POST, "/api/v1/applications", candidateUser,
                Map.of("candidateId", otherCandidate.getId(), "jobId", otherJob.getId())));
        assertStatus(400, request(HttpMethod.POST, "/api/v1/applications", candidateUser,
                Map.of("id", application.getId(), "jobId", otherJob.getId())));
        ResponseEntity<String> response = request(HttpMethod.POST, "/api/v1/applications", candidateUser,
                Map.of("jobId", otherJob.getId(), "status", "HIRED"));
        assertStatus(201, response);
        JsonNode body = json.readTree(response.getBody());
        assertEquals(candidate.getId().intValue(), body.path("candidateId").asInt());
        assertEquals("SUBMITTED", body.path("status").asText());
    }

    @Test
    void resumeAccessRequiresOwnershipOrAnApplicationToTheEmployersJob() throws Exception {
        String path = "/api/v1/resumes/" + resume.getId();
        assertStatus(403, request(HttpMethod.GET, path, otherCandidateUser, null));
        assertStatus(403, request(HttpMethod.GET, path, otherCompanyUser, null));
        assertStatus(200, request(HttpMethod.GET, path, candidateUser, null));
        assertStatus(200, request(HttpMethod.GET, path, companyUser, null));
        assertStatus(403, request(HttpMethod.GET, "/api/v1/resumes/download/" + resume.getId(), otherCandidateUser, null));
        assertStatus(403, request(HttpMethod.DELETE, path, companyUser, null));
        assertStatus(403, request(HttpMethod.DELETE, path, otherCandidateUser, null));
        assertTrue(resumes.existsById(resume.getId()));
        assertStatus(403, request(HttpMethod.GET, "/api/v1/resumes", candidateUser, null));
    }

    @Test
    void selfServiceUserAccessDoesNotAllowRoleEscalation() throws Exception {
        String path = "/api/v1/users/" + candidateUser.getId();
        assertStatus(200, request(HttpMethod.GET, path, candidateUser, null));
        assertStatus(403, request(HttpMethod.GET, path, otherCandidateUser, null));
        assertStatus(403, request(HttpMethod.PUT, path, candidateUser,
                Map.of("email", candidateUser.getEmail(), "role", "ADMIN")));
        assertEquals(User.Role.CANDIDATE, users.findById(candidateUser.getId()).orElseThrow().getRole());
        assertStatus(200, request(HttpMethod.PUT, path, candidateUser, Map.of("email", "updated@example.com")));
        assertEquals(User.Role.CANDIDATE, users.findById(candidateUser.getId()).orElseThrow().getRole());
        assertStatus(403, request(HttpMethod.GET, "/api/v1/users", otherCandidateUser, null));
        assertStatus(200, request(HttpMethod.GET, "/api/v1/users", admin, null));
    }

    @Test
    void slotReadsRedactCandidateIdsAndBookingsCannotImpersonateAnotherCandidate() throws Exception {
        JsonNode publicSlots = json.readTree(request(HttpMethod.GET, "/api/v1/slots", null, null).getBody());
        assertTrue(publicSlots.get(0).path("candidateId").isNull());
        JsonNode privateSlots = json.readTree(request(HttpMethod.GET, "/api/v1/slots", candidateUser, null).getBody());
        assertEquals(candidate.getId().intValue(), privateSlots.get(0).path("candidateId").asInt());
        assertStatus(403, request(HttpMethod.POST, "/api/v1/slots", otherCandidateUser,
                Map.of("jobId", job.getId(), "candidateId", candidate.getId(),
                        "startsAt", "2027-02-10T10:00:00Z", "endsAt", "2027-02-10T11:00:00Z")));
        assertStatus(403, request(HttpMethod.DELETE, "/api/v1/slots/" + slot.getId(), otherCandidateUser, null));
        assertStatus(403, request(HttpMethod.DELETE, "/api/v1/slots/" + slot.getId(), otherCompanyUser, null));
        assertStatus(204, request(HttpMethod.DELETE, "/api/v1/slots/" + slot.getId(), candidateUser, null));
    }

    @Test
    void graphqlMutationsRequireAuthenticationRoleAndOwnerAndPreserveDataOnFailure() throws Exception {
        String mutation = "mutation { updateJob(id: \"" + job.getId() + "\", input: {title: \"Changed\"}) { id title } }";
        assertGraphError("UNAUTHORIZED", graph(null, mutation));
        assertGraphError("FORBIDDEN", graph(candidateUser, mutation));
        assertGraphError("FORBIDDEN", graph(otherCompanyUser, mutation));
        assertEquals("Backend Engineer", jobs.findById(job.getId()).orElseThrow().getTitle());
        JsonNode result = graph(companyUser, mutation);
        assertFalse(result.has("errors"), result.toPrettyString());
        assertEquals("Changed", result.path("data").path("updateJob").path("title").asText());
        assertEquals("Changed", jobs.findById(job.getId()).orElseThrow().getTitle());
        String deletion = "mutation { deleteJob(id: \"" + otherJob.getId() + "\") }";
        assertGraphError("FORBIDDEN", graph(companyUser, deletion));
        assertTrue(jobs.existsById(otherJob.getId()));
        assertFalse(graph(admin, deletion).has("errors"));
        assertFalse(jobs.existsById(otherJob.getId()));
    }

    @Test
    void graphqlCreateJobAndSlotRejectAnonymousAndForeignOwners() throws Exception {
        String create = "mutation { createJob(input: {companyId: " + company.getId()
                + ", title: \"New job\", description: \"A sufficiently long job description\"}) { id } }";
        long count = jobs.count();
        assertGraphError("UNAUTHORIZED", graph(null, create));
        assertGraphError("FORBIDDEN", graph(otherCompanyUser, create));
        assertEquals(count, jobs.count());
        assertFalse(graph(companyUser, create).has("errors"));
        String booking = "mutation { createSlot(input: {jobId: " + job.getId() + ", candidateId: " + candidate.getId()
                + ", startsAt: \"2027-03-10T10:00:00Z\", endsAt: \"2027-03-10T11:00:00Z\"}) { id candidateId } }";
        assertGraphError("UNAUTHORIZED", graph(null, booking));
        assertGraphError("FORBIDDEN", graph(otherCandidateUser, booking));
        assertGraphError("FORBIDDEN", graph(otherCompanyUser, booking));
        assertEquals(1, slots.count());
        assertFalse(graph(candidateUser, booking).has("errors"));
        assertEquals(2, slots.count());
    }

    @Test
    void graphqlPrivateFieldsAreProtectedIncludingAfterAsynchronousCompanyLoading() throws Exception {
        String query = "{ job(id: \"" + job.getId() + "\") { title applications { id } } }";
        JsonNode anonymous = graph(null, query);
        assertGraphError("UNAUTHORIZED", anonymous);
        assertEquals("Backend Engineer", anonymous.path("data").path("job").path("title").asText());
        assertTrue(anonymous.path("data").path("job").path("applications").isNull());
        assertGraphError("FORBIDDEN", graph(otherCompanyUser, query));
        assertFalse(graph(companyUser, query).has("errors"));
        String nested = "{ job(id: \"" + job.getId() + "\") { company { jobs { applications { id } } } } }";
        assertFalse(graph(companyUser, nested).has("errors"));
        assertGraphError("FORBIDDEN", graph(otherCompanyUser, nested));
        JsonNode slotsResult = graph(null, "{ slots { id candidateId } }");
        assertFalse(slotsResult.has("errors"));
        assertTrue(slotsResult.path("data").path("slots").get(0).path("candidateId").isNull());
        assertEquals(candidate.getId().intValue(), graph(candidateUser, "{ slots { candidateId } }")
                .path("data").path("slots").get(0).path("candidateId").asInt());
    }

    private ResponseEntity<String> request(HttpMethod method, String path, User actor, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (actor != null) headers.setBearerAuth(jwt.generateToken(actor.getEmail(), actor.getRole().name()));
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
    }
    private JsonNode graph(User actor, String query) throws Exception {
        ResponseEntity<String> response = request(HttpMethod.POST, "/graphql/query", actor, Map.of("query", query));
        assertStatus(200, response);
        return json.readTree(response.getBody());
    }
    private void assertStatus(int expected, ResponseEntity<String> response) {
        assertEquals(expected, response.getStatusCode().value(), response.getBody());
    }
    private void assertGraphError(String code, JsonNode response) {
        assertEquals(code, response.path("errors").path(0).path("extensions").path("code").asText(), response.toPrettyString());
    }
    private User user(String email, User.Role role) {
        User u = new User(); u.setEmail(email); u.setRole(role); u.setPassword("encoded-test-password"); return users.save(u);
    }
    private Candidate candidate(User owner) {
        Candidate c = new Candidate(); c.setUserId(owner.getId()); c.setFullName("Test Candidate"); return candidates.save(c);
    }
    private Company company(User owner) {
        Company c = new Company(); c.setUserId(owner.getId()); c.setCompanyName("Test Company"); return companies.save(c);
    }
    private Job job(Company owner) {
        Job j = new Job(); j.setCompanyId(owner.getId()); j.setTitle("Backend Engineer");
        j.setDescription("A sufficiently long job description"); return jobs.save(j);
    }
    private Map<String, Object> jobBody(Integer companyId) {
        return Map.of("companyId", companyId, "title", "Updated job", "description", "A sufficiently long job description");
    }
}
