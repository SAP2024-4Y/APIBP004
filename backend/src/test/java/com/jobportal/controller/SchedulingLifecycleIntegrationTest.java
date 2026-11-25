package com.jobportal.controller;

import com.fasterxml.jackson.databind.*;
import com.jobportal.DataSeeder;
import com.jobportal.entity.*;
import com.jobportal.repository.*;
import com.jobportal.security.JwtUtil;
import com.jobportal.service.EmailService;
import com.jobportal.service.JobService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=$" + "{lifecycle.test.jdbc-url:jdbc:h2:mem:lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=$" + "{lifecycle.test.driver:org.h2.Driver}",
        "spring.datasource.username=$" + "{lifecycle.test.username:sa}",
        "spring.datasource.password=$" + "{lifecycle.test.password:}",
        "spring.jpa.properties.hibernate.dialect=$" + "{lifecycle.test.dialect:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF",
        "app.email.enabled=false", "app.seed.mode=off", "app.ratelimit.requests-per-minute=10000"
})
class SchedulingLifecycleIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired CandidateRepository candidates;
    @Autowired JobRepository jobs;
    @Autowired ApplicationRepository applications;
    @Autowired InterviewSlotRepository slots;
    @Autowired CacheManager caches;
    @Autowired com.jobportal.service.InterviewSlotService slotService;
    @Autowired com.jobportal.service.ApplicationService applicationService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockBean DataSeeder seeder;
    @MockBean EmailService email;
    User owner, candidateUser;
    Company company;
    Candidate candidate;
    Job job;

    @BeforeEach void setup() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        slots.deleteAll(); applications.deleteAll(); jobs.deleteAll(); candidates.deleteAll(); companies.deleteAll(); users.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        owner=user("owner@lifecycle.test",User.Role.COMPANY);
        candidateUser=user("candidate@lifecycle.test",User.Role.CANDIDATE);
        company=new Company(); company.setUserId(owner.getId()); company.setCompanyName("Lifecycle Company"); company=companies.saveAndFlush(company);
        candidate=new Candidate(); candidate.setUserId(candidateUser.getId()); candidate.setFullName("Test Candidate"); candidate=candidates.saveAndFlush(candidate);
        job=new Job(); job.setCompanyId(company.getId()); job.setTitle("Backend Engineer"); job.setDescription("Build and maintain a recruitment platform."); job=jobs.saveAndFlush(job);
    }
    private User user(String address,User.Role role) {
        User u=new User(); u.setEmail(address); u.setPassword("unused-hash"); u.setRole(role); return users.saveAndFlush(u);
    }
    private ResponseEntity<String> request(HttpMethod method,String path,User user,Object body) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(user!=null) headers.setBearerAuth(jwt.generateToken(user.getEmail(),user.getRole().name()));
        return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
    }
    private JsonNode body(ResponseEntity<String> response,int status) throws Exception {
        assertEquals(status,response.getStatusCode().value(),response.getBody()); return json.readTree(response.getBody());
    }
    private JsonNode graph(String query,Map<String,Object> variables,User user) throws Exception {
        return body(request(HttpMethod.POST,"/graphql/query",user,Map.of("query",query,"variables",variables)),200);
    }
    private Map<String,Object> slot(String start,String end) {return Map.of("jobId",job.getId(),"startsAt",start,"endsAt",end);}
    private int submit() throws Exception {return body(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId())),201).path("id").asInt();}

    @Test void offsetsNormalizeInRestGraphqlAndCalendarWindows() throws Exception {
        JsonNode created=body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T15:30:00+05:30","2027-01-10T06:00:00-05:00")),201);
        assertEquals("2027-01-10T10:00:00Z",created.path("startsAt").asText());
        assertEquals("2027-01-10T11:00:00Z",created.path("endsAt").asText());
        assertTrue(created.path("createdAt").asText().endsWith("Z"));
        assertTrue(created.path("candidateId").isNull());
        JsonNode result=graph("query($s:String,$e:String){slots(startsAt:$s,endsAt:$e){id startsAt endsAt createdAt}}",Map.of("s","2027-01-10T05:15:00-05:00","e","2027-01-10T10:45:00Z"),null);
        assertFalse(result.has("errors"),result.toString()); assertEquals(1,result.at("/data/slots").size());
        assertEquals(created.path("startsAt"),result.at("/data/slots/0/startsAt"));
        assertEquals(0,body(request(HttpMethod.GET,"/api/v1/slots?starts_at=2027-01-10T11:00:00Z&ends_at=2027-01-10T12:00:00Z",null,null),200).size());
        body(request(HttpMethod.POST,"/api/v1/slots",candidateUser,slot("2027-01-10T11:00:00","2027-01-10T12:00:00")),201);
        assertEquals(2,slots.count());
        assertEquals("SLOT_CONFLICT",body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T15:45:00+05:30","2027-01-10T16:45:00+05:30")),409).path("code").asText());
    }
    @Test void graphCreatesUtcSlotsAndJobsAndRestReadsSameInstants() throws Exception {
        JsonNode g=graph("mutation($input:CreateSlotInput!){createSlot(input:$input){id startsAt endsAt}}",Map.of("input",slot("2027-02-01T10:00:00-05:00","2027-02-01T11:00:00-05:00")),candidateUser);
        assertFalse(g.has("errors"),g.toString()); assertEquals("2027-02-01T15:00:00Z",g.at("/data/createSlot/startsAt").asText());
        Map<String,Object> input=Map.of("companyId",company.getId(),"title","Offset job","description","Work with timestamps across multiple timezones.","startsAt","2027-02-01T15:30:00+05:30","endsAt","2027-02-01T06:00:00-05:00");
        JsonNode j=graph("mutation($input:CreateJobInput!){createJob(input:$input){id startsAt endsAt createdAt}}",Map.of("input",input),owner);
        assertFalse(j.has("errors"),j.toString());
        JsonNode rest=body(request(HttpMethod.GET,"/api/v1/jobs/"+j.at("/data/createJob/id").asInt(),null,null),200);
        assertEquals("2027-02-01T10:00:00Z",rest.path("startsAt").asText()); assertEquals(rest.path("startsAt"),j.at("/data/createJob/startsAt"));
        assertTrue(j.at("/data/createJob/createdAt").asText().endsWith("Z"));
    }
    @Test void invalidWindowsAndMissingReferencesAreClientErrors() throws Exception {
        for(String query:List.of("?starts_at=2027-01-10T10:00:00Z","?ends_at=2027-01-10T10:00:00Z","?starts_at=bad&ends_at=bad","?starts_at=2027-01-10T10:00:00Z&ends_at=2027-01-10T10:00:00Z","?starts_at=2027-01-10T11:00:00Z&ends_at=2027-01-10T10:00:00Z"))
            body(request(HttpMethod.GET,"/api/v1/slots"+query,null,null),400);
        body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T15:30:00+05:30","2027-01-10T10:00:00Z")),400);
        body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("bad","bad")),400);
        var time=LocalDateTime.of(2027,1,1,10,0);
        assertThrows(com.jobportal.exception.ResourceNotFoundException.class,()->slotService.createSlot(Integer.MAX_VALUE,null,time,time.plusHours(1)));
        assertThrows(com.jobportal.exception.ResourceNotFoundException.class,()->slotService.createSlot(job.getId(),Integer.MAX_VALUE,time,time.plusHours(1)));
        assertEquals(0,slots.count());
        JsonNode invalid=graph("query($s:String){slots(startsAt:$s){id}}",Map.of("s","2027-01-10T10:00:00Z"),null);
        assertEquals("VALIDATION_ERROR",invalid.at("/errors/0/extensions/code").asText());
    }
    @Test void deletionFreesReservationAndJobTimesRemainIndependent() throws Exception {
        JsonNode created=body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T10:00:00Z","2027-01-10T11:00:00Z")),201);
        assertNull(jobs.findById(job.getId()).orElseThrow().getStartsAt());
        assertEquals(204,request(HttpMethod.DELETE,"/api/v1/slots/"+created.path("id").asLong(),owner,null).getStatusCode().value());
        body(request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T10:00:00Z","2027-01-10T11:00:00Z")),201);
    }
    @Test void simultaneousSlotCreationHasOneWinner() throws Exception {
        var outcomes=race(()->request(HttpMethod.POST,"/api/v1/slots",owner,slot("2027-01-10T10:00:00Z","2027-01-10T11:00:00Z")));
        assertEquals(List.of(201,409),outcomes.stream().map(r->r.getStatusCode().value()).sorted().toList());
        assertEquals(1,slots.count());
        for(var r:outcomes) if(r.getStatusCode().value()==409) assertEquals("SLOT_CONFLICT",json.readTree(r.getBody()).path("code").asText());
    }
    @Test void duplicateSubmissionsAreSerializedAndServerFieldsAreProtected() throws Exception {
        body(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId(),"id",999)),400);
        body(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId(),"candidateId",Integer.MAX_VALUE)),403);
        var outcomes=race(()->request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId(),"status","HIRED","appliedAt","2000-01-01T00:00:00Z")));
        assertEquals(List.of(201,409),outcomes.stream().map(r->r.getStatusCode().value()).sorted().toList());
        assertEquals(1,applications.count()); Application saved=applications.findAll().get(0);
        assertNotEquals(999,saved.getId()); assertEquals(candidate.getId(),saved.getCandidateId()); assertEquals(Application.ApplicationStatus.SUBMITTED,saved.getStatus());
        assertTrue(saved.getAppliedAt().isAfter(LocalDateTime.of(2026,1,1,0,0)));
        for(var r:outcomes) if(r.getStatusCode().value()==409) assertEquals("DUPLICATE_RESOURCE",json.readTree(r.getBody()).path("code").asText());
    }
    @Test void statusWorkflowRejectsSkipsAndTerminalChangesButAllowsRetries() throws Exception {
        int id=submit(); String path="/api/v1/applications/"+id+"/status";
        assertEquals("APPLICATION_CONFLICT",body(request(HttpMethod.PUT,path,owner,json.writeValueAsString("HIRED")),409).path("code").asText());
        for(String status:List.of("REVIEWED","REVIEWED","SHORTLISTED","HIRED","HIRED"))
            assertEquals(status,body(request(HttpMethod.PUT,path,owner,json.writeValueAsString(status)),200).path("status").asText());
        body(request(HttpMethod.PUT,path,owner,json.writeValueAsString("REJECTED")),409);
        body(request(HttpMethod.PUT,path,candidateUser,json.writeValueAsString("REVIEWED")),403);
        body(request(HttpMethod.PUT,path,owner,Map.of("status","REVIEWED")),400);
        assertEquals(Application.ApplicationStatus.HIRED,applications.findById(id).orElseThrow().getStatus());
        JsonNode nested=graph("query($id:ID!){job(id:$id){applications{appliedAt}}}",Map.of("id",job.getId()),owner);
        assertFalse(nested.has("errors"),nested.toString()); assertTrue(nested.at("/data/job/applications/0/appliedAt").asText().endsWith("Z"));
    }
    @Test void rejectionIsTerminalAndClosedJobsRejectApplications() throws Exception {
        int id=submit();
        body(request(HttpMethod.PUT,"/api/v1/applications/"+id+"/status",owner,json.writeValueAsString("REJECTED")),200);
        body(request(HttpMethod.PUT,"/api/v1/applications/"+id+"/status",owner,json.writeValueAsString("REVIEWED")),409);
        assertEquals(204,request(HttpMethod.DELETE,"/api/v1/applications/"+id,candidateUser,null).getStatusCode().value());
        for(String status:List.of("CLOSED","DRAFT")) {
            job.setStatus(status); jobs.saveAndFlush(job);
            assertEquals("APPLICATION_CONFLICT",body(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId())),409).path("code").asText());
        }
        assertEquals(0,applications.count());
    }
    @Test void concurrentTerminalTransitionsCannotOverwriteEachOther() throws Exception {
        int id=submit(); String path="/api/v1/applications/"+id+"/status";
        body(request(HttpMethod.PUT,path,owner,json.writeValueAsString("REVIEWED")),200);
        body(request(HttpMethod.PUT,path,owner,json.writeValueAsString("SHORTLISTED")),200);
        var counter=new java.util.concurrent.atomic.AtomicInteger();
        var results=race(()->request(HttpMethod.PUT,path,owner,json.writeValueAsString(counter.getAndIncrement()==0?"HIRED":"REJECTED")));
        assertEquals(List.of(200,409),results.stream().map(r->r.getStatusCode().value()).sorted().toList());
        assertTrue(Set.of(Application.ApplicationStatus.HIRED,Application.ApplicationStatus.REJECTED).contains(applications.findById(id).orElseThrow().getStatus()));
    }
    @Test void differentJobsMayOverlapAndUtcClockIgnoresJvmTimezone() throws Exception {
        Job second=new Job(); second.setCompanyId(company.getId()); second.setTitle("Second job"); second.setDescription("An independently scheduled job."); second=jobs.saveAndFlush(second);
        var start=LocalDateTime.of(2027,1,1,10,0);
        slotService.createSlot(job.getId(),candidate.getId(),start,start.plusHours(1));
        slotService.createSlot(second.getId(),candidate.getId(),start,start.plusHours(1));
        assertEquals(2,slots.count());
        assertTrue(java.time.Duration.between(java.time.Instant.now(),new User().getCreatedAt().toInstant(java.time.ZoneOffset.UTC)).abs().getSeconds()<5);
        assertTrue(java.time.Duration.between(java.time.Instant.now(),new Resume().getUploadedAt().toInstant(java.time.ZoneOffset.UTC)).abs().getSeconds()<5);
        assertTrue(json.writeValueAsString(new User()).contains("Z\""));
        assertTrue(json.writeValueAsString(new Resume()).contains("Z\""));
    }
    @Test void rolledBackSubmissionSendsNoEmail() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            Application a=new Application(); a.setCandidateId(candidate.getId()); a.setJobId(job.getId()); applicationService.saveApplication(a); status.setRollbackOnly();
        });
        assertEquals(0,applications.count()); org.mockito.Mockito.verifyNoInteractions(email);
    }
    private List<ResponseEntity<String>> race(java.util.concurrent.Callable<ResponseEntity<String>> call) throws Exception {
        var ready=new java.util.concurrent.CountDownLatch(2); var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<ResponseEntity<String>> task=()->{ready.countDown(); if(!start.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); return call.call();};
            var a=executor.submit(task); var b=executor.submit(task); assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
