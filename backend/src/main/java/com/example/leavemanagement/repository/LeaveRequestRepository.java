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
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findAllByOrderByStartDateDesc();

    List<LeaveRequest> findByEmployee_NameContainingIgnoreCaseOrderByStartDateDesc(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    interface DaysTotal {
        Long getEmployeeId();
        LeaveType getType();
        LeaveStatus getStatus();
        Long getDays();
    }

    @Query("""
            select r.employeeId as employeeId, r.type as type, r.status as status, sum(r.days) as days
            from LeaveRequest r
            where r.startDate between :from and :to
            group by r.employeeId, r.type, r.status
            """)
    List<DaysTotal> sumDaysPerEmployee(@Param("from") LocalDate from, @Param("to") LocalDate to);

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
