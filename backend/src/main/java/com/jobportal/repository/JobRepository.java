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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Job e WHERE e.id = :id")
    java.util.Optional<Job> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);


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

    // Jobs with non-null coordinates
    @Query("SELECT j FROM Job j WHERE j.latitude IS NOT NULL AND j.longitude IS NOT NULL ORDER BY j.id")
    List<Job> findAllWithCoordinates();

    // Bounding-box geospatial query
    @Query("SELECT j FROM Job j WHERE " +
           "j.latitude IS NOT NULL AND j.longitude IS NOT NULL AND " +
           "j.latitude >= :minLat AND j.latitude <= :maxLat AND " +
           "j.longitude >= :minLon AND j.longitude <= :maxLon ORDER BY j.id")
    List<Job> findByBoundingBox(
        @Param("minLat") Double minLat,
        @Param("minLon") Double minLon,
        @Param("maxLat") Double maxLat,
        @Param("maxLon") Double maxLon
    );

    List<Job> findByCompanyIdIn(List<Integer> companyIds);
}
