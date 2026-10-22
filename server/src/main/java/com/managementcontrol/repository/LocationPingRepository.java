package com.managementcontrol.repository;

import com.managementcontrol.domain.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {

    List<LocationPing> findByDeviceIdOrderByCapturedAtDesc(Long deviceId);

    List<LocationPing> findByDeviceIdAndCapturedAtBetweenOrderByCapturedAtAsc(
            Long deviceId, Instant from, Instant to);

    Optional<LocationPing> findFirstByDeviceIdOrderByCapturedAtDesc(Long deviceId);
}
