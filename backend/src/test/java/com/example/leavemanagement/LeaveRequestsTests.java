package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void create_ExceedingRemainingBalance_IsRejected() {
        Employee emp = employeeWithUsedDays(20, 18);
        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 5, 3));
        dto.setEndDate(LocalDate.of(2026, 5, 7)); // 5 days, only 2 left

        ResponseEntity<?> result = controller.create(dto);

        assertEquals(400, result.getStatusCode().value());
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_UsingExactlyRemainingBalance_Succeeds() {
        Employee emp = employeeWithUsedDays(20, 18);

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 5, 3));
        dto.setEndDate(LocalDate.of(2026, 5, 4)); // 2 days

        ResponseEntity<?> result = controller.create(dto);

        assertTrue(result.getStatusCode().is2xxSuccessful());
    }

    @Test
    void create_LastYearsLeaveDoesNotCountAgainstThisYear() {
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);
        saveApproved(emp, LocalDate.of(2025, 8, 1), 18);

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 5, 3));
        dto.setEndDate(LocalDate.of(2026, 5, 7));

        ResponseEntity<?> result = controller.create(dto);

        assertTrue(result.getStatusCode().is2xxSuccessful());
    }

    @Test
    void approve_PendingRequest_Succeeds() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest pending = savePending(emp, LocalDate.of(2026, 6, 1), 3);

        ResponseEntity<?> result = controller.approve(pending.getId());

        assertEquals(200, result.getStatusCode().value());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_AlreadyApproved_ReturnsConflict() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest pending = savePending(emp, LocalDate.of(2026, 6, 1), 3);
        controller.approve(pending.getId());

        ResponseEntity<?> result = controller.approve(pending.getId());

        assertEquals(409, result.getStatusCode().value());
    }

    @Test
    void approve_RejectedRequest_ReturnsConflict() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest r = savePending(emp, LocalDate.of(2026, 6, 1), 3);
        r.setStatus(LeaveStatus.REJECTED);
        leaveRequests.save(r);

        ResponseEntity<?> result = controller.approve(r.getId());

        assertEquals(409, result.getStatusCode().value());
    }

    @Test
    void approve_UnknownRequest_ReturnsNotFound() {
        ResponseEntity<?> result = controller.approve(999_999L);

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void approve_WhenBalanceWasUsedInTheMeantime_ReturnsConflict() {
        Employee emp = employeeWithUsedDays(10, 0);
        LeaveRequest first = savePending(emp, LocalDate.of(2026, 6, 1), 6);
        LeaveRequest second = savePending(emp, LocalDate.of(2026, 7, 1), 6);

        assertEquals(200, controller.approve(first.getId()).getStatusCode().value());
        assertEquals(409, controller.approve(second.getId()).getStatusCode().value());
    }

    @Test
    void approve_TwoRequestsAtTheSameTime_OnlyOneFitsTheQuota() throws Exception {
        Employee emp = employeeWithUsedDays(10, 0);
        LeaveRequest first = savePending(emp, LocalDate.of(2026, 6, 1), 6);
        LeaveRequest second = savePending(emp, LocalDate.of(2026, 7, 1), 6);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<?>> a = pool.submit(() -> {
                start.await();
                return controller.approve(first.getId());
            });
            Future<ResponseEntity<?>> b = pool.submit(() -> {
                start.await();
                return controller.approve(second.getId());
            });
            start.countDown();

            List<Integer> codes = List.of(a.get().getStatusCode().value(), b.get().getStatusCode().value());
            assertTrue(codes.contains(200));
            assertTrue(codes.contains(409));
        } finally {
            pool.shutdown();
        }

        long approved = leaveRequests.findAllById(List.of(first.getId(), second.getId())).stream()
                .filter(r -> r.getStatus() == LeaveStatus.APPROVED)
                .count();
        assertEquals(1, approved);
    }

    @Test
    void search_DoesNotTreatInputAsSql() {
        Employee emp = employeeWithUsedDays(20, 3);

        ResponseEntity<List<LeaveRequest>> injected = controller.search("x' OR '1'='1");
        ResponseEntity<List<LeaveRequest>> byName = controller.search("test emp");

        assertTrue(injected.getBody().isEmpty());
        assertTrue(byName.getBody().stream().anyMatch(r -> r.getEmployeeId().equals(emp.getId())));
    }

    private LeaveRequest savePending(Employee emp, LocalDate start, int days) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(emp.getId());
        r.setType(LeaveType.VACATION);
        r.setStartDate(start);
        r.setEndDate(start.plusDays(days - 1));
        r.setDays(days);
        r.setStatus(LeaveStatus.PENDING);
        return leaveRequests.save(r);
    }

    private Employee employeeWithUsedDays(int quota, int usedDays) {
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(quota);
        employees.save(emp);
        if (usedDays > 0) {
            saveApproved(emp, LocalDate.of(2026, 1, 5), usedDays);
        }
        return emp;
    }

    private void saveApproved(Employee emp, LocalDate start, int days) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(emp.getId());
        r.setType(LeaveType.VACATION);
        r.setStartDate(start);
        r.setEndDate(start.plusDays(days - 1));
        r.setDays(days);
        r.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(r);
    }
}
