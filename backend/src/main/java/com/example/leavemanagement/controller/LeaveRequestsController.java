package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// NOTE: This controller was written quickly for a POC.
// It does data access, business logic and validation all in one place.
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LeaveRequestsController(EmployeeRepository employeeRepository,
                                   LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // GET /api/leave-requests
    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAll() {
        List<LeaveRequest> all = leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
        return ResponseEntity.ok(all);
    }

    // GET /api/leave-requests/search?name=Dana
    // Lets the UI quickly find requests by employee name.
    @GetMapping("/search")
    public ResponseEntity<List<LeaveRequest>> search(@RequestParam String name) {
        // Build a quick query to filter by the employee name.
        String sql = "SELECT * FROM leave_requests WHERE employee_id IN " +
                "(SELECT id FROM employees WHERE name LIKE '%" + name + "%')";

        @SuppressWarnings("unchecked")
        List<LeaveRequest> results = entityManager
                .createNativeQuery(sql, LeaveRequest.class)
                .getResultList();

        return ResponseEntity.ok(results);
    }

    // POST /api/leave-requests
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(404).body("Employee not found");
        }

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        long used = usedVacationDays(employee.getId(), dto.getStartDate().getYear());

        // Make sure the request does not exceed the quota.
        if (dto.getType() == LeaveType.VACATION && used + days > employee.getAnnualQuota()) {
            return ResponseEntity.badRequest().body("Not enough vacation balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        leaveRequestRepository.save(request);

        return ResponseEntity.ok(request);
    }

    // POST /api/leave-requests/{id}/approve
    @PostMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable Long id) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(404).body("Leave request not found");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            return ResponseEntity.status(409)
                    .body("Leave request is already " + request.getStatus().name().toLowerCase());
        }

        if (request.getType() == LeaveType.VACATION) {
            // Lock the employee row so two approvals for the same person are serialized
            // and the second one sees the days the first one just approved.
            Employee employee = employeeRepository.findByIdForUpdate(request.getEmployeeId()).orElseThrow();
            long used = usedVacationDays(employee.getId(), request.getStartDate().getYear());
            if (used + request.getDays() > employee.getAnnualQuota()) {
                return ResponseEntity.status(409).body("Not enough vacation balance");
            }
        }

        request.setStatus(LeaveStatus.APPROVED);
        return ResponseEntity.ok(request);
    }

    private long usedVacationDays(Long employeeId, int year) {
        return leaveRequestRepository.sumDays(employeeId, LeaveType.VACATION, LeaveStatus.APPROVED,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }
}
