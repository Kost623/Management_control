package com.managementcontrol.repository;

import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(String deviceId);

    Optional<Device> findByTokenHash(String tokenHash);

    Page<Device> findByStatus(DeviceStatus status, Pageable pageable);

    Page<Device> findAllByOrderByIdDesc(Pageable pageable);

    @Query("""
            select d from Device d
            where d.lastSeenAt is null or d.lastSeenAt < :threshold
            """)
    Page<Device> findStale(@Param("threshold") Instant threshold, Pageable pageable);

    @Query("""
            select count(d) from Device d
            where d.status = :status
            """)
    long countByStatus(@Param("status") DeviceStatus status);
}
