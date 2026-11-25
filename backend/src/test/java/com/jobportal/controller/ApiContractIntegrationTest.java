package com.jobportal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.jobportal.DataSeeder;
import com.jobportal.entity.*;
import com.jobportal.repository.*;
import com.jobportal.security.JwtUtil;
import com.jobportal.service.EmailService;
import io.swagger.v3.core.util.Yaml;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:contracts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF", "app.email.enabled=false",
        "app.ratelimit.requests-per-minute=10000"
})
class ApiContractIntegrationTest {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("app.resume.upload-dir", () -> uploads.toString());
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired CandidateRepository candidates;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ApplicationRepository applications;
    @Autowired ResumeRepository resumes;
    @Autowired InterviewSlotRepository slots;
    @Autowired CacheManager caches;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @MockBean DataSeeder seeder;
    @MockBean EmailService email;
    User candidateUser, companyUser, admin;
    Candidate candidate;
    Company company;
    Job job;

    @BeforeEach void setup() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        slots.deleteAll(); resumes.deleteAll(); applications.deleteAll(); jobs.deleteAll();
        candidates.deleteAll(); companies.deleteAll(); users.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        candidateUser = user("candidate@contract.test", User.Role.CANDIDATE);
        companyUser = user("company@contract.test", User.Role.COMPANY);
        admin = user("admin@contract.test", User.Role.ADMIN);
        candidate = new Candidate(); candidate.setUserId(candidateUser.getId()); candidate.setFullName("Alex Morgan"); candidate = candidates.save(candidate);
        company = new Company(); company.setUserId(companyUser.getId()); company.setCompanyName("Example Technologies"); company = companies.save(company);
        job = new Job(); job.setCompanyId(company.getId()); job.setTitle("Backend Engineer");
        job.setDescription("Build and maintain the recruitment platform APIs."); job.setLatitude(37.7749); job.setLongitude(-122.4194); job = jobs.save(job);
    }

    @Test void generatedContractMatchesSnapshotAndEveryControllerOperation() throws Exception {
        JsonNode contract = contract();
        Set<String> documented = new TreeSet<>();
        contract.path("paths").fields().forEachRemaining(path -> path.getValue().fieldNames().forEachRemaining(method -> documented.add(method.toUpperCase()+" "+path.getKey())));
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(path -> path.startsWith("/api/v1/") || path.equals("/health"))
                .forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name()+" "+path))));
        assertEquals(47, actual.size());
        assertEquals(actual, documented);
        contract.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            JsonNode operation = method.getValue();
            for (String section : List.of("Use case:", "Consumers:", "Roles and ownership:", "Workflow and side effects:"))
                assertTrue(operation.path("description").asText().contains(section), path.getKey());
            assertTrue(operation.path("responses").has("429"));
            operation.path("responses").fields().forEachRemaining(response -> {
                response.getValue().path("content").fields().forEachRemaining(media -> {
                    if (media.getKey().equals("application/json")) {
                        assertFalse(media.getValue().path("examples").isEmpty(), path.getKey()+" "+response.getKey());
                        media.getValue().path("examples").forEach(example -> assertSchema(contract, media.getValue().path("schema"), example.path("value")));
                    }
                });
            });
        }));
        assertTrue(contract.at("/paths/~1api~1v1~1jobs~1search~1title/get/deprecated").asBoolean());
        Path snapshot = Path.of("../docs/openapi.yaml");
        JsonNode canonical = sorted(contract);
        if (Boolean.getBoolean("contracts.export")) Files.writeString(snapshot, Yaml.mapper().writeValueAsString(canonical));
        assertEquals(canonical, Yaml.mapper().readTree(Files.readString(snapshot)),
                "Refresh deliberately with -Dcontracts.export=true after reviewing the API changes");
        JsonNode config = json.readTree(http.getForObject("/v3/api-docs/swagger-config", String.class));
        assertEquals("/v3/api-docs/v1", config.path("url").asText());
    }

    @Test void liveSuccessBodiesMatchPublishedSchemas() throws Exception {
        JsonNode contract = contract();
        for (String[] route : List.of(new String[]{"/health","/health"},new String[]{"/api/v1/jobs","/api/v1/jobs"},
                new String[]{"/api/v1/jobs/"+job.getId(),"/api/v1/jobs/{id}"},new String[]{"/api/v1/jobs?title=Engineer","/api/v1/jobs"},
                new String[]{"/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5","/api/v1/jobs/map"},
                new String[]{"/api/v1/companies","/api/v1/companies"},new String[]{"/api/v1/slots","/api/v1/slots"},
                new String[]{"/api/v1/users","/api/v1/users"},new String[]{"/api/v1/candidates","/api/v1/candidates"})) {
            ResponseEntity<String> response = request(HttpMethod.GET, route[0], admin, null);
            assertEquals(200,response.getStatusCode().value(),response.getBody());
            assertSchema(contract, contract.path("paths").path(route[1]).path("get").path("responses").path("200").path("content").path("application/json").path("schema"),json.readTree(response.getBody()));
        }
    }

    @Test void fieldAndTypeErrorsReturn400BeforePersisting() throws Exception {
        long before=users.count();
        error(request(HttpMethod.POST,"/api/v1/users",admin,Map.of("email","bad","password","x","role","ADMIN")),400,"VALIDATION_ERROR");
        error(request(HttpMethod.POST,"/api/v1/users",admin,Map.of("email","new@example.com","password","password123","role","INVALID")),400,"VALIDATION_ERROR");
        assertEquals(before,users.count());
        error(request(HttpMethod.POST,"/api/v1/slots",candidateUser,Map.of("jobId","not-an-integer","startsAt","invalid")),400,"INVALID_REQUEST");
        error(request(HttpMethod.POST,"/api/v1/slots",candidateUser,Map.of("jobId",job.getId(),"startsAt","invalid","endsAt","invalid")),400,"VALIDATION_ERROR");
        error(request(HttpMethod.GET,"/api/v1/jobs/not-a-number",null,null),400,"INVALID_REQUEST");
        error(request(HttpMethod.GET,"/api/v1/jobs/-1",null,null),400,"VALIDATION_ERROR");
        error(request(HttpMethod.POST,"/api/v1/auth/register",null,"{broken"),400,"INVALID_REQUEST");
        assertEquals(0,slots.count());
    }

    @Test void jobConstraintsAreSharedWithGraphQL() throws Exception {
        Map<String,Object> input=new LinkedHashMap<>(Map.of("companyId",company.getId(),"title","Engineer","description","A sufficiently long job description."));
        input.put("minSalary",200); input.put("maxSalary",100);
        error(request(HttpMethod.PUT,"/api/v1/jobs/"+job.getId(),companyUser,input),400,"VALIDATION_ERROR");
        JsonNode unchanged=json.readTree(request(HttpMethod.GET,"/api/v1/jobs/"+job.getId(),null,null).getBody());
        assertTrue(unchanged.path("minSalary").isNull(), "Rejected updates must not mutate the read cache");
        assertTrue(jobs.findById(job.getId()).orElseThrow().getMinSalary()==null);
        ResponseEntity<String> gql=graphql("mutation { createJob(input: {companyId: "+company.getId()+", title: \"Engineer\", description: \"A sufficiently long job description.\", minSalary: 200, maxSalary: 100}) { id } }",companyUser);
        assertEquals("VALIDATION_ERROR",json.readTree(gql.getBody()).at("/errors/0/extensions/code").asText(),gql.getBody());
        input.remove("minSalary"); input.remove("maxSalary"); input.put("employmentType","invented");
        error(request(HttpMethod.POST,"/api/v1/jobs",companyUser,input),400,"VALIDATION_ERROR");
        assertEquals(1,jobs.count());
    }

    @Test void invalidFiltersAndGeographyReturnClientErrors() throws Exception {
        for(String query:List.of("?limit=0","?limit=101","?status=INVALID","?min_price=200&max_price=100","?after=bad!","/map?latitude=95&longitude=0&radius_km=5","/map?latitude=10","/map?min_lat=20&max_lat=10&min_lon=0&max_lon=1"))
            error(request(HttpMethod.GET,"/api/v1/jobs"+query,null,null),400,"VALIDATION_ERROR");
        error(request(HttpMethod.GET,"/api/v1/slots?starts_at=2027-01-10T10:00:00",null,null),400,"VALIDATION_ERROR");
    }

    @Test void authenticationAndPermissionErrorsUseTheSharedContract() throws Exception {
        error(request(HttpMethod.GET,"/api/v1/users",null,null),401,"UNAUTHORIZED");
        error(request(HttpMethod.GET,"/api/v1/users",candidateUser,null),403,"FORBIDDEN");
        error(request(HttpMethod.POST,"/api/v1/auth/login",null,Map.of("email",candidateUser.getEmail(),"password","incorrect")),401,"UNAUTHORIZED");
        error(request(HttpMethod.POST,"/api/v1/auth/token",null,Map.of("password","password123")),400,"VALIDATION_ERROR");
        ResponseEntity<String> good=request(HttpMethod.POST,"/api/v1/auth/token",null,Map.of("email",candidateUser.getEmail(),"password","password123"));
        assertEquals(200,good.getStatusCode().value(),good.getBody());
        assertTrue(json.readTree(good.getBody()).hasNonNull("token"));
    }

    @Test void updatePasswordUsesValidatedInputAndNeverReturnsIt() throws Exception {
        ResponseEntity<String> response=request(HttpMethod.PUT,"/api/v1/users/"+candidateUser.getId(),candidateUser,Map.of("email",candidateUser.getEmail(),"password","new-password123"));
        assertEquals(200,response.getStatusCode().value(),response.getBody());
        assertFalse(json.readTree(response.getBody()).has("password"));
        assertTrue(passwords.matches("new-password123",users.findById(candidateUser.getId()).orElseThrow().getPassword()));
    }

    @Test void applicationRequiresAnExistingJobAndRejectsDuplicate() throws Exception {
        error(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",999999)),404,"RESOURCE_NOT_FOUND");
        assertEquals(0,applications.count());
        ResponseEntity<String> response=request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId(),"status","HIRED","unknownField","ignored"));
        assertEquals(201,response.getStatusCode().value(),response.getBody());
        assertEquals("SUBMITTED",json.readTree(response.getBody()).path("status").asText());
        error(request(HttpMethod.POST,"/api/v1/applications",candidateUser,Map.of("jobId",job.getId())),409,"DUPLICATE_RESOURCE");
    }

    @Test void slotConflictUsesDocumentedCode() throws Exception {
        Map<String,Object> input=Map.of("jobId",job.getId(),"startsAt","2027-01-10T10:00:00","endsAt","2027-01-10T10:30:00");
        assertEquals(201,request(HttpMethod.POST,"/api/v1/slots",candidateUser,input).getStatusCode().value());
        error(request(HttpMethod.POST,"/api/v1/slots",candidateUser,input),409,"SLOT_CONFLICT");
    }

    @Test void resumesRoundTripAndMissingFilesReturnJson404() throws Exception {
        byte[] bytes="%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<String> response=upload("resume.PDF",bytes,candidate.getId());
        assertEquals(201,response.getStatusCode().value(),response.getBody());
        int id=json.readTree(response.getBody()).at("/resume/id").asInt();
        HttpHeaders headers=headers(candidateUser); headers.setAccept(List.of(MediaType.ALL));
        ResponseEntity<byte[]> download=http.exchange("/api/v1/resumes/download/"+id,HttpMethod.GET,new HttpEntity<>(headers),byte[].class);
        assertEquals(200,download.getStatusCode().value()); assertArrayEquals(bytes,download.getBody());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM,download.getHeaders().getContentType());
        assertTrue(download.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        Resume stored=resumes.findById(id).orElseThrow(); Files.delete(uploads.resolve(Path.of(stored.getFilePath()).getFileName()));
        error(request(HttpMethod.GET,"/api/v1/resumes/download/"+id,candidateUser,null),404,"RESOURCE_NOT_FOUND");
        assertEquals(200,request(HttpMethod.DELETE,"/api/v1/resumes/"+id,candidateUser,null).getStatusCode().value());
        assertFalse(resumes.existsById(id));
    }

    @Test void invalidAndOversizedUploadsReturn400Or413() throws Exception {
        error(upload("resume",new byte[]{1},candidate.getId()),400,"VALIDATION_ERROR");
        error(upload("resume.exe",new byte[]{1},candidate.getId()),400,"VALIDATION_ERROR");
        error(upload("resume.pdf",new byte[0],candidate.getId()),400,"VALIDATION_ERROR");
        error(upload("resume.pdf",new byte[5*1024*1024+1],candidate.getId()),413,"PAYLOAD_TOO_LARGE");
        assertEquals(0,resumes.count());
    }

    @Test void publishedGraphqlSchemaMatchesRuntimeAndSupportsFiveKmSearch() throws Exception {
        String runtime=Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));
        assertEquals(runtime,Files.readString(Path.of("../docs/schema.graphql")));
        var ast=new graphql.parser.Parser().parseDocument(runtime);
        String query="{ __schema { queryType { fields { name } } mutationType { fields { name } } } }";
        JsonNode introspection=json.readTree(graphql(query,null).getBody());
        for(String type:List.of("Query","Mutation")) {
            var definition=ast.getDefinitionsOfType(graphql.language.ObjectTypeDefinition.class).stream().filter(d -> d.getName().equals(type)).findFirst().orElseThrow();
            Set<String> expected=new TreeSet<>(); definition.getFieldDefinitions().forEach(f -> expected.add(f.getName()));
            Set<String> actual=new TreeSet<>(); introspection.at("/data/__schema/"+(type.equals("Query")?"queryType":"mutationType")+"/fields").forEach(f -> actual.add(f.path("name").asText()));
            assertEquals(expected,actual);
        }
        JsonNode near=json.readTree(graphql("{ jobsNear(latitude:37.7749, longitude:-122.4194, radiusKm:5) { id title company { companyName } } }",null).getBody());
        assertFalse(near.has("errors"),near.toString()); assertEquals(1,near.at("/data/jobsNear").size());
        for(String invalid:List.of("{ jobs(first:0) { edges { cursor } } }", "{ jobsNear(latitude:95,longitude:0,radiusKm:5) { id } }", "{ jobs(after:\"bad!\") { edges { cursor } } }")) {
            JsonNode failure=json.readTree(graphql(invalid,null).getBody());
            assertEquals("VALIDATION_ERROR",failure.at("/errors/0/extensions/code").asText(),failure.toString());
        }
    }

    private User user(String email,User.Role role) {
        User user=new User(); user.setEmail(email); user.setPassword(passwords.encode("password123")); user.setRole(role); return users.save(user);
    }
    private HttpHeaders headers(User user) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(user!=null) headers.setBearerAuth(jwt.generateToken(user.getEmail(),user.getRole().name())); return headers;
    }
    private ResponseEntity<String> request(HttpMethod method,String path,User user,Object body) {
        return http.exchange(path,method,new HttpEntity<>(body,headers(user)),String.class);
    }
    private ResponseEntity<String> graphql(String query,User user) { return request(HttpMethod.POST,"/graphql/query",user,Map.of("query",query)); }
    private ResponseEntity<String> upload(String filename,byte[] bytes,int candidateId) {
        LinkedMultiValueMap<String,Object> body=new LinkedMultiValueMap<>(); body.add("candidateId",Integer.toString(candidateId));
        body.add("file",new ByteArrayResource(bytes) { @Override public String getFilename() {return filename;} });
        HttpHeaders headers=headers(candidateUser); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/resumes/upload",HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
    }
    private JsonNode contract() throws Exception { return json.readTree(http.getForObject("/v3/api-docs/v1",String.class)); }
    private void error(ResponseEntity<String> response,int status,String code) throws Exception {
        assertEquals(status,response.getStatusCode().value(),response.getBody());
        JsonNode body=json.readTree(response.getBody()); assertEquals(status,body.path("status").asInt()); assertEquals(code,body.path("code").asText(),body.toString());
        assertTrue(body.path("details").isArray()); assertTrue(body.path("timestamp").asText().endsWith("Z")); assertTrue(body.path("path").asText().startsWith("/"));
        assertNotNull(response.getHeaders().getFirst("RateLimit-Limit"));
        assertSchema(contract(),contract().at("/components/schemas/ErrorResponse"),body);
    }
    private static JsonNode sorted(JsonNode node) {
        if(node.isObject()) { ObjectNode result=JsonNodeFactory.instance.objectNode(); TreeMap<String,JsonNode> fields=new TreeMap<>(); node.fields().forEachRemaining(e -> fields.put(e.getKey(),sorted(e.getValue()))); fields.forEach(result::set); return result; }
        if(node.isArray()) { ArrayNode result=JsonNodeFactory.instance.arrayNode(); node.forEach(value -> result.add(sorted(value))); return result; } return node;
    }
    /** Validates the structural subset used by this contract against real payloads and documented examples. */
    private static void assertSchema(JsonNode contract,JsonNode schema,JsonNode value) {
        assertFalse(schema.isMissingNode(),"Missing response schema");
        if(schema.has("$ref")) { assertSchema(contract,contract.at(schema.path("$ref").asText().substring(1)),value); return; }
        if(value.isNull()) { assertTrue(schema.path("nullable").asBoolean(),schema.toString()); return; }
        if(schema.has("oneOf")) { long matches=0; for(JsonNode choice:schema.path("oneOf")) {try {assertSchema(contract,choice,value); matches++;}catch(AssertionError ignored){}} assertEquals(1,matches,value.toString()); return; }
        switch(schema.path("type").asText()) {
            case "object" -> {assertTrue(value.isObject(),value.toString()); schema.path("required").forEach(field -> assertTrue(value.has(field.asText()),"Missing "+field)); value.fields().forEachRemaining(field -> {if(schema.path("properties").has(field.getKey())) assertSchema(contract,schema.path("properties").path(field.getKey()),field.getValue());});}
            case "array" -> {assertTrue(value.isArray(),value.toString()); value.forEach(item -> assertSchema(contract,schema.path("items"),item));}
            case "integer" -> assertTrue(value.isIntegralNumber(),value.toString());
            case "number" -> assertTrue(value.isNumber(),value.toString());
            case "boolean" -> assertTrue(value.isBoolean(),value.toString());
            case "string" -> assertTrue(value.isTextual(),value.toString());
            default -> fail("Unsupported schema: "+schema);
        }
        if(schema.has("enum")) {boolean found=false; for(JsonNode allowed:schema.path("enum")) if(allowed.equals(value)) found=true; assertTrue(found,value.toString());}
    }
}
