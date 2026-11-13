# PostgreSQL Database Configuration for Job Portal

To connect your Job Portal application to PostgreSQL, you need to configure these properties:

## Database Connection Settings:
spring.datasource.url=jdbc:postgresql://localhost:5432/jobportal
spring.datasource.username=job_portal_user
spring.datasource.password=your_secure_password
spring.datasource.driver-class-name=org.postgresql.Driver

## Setup Steps:

1. Install PostgreSQL on your system
2. Create the database and user:
   ```sql
   CREATE DATABASE jobportal;
   CREATE USER job_portal_user WITH PASSWORD 'your_secure_password';
   GRANT ALL PRIVILEGES ON DATABASE jobportal TO job_portal_user;
   ```

3. Run the database schema script:
   ```bash
   psql -U job_portal_user -d jobportal -f database/jobportal.sql
   psql -U job_portal_user -d jobportal -f database/seed-data.sql
   ```

4. Update the application.properties file in src/main/resources/ with your actual:
   - Database host (if not localhost)
   - Database port (if not 5432)
   - Username and password

## Alternative: Use your existing PostgreSQL setup
If you have PostgreSQL already running, you can:
1. Create the job_portal database
2. Update the connection settings to match your existing PostgreSQL configuration
3. Use your existing username/password