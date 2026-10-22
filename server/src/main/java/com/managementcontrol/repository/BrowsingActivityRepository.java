package com.managementcontrol.repository;

import com.managementcontrol.domain.BrowsingActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BrowsingActivityRepository extends JpaRepository<BrowsingActivity, Long> {

    List<BrowsingActivity> findByDeviceIdAndStartedAtBetweenOrderByStartedAtAsc(
            Long deviceId, Instant from, Instant to);

    @Query("""
            select b.domain, coalesce(sum(b.durationSeconds), 0) as total
            from BrowsingActivity b
            where b.deviceId = :deviceId
              and b.idle = false
              and b.startedAt >= :from and b.startedAt < :to
            group by b.domain
            order by total desc
            """)
    List<Object[]> topDomains(@Param("deviceId") Long deviceId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);
}
