package com.jobportal.repository;

import com.jobportal.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Integer> {

    List<Job> findByCompanyId(Integer companyId);
    List<Job> findByLocationContainingIgnoreCase(String location);
    List<Job> findByTitleContainingIgnoreCase(String title);

    // Pagination support
    Page<Job> findByCompanyId(Integer companyId, Pageable pageable);
    Page<Job> findByLocationContainingIgnoreCase(String location, Pageable pageable);
    Page<Job> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // Combined filter search with pagination
    @Query("SELECT j FROM Job j WHERE " +
           "(:title IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
           "(:skills IS NULL OR LOWER(j.skillsRequired) LIKE LOWER(CONCAT('%', :skills, '%')))")
    Page<Job> searchJobs(
        @Param("title") String title,
        @Param("location") String location,
        @Param("skills") String skills,
        Pageable pageable
    );

    // Advanced filter for List API with cursor-based pagination support
    @Query("SELECT j FROM Job j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:category IS NULL OR j.category = :category) AND " +
           "(:minPrice IS NULL OR j.minSalary >= :minPrice) AND " +
           "(:maxPrice IS NULL OR j.maxSalary <= :maxPrice) AND " +
           "(:afterId IS NULL OR j.id > :afterId)")
    List<Job> findWithFiltersAndCursor(
        @Param("status") String status,
        @Param("category") String category,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        @Param("afterId") Integer afterId,
        Pageable pageable
    );

    @Query("SELECT COUNT(j) FROM Job j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:category IS NULL OR j.category = :category) AND " +
           "(:minPrice IS NULL OR j.minSalary >= :minPrice) AND " +
           "(:maxPrice IS NULL OR j.maxSalary <= :maxPrice) AND " +
           "(:afterId IS NULL OR j.id > :afterId)")
    long countWithFiltersAndCursor(
        @Param("status") String status,
        @Param("category") String category,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        @Param("afterId") Integer afterId
    );

    // Jobs with non-null coordinates
    @Query("SELECT j FROM Job j WHERE j.latitude IS NOT NULL AND j.longitude IS NOT NULL")
    List<Job> findAllWithCoordinates();

    // Bounding-box geospatial query
    @Query("SELECT j FROM Job j WHERE " +
           "j.latitude IS NOT NULL AND j.longitude IS NOT NULL AND " +
           "j.latitude >= :minLat AND j.latitude <= :maxLat AND " +
           "j.longitude >= :minLon AND j.longitude <= :maxLon")
    List<Job> findByBoundingBox(
        @Param("minLat") Double minLat,
        @Param("minLon") Double minLon,
        @Param("maxLat") Double maxLat,
        @Param("maxLon") Double maxLon
    );

    // GraphQL Connection queries
    @Query("SELECT j FROM Job j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:category IS NULL OR j.category = :category) AND " +
           "(:minPrice IS NULL OR j.minSalary >= :minPrice) AND " +
           "(:maxPrice IS NULL OR j.maxSalary <= :maxPrice)")
    List<Job> findForGraphQL(
        @Param("status") String status,
        @Param("category") String category,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        Pageable pageable
    );

    List<Job> findByCompanyIdIn(List<Integer> companyIds);
}
