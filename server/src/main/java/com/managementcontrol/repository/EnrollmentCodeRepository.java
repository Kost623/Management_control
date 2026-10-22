package com.managementcontrol.repository;

import com.managementcontrol.domain.EnrollmentCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EnrollmentCodeRepository extends JpaRepository<EnrollmentCode, Long> {

    Optional<EnrollmentCode> findByCode(String code);

    List<EnrollmentCode> findByActiveTrueOrderByCreatedAtDesc();

    List<EnrollmentCode> findByActiveTrueAndExpiresAtAfter(Instant now);
}
