package com.example.leavemanagement.repository;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select coalesce(sum(r.days), 0) from LeaveRequest r
            where r.employeeId = :employeeId
              and r.type = :type
              and r.status = :status
              and r.startDate between :from and :to
            """)
    long sumDays(@Param("employeeId") Long employeeId,
                 @Param("type") LeaveType type,
                 @Param("status") LeaveStatus status,
                 @Param("from") LocalDate from,
                 @Param("to") LocalDate to);
}
