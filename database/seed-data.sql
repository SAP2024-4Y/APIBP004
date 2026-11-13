-- Job Portal Database - Sample Data
-- This script inserts sample data for testing and development

-- Clear existing data (optional - uncomment if needed)
-- TRUNCATE TABLE applications, resumes, jobs, companies, candidates, users RESTART IDENTITY CASCADE;

-- Sample Users
INSERT INTO users (email, password, role, created_at) VALUES
-- Candidates (password is 'password123' encoded with BCrypt)
('john.doe@email.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'CANDIDATE', NOW()),
('jane.smith@email.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'CANDIDATE', NOW()),
('mike.johnson@email.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'CANDIDATE', NOW()),
('sarah.wilson@email.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'CANDIDATE', NOW()),
('alex.brown@email.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'CANDIDATE', NOW()),

-- Companies (password is 'password123' encoded with BCrypt)
('hr@techcorp.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'COMPANY', NOW()),
('careers@innovatelab.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'COMPANY', NOW()),
('jobs@globaltech.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'COMPANY', NOW()),
('recruit@startupxyz.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'COMPANY', NOW()),
('hiring@megacorp.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'COMPANY', NOW()),

-- Admin user
('admin@jobportal.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFGjO6NzUfkNDUJyOJoOKkOi', 'ADMIN', NOW());

-- Sample Candidates
INSERT INTO candidates (user_id, full_name, phone, location, experience) VALUES
(1, 'John Doe', '+1-555-0101', 'San Francisco, CA', '3 years of full-stack development experience with React, Node.js, and PostgreSQL. Previously worked at a fintech startup.'),
(2, 'Jane Smith', '+1-555-0102', 'New York, NY', '5 years of frontend development specializing in React, Vue.js, and modern CSS frameworks. Strong UX/UI design skills.'),
(3, 'Mike Johnson', '+1-555-0103', 'Austin, TX', '2 years of backend development with Java Spring Boot, microservices, and cloud technologies. AWS certified.'),
(4, 'Sarah Wilson', '+1-555-0104', 'Seattle, WA', '4 years of DevOps engineering experience with Docker, Kubernetes, CI/CD pipelines, and infrastructure as code.'),
(5, 'Alex Brown', '+1-555-0105', 'Boston, MA', '6 years of data science experience with Python, Machine Learning, and big data technologies. PhD in Computer Science.');

-- Sample Companies
INSERT INTO companies (user_id, company_name, industry, address, approved) VALUES
(6, 'TechCorp Solutions', 'Software Development', '123 Innovation Drive, San Francisco, CA 94105', true),
(7, 'InnovateLab Inc', 'AI & Machine Learning', '456 Tech Street, New York, NY 10001', true),
(8, 'GlobalTech Systems', 'Enterprise Software', '789 Business Ave, Austin, TX 73301', true),
(9, 'StartupXYZ', 'Mobile Applications', '321 Startup Lane, Seattle, WA 98101', false),
(10, 'MegaCorp International', 'Cloud Computing', '654 Corporate Blvd, Boston, MA 02101', true);

-- Sample Jobs
INSERT INTO jobs (company_id, title, description, skills_required, salary_range, location, employment_type, created_at) VALUES
-- TechCorp Solutions Jobs
(1, 'Senior Full Stack Developer', 'We are looking for an experienced full-stack developer to join our growing team. You will work on cutting-edge web applications using modern technologies.', 'React, Node.js, PostgreSQL, AWS, Docker, Git', '$90,000 - $120,000', 'San Francisco, CA', 'Full-time', NOW() - INTERVAL '2 days'),
(1, 'Frontend Developer', 'Join our frontend team to build beautiful and responsive user interfaces. Experience with modern JavaScript frameworks is required.', 'React, TypeScript, CSS3, HTML5, Webpack, Jest', '$70,000 - $90,000', 'San Francisco, CA', 'Full-time', NOW() - INTERVAL '1 day'),

-- InnovateLab Inc Jobs
(2, 'Machine Learning Engineer', 'Work on cutting-edge AI projects including natural language processing and computer vision. Strong mathematical background required.', 'Python, TensorFlow, PyTorch, Scikit-learn, SQL, Statistics', '$100,000 - $140,000', 'New York, NY', 'Full-time', NOW() - INTERVAL '3 days'),
(2, 'Data Scientist', 'Analyze large datasets to extract business insights and build predictive models. Experience with big data technologies preferred.', 'Python, R, SQL, Pandas, Jupyter, Tableau, Spark', '$85,000 - $115,000', 'New York, NY', 'Full-time', NOW()),

-- GlobalTech Systems Jobs
(3, 'Backend Developer', 'Develop and maintain enterprise-grade backend systems. Experience with microservices architecture and cloud platforms required.', 'Java, Spring Boot, Microservices, Kubernetes, AWS, MongoDB', '$80,000 - $110,000', 'Austin, TX', 'Full-time', NOW() - INTERVAL '1 day'),
(3, 'DevOps Engineer', 'Build and maintain CI/CD pipelines, manage cloud infrastructure, and ensure system reliability and scalability.', 'Docker, Kubernetes, Jenkins, AWS, Terraform, Linux, Python', '$95,000 - $125,000', 'Austin, TX', 'Full-time', NOW()),

-- StartupXYZ Jobs (Company not yet approved)
(4, 'Mobile App Developer', 'Develop cross-platform mobile applications for iOS and Android. Startup environment with equity opportunities.', 'React Native, Flutter, iOS, Android, Firebase, Git', '$65,000 - $85,000', 'Seattle, WA', 'Full-time', NOW() - INTERVAL '5 days'),

-- MegaCorp International Jobs
(5, 'Cloud Architect', 'Design and implement enterprise cloud solutions. Lead technical teams and make architectural decisions for large-scale systems.', 'AWS, Azure, Microservices, Docker, Kubernetes, System Design', '$130,000 - $160,000', 'Boston, MA', 'Full-time', NOW() - INTERVAL '1 day'),
(5, 'Software Engineer Intern', 'Summer internship program for computer science students. Work on real projects with mentorship from senior engineers.', 'Java, Python, Git, Agile, Problem Solving', '$25 - $30 per hour', 'Boston, MA', 'Internship', NOW()),
(5, 'Product Manager', 'Lead product development lifecycle from conception to launch. Work closely with engineering and design teams.', 'Product Management, Agile, Analytics, SQL, Communication', '$110,000 - $140,000', 'Boston, MA', 'Full-time', NOW() - INTERVAL '2 days');

-- Sample Resumes
INSERT INTO resumes (candidate_id, file_path, uploaded_at) VALUES
(1, '/uploads/resumes/john_doe_resume.pdf', NOW() - INTERVAL '1 day'),
(2, '/uploads/resumes/jane_smith_resume.pdf', NOW() - INTERVAL '2 days'),
(3, '/uploads/resumes/mike_johnson_resume.pdf', NOW() - INTERVAL '1 day'),
(4, '/uploads/resumes/sarah_wilson_resume.pdf', NOW() - INTERVAL '3 days'),
(5, '/uploads/resumes/alex_brown_resume.pdf', NOW() - INTERVAL '1 day');

-- Sample Applications
INSERT INTO applications (candidate_id, job_id, status, applied_at) VALUES
-- John Doe applications
(1, 1, 'SUBMITTED', NOW() - INTERVAL '1 day'),
(1, 5, 'REVIEWED', NOW() - INTERVAL '2 days'),

-- Jane Smith applications
(2, 2, 'SHORTLISTED', NOW() - INTERVAL '1 day'),
(2, 4, 'SUBMITTED', NOW() - INTERVAL '3 days'),

-- Mike Johnson applications
(3, 5, 'SUBMITTED', NOW() - INTERVAL '1 day'),
(3, 6, 'REVIEWED', NOW() - INTERVAL '2 days'),
(3, 1, 'REJECTED', NOW() - INTERVAL '5 days'),

-- Sarah Wilson applications
(4, 6, 'SHORTLISTED', NOW() - INTERVAL '1 day'),
(4, 8, 'SUBMITTED', NOW() - INTERVAL '2 days'),

-- Alex Brown applications
(5, 3, 'HIRED', NOW() - INTERVAL '7 days'),
(5, 4, 'SUBMITTED', NOW() - INTERVAL '1 day');

-- Update sequences to ensure they continue from the correct values
SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1));
SELECT setval('candidates_id_seq', COALESCE((SELECT MAX(id) FROM candidates), 1));
SELECT setval('companies_id_seq', COALESCE((SELECT MAX(id) FROM companies), 1));
SELECT setval('jobs_id_seq', COALESCE((SELECT MAX(id) FROM jobs), 1));
SELECT setval('resumes_id_seq', COALESCE((SELECT MAX(id) FROM resumes), 1));
SELECT setval('applications_id_seq', COALESCE((SELECT MAX(id) FROM applications), 1));

-- Verify data insertion
SELECT 'Data insertion completed successfully!' as message;
SELECT 'Users created: ' || COUNT(*) as user_count FROM users;
SELECT 'Companies created: ' || COUNT(*) as company_count FROM companies;
SELECT 'Jobs created: ' || COUNT(*) as job_count FROM jobs;
SELECT 'Applications created: ' || COUNT(*) as application_count FROM applications;