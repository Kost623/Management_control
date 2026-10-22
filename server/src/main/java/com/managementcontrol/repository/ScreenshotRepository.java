package com.managementcontrol.repository;

import com.managementcontrol.domain.Screenshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {

    Optional<Screenshot> findByIdAndDeviceId(Long id, Long deviceId);

    Page<Screenshot> findByDeviceIdOrderByTakenAtDesc(Long deviceId, Pageable pageable);

    Page<Screenshot> findByDeviceIdAndTakenAtBetweenOrderByTakenAtDesc(
            Long deviceId, Instant from, Instant to, Pageable pageable);

    @Query("""
            select count(s) from Screenshot s
            where s.deviceId = :deviceId
              and s.takenAt >= :from and s.takenAt < :to
            """)
    long countForDay(@Param("deviceId") Long deviceId,
                     @Param("from") Instant from,
                     @Param("to") Instant to);
}
