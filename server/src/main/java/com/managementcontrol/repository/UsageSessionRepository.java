package com.managementcontrol.repository;

import com.managementcontrol.domain.UsageSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

    List<UsageSession> findByDeviceIdAndStartedAtBetweenOrderByStartedAtAsc(
            Long deviceId, Instant from, Instant to);

    @Query("""
            select coalesce(sum(u.durationSeconds), 0) from UsageSession u
            where u.deviceId = :deviceId
              and u.idle = false
              and u.startedAt >= :from and u.startedAt < :to
            """)
    long sumActiveSeconds(@Param("deviceId") Long deviceId,
                          @Param("from") Instant from,
                          @Param("to") Instant to);

    @Query("""
            select u.processName, coalesce(sum(u.durationSeconds), 0) as total
            from UsageSession u
            where u.deviceId = :deviceId
              and u.idle = false
              and u.startedAt >= :from and u.startedAt < :to
            group by u.processName
            order by total desc
            """)
    List<Object[]> topProcesses(@Param("deviceId") Long deviceId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);
}
