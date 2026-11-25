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
        "spring.datasource.url=$" + "{discovery.test.jdbc-url:jdbc:h2:mem:job-discovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=$" + "{discovery.test.driver:org.h2.Driver}",
        "spring.datasource.username=$" + "{discovery.test.username:sa}",
        "spring.datasource.password=$" + "{discovery.test.password:}",
        "spring.jpa.properties.hibernate.dialect=$" + "{discovery.test.dialect:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF",
        "app.email.enabled=false", "app.seed.mode=off", "app.ratelimit.requests-per-minute=10000"
})
class JobDiscoveryIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired JobService service;
    @Autowired CacheManager caches;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean DataSeeder seeder;
    @MockBean EmailService email;
    User owner, admin;
    Company company;
    List<Job> fixtures;

    @BeforeEach void setup() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        jobs.deleteAll(); companies.deleteAll(); users.deleteAll();
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        owner=user("owner@discovery.test", User.Role.COMPANY); admin=user("admin@discovery.test", User.Role.ADMIN);
        company=company(owner,"Discovery Company");
        for(int i=0;i<6;i++) company(user("company"+i+"@discovery.test",User.Role.COMPANY),"Company "+i);
        String[] titles={"Alpha","Echo","Echo","Zulu","Alpha","Beta","Gamma","Gamma","Zulu"};
        Integer[] min={null,100,100,50,50,null,300,50,0}, max={null,300,300,250,200,200,500,null,0};
        fixtures=new ArrayList<>();
        for(int i=0;i<titles.length;i++) {
            Job job=job(titles[i]); job.setMinSalary(min[i]); job.setMaxSalary(max[i]);
            job.setCreatedAt(i%3==0 ? null : LocalDateTime.of(2027,1,1,9,0).plusDays(i%3));
            job.setCategory(i%2==0 ? "Technology":"Finance"); job.setStatus(i%3==0 ? "CLOSED":"OPEN");
            job.setLocation(i%2==0 ? "San Francisco":"New York"); job.setSkillsRequired(i%2==0 ? "Java, SQL":"Python");
            fixtures.add(jobs.saveAndFlush(job));
        }
    }

    @ParameterizedTest
    @CsvSource({"id,asc","id,desc","title,asc","title,desc","created_at,asc","created_at,desc","min_price,asc","min_price,desc","max_price,asc","max_price,desc"})
    void completeRestAndGraphqlTraversalHasNoGapsOrDuplicates(String sort,String order) throws Exception {
        List<Integer> expected=fixtures.stream().sorted(comparator(sort,order)).map(Job::getId).toList();
        List<Integer> rest=new ArrayList<>(); String after=null;
        for(int page=0;page<20;page++) {
            JsonNode body=get("/api/v1/jobs?limit=2&sort="+sort+"&order="+order+(after==null?"":"&after="+after));
            body.path("data").forEach(job->rest.add(job.path("id").asInt()));
            assertEquals(2,body.path("limit").asInt());
            if(!body.path("has_more").asBoolean()) {assertTrue(body.path("next_cursor").isNull()); break;}
            after=body.path("next_cursor").asText(); assertFalse(after.isBlank());
        }
        assertEquals(expected,rest); assertEquals(rest.size(),new HashSet<>(rest).size());
        List<Integer> graph=new ArrayList<>(); after=null;
        for(int page=0;page<20;page++) {
            JsonNode result=graph("query($after:String){jobs(first:2,after:$after,sort:\""+sort+"\",order:\""+order+"\"){edges{cursor node{id}} pageInfo{hasNextPage hasPreviousPage startCursor endCursor}}}",nullableAfter(after),null);
            assertFalse(result.has("errors"),result.toString()); JsonNode connection=result.at("/data/jobs");
            JsonNode edges=connection.path("edges"), info=connection.path("pageInfo");
            assertEquals(page>0,info.path("hasPreviousPage").asBoolean());
            assertEquals(edges.get(0).path("cursor"),info.path("startCursor"));
            assertEquals(edges.get(edges.size()-1).path("cursor"),info.path("endCursor"));
            edges.forEach(edge->graph.add(edge.at("/node/id").asInt()));
            if(!info.path("hasNextPage").asBoolean()) break;
            after=info.path("endCursor").asText();
        }
        assertEquals(expected,graph); assertEquals(graph.size(),new HashSet<>(graph).size());
    }

    @Test void filtersCombineAcrossPagesAndEscapeWildcardCharacters() throws Exception {
        List<Integer> expected=fixtures.stream().filter(j->j.getCategory().equals("Technology") && j.getStatus().equals("OPEN") && j.getMinSalary()!=null && j.getMinSalary()>=50 && j.getMaxSalary()!=null && j.getMaxSalary()<=300).map(Job::getId).sorted().toList();
        List<Integer> found=new ArrayList<>(); String after=null;
        do {
            JsonNode page=get("/api/v1/jobs?status=OPEN&category=Technology&min_price=50&max_price=300&location=Francisco&skills=Java&limit=1&sort=id&order=asc"+(after==null?"":"&after="+after));
            page.path("data").forEach(job->found.add(job.path("id").asInt()));
            after=page.path("next_cursor").isNull()?null:page.path("next_cursor").asText();
        } while(after!=null && found.size()<20);
        assertEquals(expected,found);
        JsonNode graph=graph("{ jobs(status:\"OPEN\",category:\"Technology\",minPrice:50,maxPrice:300,location:\"Francisco\",skills:\"Java\",sort:\"id\",order:\"asc\"){edges{node{id}}} }",Map.of(),null);
        List<Integer> ids=new ArrayList<>(); graph.at("/data/jobs/edges").forEach(edge->ids.add(edge.at("/node/id").asInt())); assertEquals(expected,ids);
        assertEquals(0,get("/api/v1/jobs?skills=Rust").path("data").size());
        Job literal=jobs.saveAndFlush(job("100%_literal"));
        JsonNode literalGraph=graph("query($title:String){jobs(title:$title){edges{node{id}}}}",Map.of("title","%_"),null);
        assertEquals(1,literalGraph.at("/data/jobs/edges").size()); assertEquals(literal.getId().intValue(),literalGraph.at("/data/jobs/edges/0/node/id").asInt());
    }

    @Test void cursorsRejectChangedContextAndMalformedInputs() throws Exception {
        String cursor=get("/api/v1/jobs?sort=title&order=asc&limit=2").path("next_cursor").asText();
        for(String query:List.of("?sort=title&order=desc&after="+cursor,"?sort=title&order=asc&status=OPEN&after="+cursor,"?after=MQ==","?after=","?after=bad!","?sort=missing","?order=sideways","?limit=0","?limit=101","?skills=","?min_price=500&max_price=100"))
            error(request(HttpMethod.GET,"/api/v1/jobs"+query,null,null),"VALIDATION_ERROR");
        JsonNode company=graph("query($after:String){companies(first:2,after:$after){edges{node{id}}}}",Map.of("after",cursor),null);
        assertEquals("VALIDATION_ERROR",company.at("/errors/0/extensions/code").asText());
        JsonNode invalid=graph("{jobs(sort:\"missing\"){edges{node{id}}}}",Map.of(),null);
        assertEquals("VALIDATION_ERROR",invalid.at("/errors/0/extensions/code").asText());
    }

    @Test void companyPaginationAndEmptyFinalPagesHaveAccurateMetadata() throws Exception {
        List<Integer> expected=companies.findAll().stream().map(Company::getId).sorted().toList(), result=new ArrayList<>();
        String after=null;
        for(int page=0;page<10;page++) {
            JsonNode body=graph("query($after:String){companies(first:2,after:$after){edges{node{id}} pageInfo{hasNextPage hasPreviousPage startCursor endCursor}}}",nullableAfter(after),null).at("/data/companies");
            body.path("edges").forEach(edge->result.add(edge.at("/node/id").asInt()));
            assertEquals(page>0,body.at("/pageInfo/hasPreviousPage").asBoolean());
            after=body.at("/pageInfo/endCursor").asText();
            if(!body.at("/pageInfo/hasNextPage").asBoolean()) break;
        }
        assertEquals(expected,result);
        JsonNode empty=graph("query($after:String){companies(first:2,after:$after){edges{node{id}} pageInfo{hasNextPage hasPreviousPage startCursor endCursor}}}",Map.of("after",after),null).at("/data/companies");
        assertEquals(0,empty.path("edges").size()); assertFalse(empty.at("/pageInfo/hasNextPage").asBoolean()); assertTrue(empty.at("/pageInfo/hasPreviousPage").asBoolean());
        assertTrue(empty.at("/pageInfo/startCursor").isNull()); assertTrue(empty.at("/pageInfo/endCursor").isNull());
        JsonNode noJobs=graph("{jobs(category:\"absent\"){edges{node{id}} pageInfo{startCursor endCursor hasNextPage hasPreviousPage}}}",Map.of(),null).at("/data/jobs");
        assertEquals(0,noJobs.path("edges").size()); assertTrue(noJobs.at("/pageInfo/endCursor").isNull()); assertFalse(noJobs.at("/pageInfo/hasPreviousPage").asBoolean());
    }

    @Test void deletingTheCursorRowDoesNotBreakContinuation() throws Exception {
        List<Integer> expected=fixtures.stream().sorted(comparator("title","asc")).map(Job::getId).toList();
        JsonNode first=get("/api/v1/jobs?sort=title&order=asc&limit=2");
        jobs.deleteById(first.at("/data/1/id").asInt());
        JsonNode next=get("/api/v1/jobs?sort=title&order=asc&limit=100&after="+first.path("next_cursor").asText());
        List<Integer> remaining=new ArrayList<>(); next.path("data").forEach(job->remaining.add(job.path("id").asInt())); assertEquals(expected.subList(2,expected.size()),remaining);
    }

    @Test void restCreateReadFilterMapAndUpdatePersistAllSupportedFields() throws Exception {
        Map<String,Object> input=fullInput("REST discovery job");
        ResponseEntity<String> response=request(HttpMethod.POST,"/api/v1/jobs",owner,input); assertEquals(201,response.getStatusCode().value(),response.getBody());
        JsonNode created=json.readTree(response.getBody()); int id=created.path("id").asInt();
        assertAllFields(created,input); assertAllFields(get("/api/v1/jobs/"+id),input);
        assertEquals(id,get("/api/v1/jobs?title=REST&category=Science&min_price=90000&max_price=120000").at("/data/0/id").asInt());
        assertEquals(id,get("/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5").at("/features/0/properties/id").asInt());
        Map<String,Object> update=new HashMap<>(); update.put("title","Updated REST discovery"); update.put("description",null); update.put("minSalary",null); update.put("latitude",37.775);
        JsonNode updated=json.readTree(request(HttpMethod.PUT,"/api/v1/jobs/"+id,owner,update).getBody());
        assertEquals(input.get("description"),updated.path("description").asText()); assertEquals(90000,updated.path("minSalary").asInt());
        assertEquals(37.775,updated.path("latitude").asDouble()); assertEquals(-122.4194,updated.path("longitude").asDouble());
        error(request(HttpMethod.PUT,"/api/v1/jobs/"+id,owner,Map.of("endsAt","2020-01-01T00:00:00")),"VALIDATION_ERROR");
        assertEquals("2027-02-01T10:00:00Z",get("/api/v1/jobs/"+id).path("endsAt").asText());
        Map<String,Object> missingCompany=new HashMap<>(input); missingCompany.put("companyId",999999);
        assertEquals(404,request(HttpMethod.POST,"/api/v1/jobs",admin,missingCompany).getStatusCode().value());
    }

    @Test void graphqlWritesUseTheSameFieldsAndNullUpdateRules() throws Exception {
        Map<String,Object> input=fullInput("GraphQL discovery job");
        JsonNode response=graph("mutation($input:CreateJobInput!){createJob(input:$input){id title description category status location skillsRequired employmentType minSalary maxSalary salaryRange latitude longitude startsAt endsAt}}",Map.of("input",input),owner);
        assertFalse(response.has("errors"),response.toString()); JsonNode created=response.at("/data/createJob"); assertAllFields(created,input);
        int id=created.path("id").asInt(); assertAllFields(get("/api/v1/jobs/"+id),input);
        Map<String,Object> patch=new HashMap<>(); patch.put("title",null); patch.put("startsAt","2027-02-01T09:30:00Z"); patch.put("longitude",-122.4190);
        JsonNode result=graph("mutation($id:ID!,$input:UpdateJobInput!){updateJob(id:$id,input:$input){id title startsAt endsAt latitude longitude}}",Map.of("id",Integer.toString(id),"input",patch),owner);
        assertFalse(result.has("errors"),result.toString()); assertEquals(input.get("title"),result.at("/data/updateJob/title").asText());
        assertEquals("2027-02-01T09:30:00Z",get("/api/v1/jobs/"+id).path("startsAt").asText());
    }

    @Test void salaryMigrationBackfillsNumericDisplayAndPreservesLegacyOnlyText() {
        jdbc.update("UPDATE jobs SET salary_range='Legacy display text'");
        var migration = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V2__numeric_salary_display.sql"));
        migration.execute(jdbc.getDataSource());
        assertEquals("Legacy display text", jobs.findById(fixtures.get(0).getId()).orElseThrow().getSalaryRange());
        assertEquals("100 - 300", jobs.findById(fixtures.get(1).getId()).orElseThrow().getSalaryRange());
        assertEquals("Up to 200", jobs.findById(fixtures.get(5).getId()).orElseThrow().getSalaryRange());
        assertEquals("50+", jobs.findById(fixtures.get(7).getId()).orElseThrow().getSalaryRange());
        assertEquals(fixtures.size(), jobs.count());
    }

    @Test void salaryBoundsOverrideLegacyTextAndUnknownBoundsAreExcludedOnlyWhenFiltered() throws Exception {
        Map<String,Object> input=fullInput("Salary policy job"); input.remove("maxSalary"); input.put("salaryRange","contradictory text");
        JsonNode created=json.readTree(request(HttpMethod.POST,"/api/v1/jobs",owner,input).getBody()); assertEquals("90000+",created.path("salaryRange").asText());
        assertEquals(1,get("/api/v1/jobs?title=Salary&min_price=90000").path("data").size());
        assertEquals(0,get("/api/v1/jobs?title=Salary&max_price=150000").path("data").size());
        input.remove("minSalary"); input.put("title","Legacy salary only");
        JsonNode legacy=json.readTree(request(HttpMethod.POST,"/api/v1/jobs",owner,input).getBody()); assertEquals("contradictory text",legacy.path("salaryRange").asText());
        assertTrue(legacy.path("minSalary").isNull()); assertEquals(0,get("/api/v1/jobs?title=Legacy&min_price=1").path("data").size());
    }

    @Test void fiveKmBoundaryFixturesProduceFullGraphqlAndGeoJsonResults() throws Exception {
        jobs.deleteAll();
        double homeLat=37.7749, homeLon=-122.4194;
        List<Job> points=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Job job=job(new String[]{"At home","Inside radius","On radius","Outside radius"}[i]);
            job.setLatitude(homeLat+Math.toDegrees(new double[]{0,4.9,5,5.1}[i]/6371.0)); job.setLongitude(homeLon); points.add(jobs.saveAndFlush(job));
        }
        jobs.saveAndFlush(job("No coordinates"));
        JsonNode response=graph("query NearbyJobs($lat:Float!,$lon:Float!){jobsNear(latitude:$lat,longitude:$lon,radiusKm:5){id title company{id companyName}}}",Map.of("lat",homeLat,"lon",homeLon),null);
        var expected=json.createArrayNode();
        for(int i=0;i<3;i++) expected.add(json.valueToTree(Map.of("id",points.get(i).getId().toString(),"title",points.get(i).getTitle(),"company",Map.of("id",company.getId().toString(),"companyName",company.getCompanyName()))));
        assertEquals(expected,response.at("/data/jobsNear"));
        JsonNode map=get("/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5"); assertEquals("FeatureCollection",map.path("type").asText()); assertEquals(3,map.path("features").size());
        assertEquals(homeLon,map.at("/features/0/geometry/coordinates/0").asDouble()); assertEquals(homeLat,map.at("/features/0/geometry/coordinates/1").asDouble());
        assertEquals(0,graph("{jobsNear(latitude:0,longitude:0,radiusKm:5){id}}",Map.of(),null).at("/data/jobsNear").size());
        assertEquals(4,get("/api/v1/jobs/map").path("features").size());
    }

    @Test void poleAndDateLineSearchesDoNotDropMatchingJobs() throws Exception {
        jobs.deleteAll();
        Job west=job("West dateline"); west.setLatitude(0.0); west.setLongitude(-179.99); west=jobs.saveAndFlush(west);
        Job east=job("East dateline"); east.setLatitude(0.0); east.setLongitude(179.99); east=jobs.saveAndFlush(east);
        Job pole=job("North pole"); pole.setLatitude(90.0); pole.setLongitude(150.0); pole=jobs.saveAndFlush(pole);
        assertEquals(Set.of(west.getId(),east.getId()),new HashSet<>(service.findJobsWithinRadius(0,180,5).stream().map(Job::getId).toList()));
        assertEquals(Set.of(west.getId(),east.getId()),new HashSet<>(service.findJobsInBoundingBox(-1,179,1,-179).stream().map(Job::getId).toList()));
        assertEquals(List.of(pole.getId()),service.findJobsWithinRadius(90,-30,1).stream().map(Job::getId).toList());
        assertEquals(3,service.findJobsWithinRadius(0,0,21000).size());
        Job meridian=job("Exact dateline"); meridian.setLatitude(0.0); meridian.setLongitude(-180.0); meridian=jobs.saveAndFlush(meridian);
        assertTrue(service.findJobsInBoundingBox(-1,170,1,180).stream().map(Job::getId).toList().contains(meridian.getId()));
        assertThrows(com.jobportal.exception.ValidationException.class,()->service.findJobsWithinRadius(Double.NaN,0,5));
        assertThrows(com.jobportal.exception.ValidationException.class,()->service.findJobsWithinRadius(0,0,Double.POSITIVE_INFINITY));
        for(String query:List.of("?latitude=0","?latitude=0&longitude=0&radius_km=5&min_lat=-1&min_lon=-1&max_lat=1&max_lon=1","?min_lat=2&min_lon=0&max_lat=1&max_lon=1","?latitude=91&longitude=0&radius_km=5","?latitude=0&longitude=0&radius_km=0"))
            error(request(HttpMethod.GET,"/api/v1/jobs/map"+query,null,null),"VALIDATION_ERROR");
    }

    private Job job(String title) { Job job=new Job(); job.setCompanyId(company.getId()); job.setTitle(title); job.setDescription("A sufficiently detailed synthetic job description."); return job; }
    private User user(String email,User.Role role) { User u=new User(); u.setEmail(email); u.setPassword("unused-test-password"); u.setRole(role); return users.saveAndFlush(u); }
    private Company company(User user,String name) { Company c=new Company(); c.setUserId(user.getId()); c.setCompanyName(name); return companies.saveAndFlush(c); }
    private Map<String,Object> nullableAfter(String after) { Map<String,Object> result=new HashMap<>(); result.put("after",after); return result; }
    private Map<String,Object> fullInput(String title) {
        Map<String,Object> input=new LinkedHashMap<>(); input.put("companyId",company.getId()); input.put("title",title); input.put("description","Build and maintain the complete recruitment platform APIs.");
        input.put("category","Science"); input.put("status","OPEN"); input.put("location","San Francisco"); input.put("skillsRequired","Java, PostgreSQL"); input.put("employmentType","Full-time");
        input.put("minSalary",90000); input.put("maxSalary",120000); input.put("salaryRange","ignored legacy input"); input.put("latitude",37.7749); input.put("longitude",-122.4194);
        input.put("startsAt","2027-02-01T09:00:00Z"); input.put("endsAt","2027-02-01T10:00:00Z"); return input;
    }
    private void assertAllFields(JsonNode result,Map<String,Object> input) {
        input.forEach((key,value)-> {if(!Set.of("companyId","salaryRange").contains(key)) assertEquals(json.valueToTree(value),result.path(key),key+": "+result);});
        assertEquals("90000 - 120000",result.path("salaryRange").asText());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private Comparator<Job> comparator(String sort,String order) {
        return (a,b)-> {
            Comparable x=value(a,sort), y=value(b,sort); int c=x==null?(y==null?0:1):y==null?-1:(order.equals("asc")?x.compareTo(y):y.compareTo(x));
            return c!=0?c:order.equals("asc")?a.getId().compareTo(b.getId()):b.getId().compareTo(a.getId());
        };
    }
    private Comparable<?> value(Job job,String sort) {return switch(sort){case "id"->job.getId();case "title"->job.getTitle();case "min_price"->job.getMinSalary();case "max_price"->job.getMaxSalary();default->job.getCreatedAt();};}
    private ResponseEntity<String> request(HttpMethod method,String path,User user,Object body) {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); if(user!=null) headers.setBearerAuth(jwt.generateToken(user.getEmail(),user.getRole().name()));
        return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
    }
    private JsonNode get(String path) throws Exception {var response=request(HttpMethod.GET,path,null,null); assertEquals(200,response.getStatusCode().value(),response.getBody()); return json.readTree(response.getBody());}
    private JsonNode graph(String query,Map<String,Object> variables,User user) throws Exception {var response=request(HttpMethod.POST,"/graphql/query",user,Map.of("query",query,"variables",variables)); assertEquals(200,response.getStatusCode().value(),response.getBody()); return json.readTree(response.getBody());}
    private void error(ResponseEntity<String> response,String code) throws Exception {assertEquals(400,response.getStatusCode().value(),response.getBody()); assertEquals(code,json.readTree(response.getBody()).path("code").asText());}
}
