package com.managementcontrol.repository;

import com.managementcontrol.domain.RemoteCommand;
import com.managementcontrol.domain.RemoteCommandStatus;
import com.managementcontrol.domain.RemoteCommandType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RemoteCommandRepository extends JpaRepository<RemoteCommand, Long> {

    List<RemoteCommand> findByDeviceIdAndStatusInOrderByCreatedAtAsc(
            Long deviceId, Collection<RemoteCommandStatus> statuses);

    Page<RemoteCommand> findByDeviceIdOrderByCreatedAtDesc(Long deviceId, Pageable pageable);

    Page<RemoteCommand> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RemoteCommand> findByStatusAndExpiresAtBefore(RemoteCommandStatus status, Instant now);

    boolean existsByDeviceIdAndTypeAndStatusInAndCreatedAtAfter(
            Long deviceId, RemoteCommandType type, Collection<RemoteCommandStatus> statuses, Instant since);
}
