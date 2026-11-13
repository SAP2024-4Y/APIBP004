package com.jobportal;

import com.jobportal.entity.*;
import com.jobportal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements ApplicationRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewSlotRepository slotRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("demo@jobportal.com")) {
            return; // Already seeded — idempotent
        }

        // Demo user (ADMIN, for evaluator)
        User demo = user("demo@jobportal.com", "demo-password", User.Role.ADMIN);

        // Admin
        User admin = user("admin@jobportal.com", "admin-pass", User.Role.ADMIN);

        // Company users
        User cu1 = user("hr@techcorp.com", "password123", User.Role.COMPANY);
        User cu2 = user("careers@innovatelab.com", "password123", User.Role.COMPANY);
        User cu3 = user("jobs@globaltech.com", "password123", User.Role.COMPANY);
        User cu4 = user("recruit@financeplus.com", "password123", User.Role.COMPANY);
        User cu5 = user("hiring@healthnet.com", "password123", User.Role.COMPANY);

        // Candidate users
        User cand1 = user("john.doe@email.com", "password123", User.Role.CANDIDATE);
        User cand2 = user("jane.smith@email.com", "password123", User.Role.CANDIDATE);
        User cand3 = user("mike.johnson@email.com", "password123", User.Role.CANDIDATE);
        User cand4 = user("sarah.wilson@email.com", "password123", User.Role.CANDIDATE);
        User cand5 = user("alex.brown@email.com", "password123", User.Role.CANDIDATE);

        // Companies with real-world coordinates
        Company c1 = company(cu1.getId(), "TechCorp Solutions", "Software Development",
                "123 Innovation Drive, San Francisco, CA 94105", true);
        Company c2 = company(cu2.getId(), "InnovateLab Inc", "AI & Machine Learning",
                "456 Tech Street, New York, NY 10001", true);
        Company c3 = company(cu3.getId(), "GlobalTech Systems", "Enterprise Software",
                "789 Business Ave, Austin, TX 73301", true);
        Company c4 = company(cu4.getId(), "FinancePlus Ltd", "Financial Services",
                "321 Wall Street, New York, NY 10005", true);
        Company c5 = company(cu5.getId(), "HealthNet Corp", "Healthcare Technology",
                "654 Medical Center Dr, Boston, MA 02115", true);

        // Candidates
        candidate(cand1.getId(), "John Doe", "+1-555-0101", "San Francisco, CA",
                "3 years full-stack dev with React, Node.js, PostgreSQL");
        candidate(cand2.getId(), "Jane Smith", "+1-555-0102", "New York, NY",
                "5 years frontend dev specializing in React and Vue.js");
        candidate(cand3.getId(), "Mike Johnson", "+1-555-0103", "Austin, TX",
                "2 years backend dev with Java Spring Boot, microservices");
        candidate(cand4.getId(), "Sarah Wilson", "+1-555-0104", "Seattle, WA",
                "4 years DevOps engineering with Docker, Kubernetes");
        candidate(cand5.getId(), "Alex Brown", "+1-555-0105", "Boston, MA",
                "6 years data science with Python, ML, big data");

        LocalDateTime now = LocalDateTime.now();

        // 25+ Jobs with real coordinates, categories, statuses, salary ranges, and slots
        // San Francisco area (lat ~37.77, lon ~-122.41)
        job(c1.getId(), "Senior Full Stack Developer", "Build cutting-edge web applications using modern technologies. Work with React, Node.js, and cloud infrastructure.",
                "React, Node.js, PostgreSQL, AWS, Docker", "Technology", "OPEN",
                37.7749, -122.4194, 90000, 130000, "Full-time",
                now.plusDays(10), now.plusDays(10).plusHours(1));

        job(c1.getId(), "Frontend React Developer", "Join our frontend team building responsive UIs. TypeScript required.",
                "React, TypeScript, CSS3, HTML5, Webpack, Jest", "Technology", "OPEN",
                37.7751, -122.4180, 70000, 95000, "Full-time",
                now.plusDays(12), now.plusDays(12).plusHours(1));

        job(c1.getId(), "DevOps Engineer", "Manage cloud infrastructure and CI/CD pipelines for our platform.",
                "Docker, Kubernetes, Jenkins, AWS, Terraform", "Technology", "OPEN",
                37.7760, -122.4200, 95000, 130000, "Full-time",
                now.plusDays(15), now.plusDays(15).plusHours(1));

        // New York area (lat ~40.71, lon ~-74.01)
        job(c2.getId(), "Machine Learning Engineer", "Work on NLP and computer vision projects with cutting-edge AI.",
                "Python, TensorFlow, PyTorch, Scikit-learn, Statistics", "Technology", "OPEN",
                40.7128, -74.0060, 110000, 150000, "Full-time",
                now.plusDays(8), now.plusDays(8).plusHours(1));

        job(c2.getId(), "Data Scientist", "Analyze large datasets and build predictive models for business insights.",
                "Python, R, SQL, Pandas, Tableau, Spark", "Technology", "OPEN",
                40.7135, -74.0045, 90000, 120000, "Full-time",
                now.plusDays(20), now.plusDays(20).plusHours(1));

        job(c4.getId(), "Quantitative Analyst", "Develop quantitative trading models and risk analytics.",
                "Python, R, C++, Statistics, Financial Modeling", "Finance", "OPEN",
                40.7057, -74.0088, 120000, 170000, "Full-time",
                now.plusDays(14), now.plusDays(14).plusHours(1));

        job(c4.getId(), "Financial Software Engineer", "Build trading platforms and financial data pipelines.",
                "Java, Spring Boot, Kafka, PostgreSQL, FIX protocol", "Finance", "OPEN",
                40.7062, -74.0080, 100000, 140000, "Full-time",
                now.plusDays(18), now.plusDays(18).plusHours(1));

        job(c4.getId(), "Risk Management Analyst", "Analyze credit and market risk for portfolio management.",
                "Excel, Python, VBA, Bloomberg, Risk Metrics", "Finance", "CLOSED",
                40.7065, -74.0070, 85000, 110000, "Full-time",
                null, null);

        // Austin area (lat ~30.26, lon ~-97.74)
        job(c3.getId(), "Backend Java Developer", "Develop enterprise backend systems with microservices architecture.",
                "Java, Spring Boot, Microservices, Kubernetes, MongoDB", "Technology", "OPEN",
                30.2672, -97.7431, 80000, 115000, "Full-time",
                now.plusDays(11), now.plusDays(11).plusHours(1));

        job(c3.getId(), "Cloud Architect", "Design and implement enterprise cloud solutions. Lead technical teams.",
                "AWS, Azure, Microservices, Docker, System Design", "Technology", "OPEN",
                30.2678, -97.7420, 135000, 175000, "Full-time",
                now.plusDays(25), now.plusDays(25).plusHours(2));

        job(c3.getId(), "Product Manager - SaaS", "Define product roadmap for enterprise SaaS platform.",
                "Agile, JIRA, Analytics, User Research, SQL", "Technology", "OPEN",
                30.2665, -97.7440, 90000, 120000, "Full-time",
                now.plusDays(22), now.plusDays(22).plusHours(1));

        // Boston area (lat ~42.36, lon ~-71.06)
        job(c5.getId(), "Healthcare Data Engineer", "Build data pipelines for clinical analytics platform.",
                "Python, Apache Spark, FHIR, PostgreSQL, AWS", "Healthcare", "OPEN",
                42.3601, -71.0589, 95000, 125000, "Full-time",
                now.plusDays(16), now.plusDays(16).plusHours(1));

        job(c5.getId(), "Clinical Software Developer", "Develop EHR integrations and clinical decision support tools.",
                "Java, HL7, FHIR, Spring Boot, PostgreSQL", "Healthcare", "OPEN",
                42.3598, -71.0600, 85000, 115000, "Full-time",
                now.plusDays(19), now.plusDays(19).plusHours(1));

        job(c5.getId(), "UI/UX Designer - Healthcare", "Design intuitive interfaces for clinical workflows.",
                "Figma, Adobe XD, User Research, Accessibility, HTML/CSS", "Healthcare", "OPEN",
                42.3610, -71.0575, 70000, 95000, "Full-time",
                now.plusDays(30), now.plusDays(30).plusHours(1));

        // Seattle area (lat ~47.60, lon ~-122.33)
        job(c1.getId(), "Mobile App Developer", "Build cross-platform mobile apps for iOS and Android.",
                "React Native, Flutter, iOS, Android, Firebase", "Technology", "OPEN",
                47.6062, -122.3321, 85000, 115000, "Full-time",
                now.plusDays(9), now.plusDays(9).plusHours(1));

        // Chicago area (lat ~41.88, lon ~-87.63)
        job(c3.getId(), "Supply Chain Software Engineer", "Build logistics and supply chain management systems.",
                "Java, Python, SAP, SQL, REST APIs", "Technology", "OPEN",
                41.8781, -87.6298, 85000, 110000, "Full-time",
                now.plusDays(17), now.plusDays(17).plusHours(1));

        // Los Angeles area (lat ~34.05, lon ~-118.24)
        job(c2.getId(), "Computer Vision Engineer", "Develop real-time computer vision systems for media applications.",
                "Python, OpenCV, TensorFlow, C++, CUDA", "Technology", "OPEN",
                34.0522, -118.2437, 105000, 145000, "Full-time",
                now.plusDays(13), now.plusDays(13).plusHours(1));

        // London, UK (lat ~51.50, lon ~-0.12)
        job(c4.getId(), "Fintech Engineer - London", "Build payment systems and banking APIs for European market.",
                "Java, Spring Boot, REST, Kafka, PostgreSQL", "Finance", "OPEN",
                51.5074, -0.1278, 75000, 105000, "Full-time",
                now.plusDays(21), now.plusDays(21).plusHours(1));

        // Berlin, Germany (lat ~52.52, lon ~13.40)
        job(c3.getId(), "Software Engineer - Berlin", "Build distributed systems for global logistics platform.",
                "Go, Python, gRPC, Kubernetes, Cassandra", "Technology", "OPEN",
                52.5200, 13.4050, 65000, 90000, "Full-time",
                now.plusDays(28), now.plusDays(28).plusHours(1));

        // Remote jobs
        job(c1.getId(), "Senior Backend Engineer (Remote)", "Design and build scalable APIs for millions of users.",
                "Node.js, TypeScript, PostgreSQL, Redis, AWS", "Technology", "OPEN",
                37.7749, -122.4194, 100000, 145000, "Remote",
                now.plusDays(7), now.plusDays(7).plusHours(1));

        job(c2.getId(), "ML Research Engineer (Remote)", "Research and implement state-of-the-art ML models.",
                "Python, PyTorch, Transformers, Research, Papers", "Technology", "OPEN",
                40.7128, -74.0060, 130000, 175000, "Remote",
                now.plusDays(23), now.plusDays(23).plusHours(1));

        // Education domain
        job(c5.getId(), "EdTech Platform Engineer", "Build scalable learning management system.",
                "React, Node.js, PostgreSQL, WebRTC, AWS", "Education", "OPEN",
                42.3601, -71.0589, 80000, 105000, "Full-time",
                now.plusDays(26), now.plusDays(26).plusHours(1));

        // Retail domain
        job(c3.getId(), "E-Commerce Backend Engineer", "Build high-performance retail platform handling millions of transactions.",
                "Java, Spring Boot, Redis, MySQL, Elasticsearch", "Retail", "OPEN",
                30.2672, -97.7431, 85000, 115000, "Full-time",
                now.plusDays(24), now.plusDays(24).plusHours(1));

        // Part-time / Contract jobs
        job(c1.getId(), "Part-time React Developer", "Develop new features for our flagship product on a part-time basis.",
                "React, JavaScript, Git, REST APIs", "Technology", "OPEN",
                37.7749, -122.4194, 40000, 55000, "Part-time",
                now.plusDays(27), now.plusDays(27).plusHours(1));

        job(c4.getId(), "Contract: Trading System QA", "Test and validate algorithmic trading systems.",
                "Python, Selenium, Testing, Financial Markets", "Finance", "DRAFT",
                40.7057, -74.0088, 70000, 90000, "Contract",
                null, null);

        // Add standalone interview slots for Calendar API testing
        slotRepository.save(makeSlot(1, cand1.getId(), now.plusDays(10), now.plusDays(10).plusHours(1)));
        slotRepository.save(makeSlot(2, cand2.getId(), now.plusDays(12), now.plusDays(12).plusHours(1)));
        slotRepository.save(makeSlot(3, cand3.getId(), now.plusDays(15), now.plusDays(15).plusHours(1)));
        slotRepository.save(makeSlot(4, cand1.getId(), now.plusDays(8), now.plusDays(8).plusHours(1)));
        slotRepository.save(makeSlot(5, cand4.getId(), now.plusDays(20), now.plusDays(20).plusHours(1)));
    }

    private User user(String email, String password, User.Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        return userRepository.save(u);
    }

    private Company company(Integer userId, String name, String industry, String address, boolean approved) {
        Company c = new Company();
        c.setUserId(userId);
        c.setCompanyName(name);
        c.setIndustry(industry);
        c.setAddress(address);
        c.setApproved(approved);
        return companyRepository.save(c);
    }

    private void candidate(Integer userId, String fullName, String phone, String location, String experience) {
        Candidate c = new Candidate();
        c.setUserId(userId);
        c.setFullName(fullName);
        c.setPhone(phone);
        c.setLocation(location);
        c.setExperience(experience);
        candidateRepository.save(c);
    }

    private void job(Integer companyId, String title, String description, String skills,
                     String category, String status, Double lat, Double lon,
                     Integer minSalary, Integer maxSalary, String employmentType,
                     LocalDateTime startsAt, LocalDateTime endsAt) {
        Job j = new Job();
        j.setCompanyId(companyId);
        j.setTitle(title);
        j.setDescription(description);
        j.setSkillsRequired(skills);
        j.setCategory(category);
        j.setStatus(status);
        j.setLatitude(lat);
        j.setLongitude(lon);
        j.setMinSalary(minSalary);
        j.setMaxSalary(maxSalary);
        j.setSalaryRange(minSalary + " - " + maxSalary);
        j.setEmploymentType(employmentType);
        j.setStartsAt(startsAt);
        j.setEndsAt(endsAt);
        j.setCreatedAt(LocalDateTime.now());
        // Derive location from coordinates
        j.setLocation(deriveLocation(lat, lon));
        jobRepository.save(j);
    }

    private InterviewSlot makeSlot(Integer jobId, Integer candidateId, LocalDateTime startsAt, LocalDateTime endsAt) {
        InterviewSlot s = new InterviewSlot();
        s.setJobId(jobId);
        s.setCandidateId(candidateId);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setStatus("SCHEDULED");
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    private String deriveLocation(Double lat, Double lon) {
        if (lat == null || lon == null) return "Remote";
        if (lat > 47.0 && lat < 48.0 && lon < -121.0) return "Seattle, WA";
        if (lat > 37.5 && lat < 38.0 && lon < -122.0) return "San Francisco, CA";
        if (lat > 34.0 && lat < 35.0 && lon < -117.0) return "Los Angeles, CA";
        if (lat > 41.8 && lat < 42.0 && lon < -87.5) return "Chicago, IL";
        if (lat > 30.0 && lat < 31.0 && lon < -97.0) return "Austin, TX";
        if (lat > 40.5 && lat < 41.0 && lon < -73.0) return "New York, NY";
        if (lat > 42.2 && lat < 42.5 && lon < -70.8) return "Boston, MA";
        if (lat > 51.0 && lat < 52.0 && lon < 0.0) return "London, UK";
        if (lat > 52.0 && lat < 53.0 && lon > 13.0) return "Berlin, Germany";
        return "Remote";
    }
}
