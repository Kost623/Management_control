package com.managementcontrol.repository;

import com.managementcontrol.domain.ProcessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ProcessEventRepository extends JpaRepository<ProcessEvent, Long> {

    List<ProcessEvent> findByDeviceIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long deviceId, Instant from, Instant to);
}
