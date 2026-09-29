package com.example.leavemanagement;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.dto.EmployeeBalanceResponse;
import com.example.leavemanagement.dto.LeaveRequestResponse;
import com.example.leavemanagement.exception.ConflictException;
import com.example.leavemanagement.exception.InvalidRequestException;
import com.example.leavemanagement.exception.NotFoundException;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.service.EmployeeBalanceService;
import com.example.leavemanagement.service.LeaveRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@AutoConfigureMockMvc
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
    private LeaveRequestService service;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Autowired
    private EmployeeBalanceService balances;

    @Autowired
    private MockMvc mvc;

    // --- create ---

    @Test
    void create_WithinQuota_Succeeds() {
        Employee emp = employeeWithUsedDays(20, 0);
        long before = leaveRequests.count();

        LeaveRequestResponse created = service.create(vacation(emp, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));

        assertEquals(3, created.days());
        assertEquals(LeaveStatus.PENDING, created.status());
        assertEquals("Test Emp", created.employeeName());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void create_ExceedingRemainingBalance_IsRejected() {
        Employee emp = employeeWithUsedDays(20, 18);
        long before = leaveRequests.count();

        // 5 days, only 2 left
        CreateLeaveRequestDto dto = vacation(emp, LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 7));

        assertThrows(InvalidRequestException.class, () -> service.create(dto));
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_UsingExactlyRemainingBalance_Succeeds() {
        Employee emp = employeeWithUsedDays(20, 18);

        LeaveRequestResponse created = service.create(vacation(emp, LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4)));

        assertEquals(2, created.days());
    }

    @Test
    void create_LastYearsLeaveDoesNotCountAgainstThisYear() {
        Employee emp = employeeWithUsedDays(20, 0);
        saveApproved(emp, LocalDate.of(2025, 8, 1), 18);

        LeaveRequestResponse created = service.create(vacation(emp, LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 7)));

        assertEquals(5, created.days());
    }

    @Test
    void create_SickLeaveIsNotLimitedByVacationQuota() {
        Employee emp = employeeWithUsedDays(20, 20);
        CreateLeaveRequestDto dto = vacation(emp, LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 7));
        dto.setType(LeaveType.SICK);

        assertEquals(LeaveStatus.PENDING, service.create(dto).status());
    }

    @Test
    void create_EndBeforeStart_IsRejected() {
        Employee emp = employeeWithUsedDays(20, 0);
        CreateLeaveRequestDto dto = vacation(emp, LocalDate.of(2026, 5, 7), LocalDate.of(2026, 5, 3));

        assertThrows(InvalidRequestException.class, () -> service.create(dto));
    }

    @Test
    void create_UnknownEmployee_ThrowsNotFound() {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(999_999L);
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 5, 3));
        dto.setEndDate(LocalDate.of(2026, 5, 4));

        assertThrows(NotFoundException.class, () -> service.create(dto));
    }

    // --- approve ---

    @Test
    void approve_PendingRequest_Succeeds() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest pending = savePending(emp, LocalDate.of(2026, 6, 1), 3);

        LeaveRequestResponse approved = service.approve(pending.getId());

        assertEquals(LeaveStatus.APPROVED, approved.status());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_AlreadyApproved_ThrowsConflict() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest pending = savePending(emp, LocalDate.of(2026, 6, 1), 3);
        service.approve(pending.getId());

        assertThrows(ConflictException.class, () -> service.approve(pending.getId()));
    }

    @Test
    void approve_RejectedRequest_ThrowsConflict() {
        Employee emp = employeeWithUsedDays(20, 5);
        LeaveRequest r = savePending(emp, LocalDate.of(2026, 6, 1), 3);
        r.setStatus(LeaveStatus.REJECTED);
        leaveRequests.save(r);

        assertThrows(ConflictException.class, () -> service.approve(r.getId()));
    }

    @Test
    void approve_UnknownRequest_ThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> service.approve(999_999L));
    }

    @Test
    void approve_WhenBalanceWasUsedInTheMeantime_ThrowsConflict() {
        Employee emp = employeeWithUsedDays(10, 0);
        LeaveRequest first = savePending(emp, LocalDate.of(2026, 6, 1), 6);
        LeaveRequest second = savePending(emp, LocalDate.of(2026, 7, 1), 6);

        service.approve(first.getId());

        assertThrows(ConflictException.class, () -> service.approve(second.getId()));
    }

    @Test
    void approve_TwoRequestsAtTheSameTime_OnlyOneFitsTheQuota() throws Exception {
        Employee emp = employeeWithUsedDays(10, 0);
        LeaveRequest first = savePending(emp, LocalDate.of(2026, 6, 1), 6);
        LeaveRequest second = savePending(emp, LocalDate.of(2026, 7, 1), 6);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (Long id : List.of(first.getId(), second.getId())) {
                Callable<Boolean> approve = () -> {
                    start.await();
                    try {
                        service.approve(id);
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                };
                results.add(pool.submit(approve));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get(10, TimeUnit.SECONDS)) succeeded++;
            }
            assertEquals(1, succeeded);
        } finally {
            pool.shutdownNow();
        }

        long approved = leaveRequests.findAllById(List.of(first.getId(), second.getId())).stream()
                .filter(r -> r.getStatus() == LeaveStatus.APPROVED)
                .count();
        assertEquals(1, approved);
    }

    // --- search ---

    @Test
    void search_DoesNotTreatInputAsSql() {
        Employee emp = employeeWithUsedDays(20, 3);

        assertTrue(service.searchByEmployeeName("x' OR '1'='1").isEmpty());
        assertTrue(service.searchByEmployeeName("test emp").stream()
                .anyMatch(r -> r.employeeId().equals(emp.getId())));
    }

    // --- balances ---

    @Test
    void balances_CountApprovedPendingAndOtherTypesForTheYear() {
        Employee emp = employeeWithUsedDays(20, 5);           // 5 approved vacation in 2026
        savePending(emp, LocalDate.of(2026, 6, 1), 3);        // 3 pending vacation
        saveApproved(emp, LocalDate.of(2025, 6, 1), 4);       // last year, ignored
        LeaveRequest sick = savePending(emp, LocalDate.of(2026, 2, 1), 2);
        sick.setType(LeaveType.SICK);
        sick.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(sick);

        EmployeeBalanceResponse b = balances.getBalances(2026).stream()
                .filter(x -> x.employeeId().equals(emp.getId()))
                .findFirst().orElseThrow();

        assertEquals(20, b.vacationQuota());
        assertEquals(5, b.vacationUsed());
        assertEquals(3, b.vacationPending());
        assertEquals(15, b.vacationRemaining());
        assertEquals(2, b.sickUsed());
        assertEquals(0, b.unpaidUsed());
    }

    @Test
    void balances_EmployeeWithNoRequestsHasFullQuota() {
        Employee emp = employeeWithUsedDays(14, 0);

        EmployeeBalanceResponse b = balances.getBalances(2026).stream()
                .filter(x -> x.employeeId().equals(emp.getId()))
                .findFirst().orElseThrow();

        assertEquals(14, b.vacationRemaining());
        assertEquals(0, b.vacationUsed());
    }

    @Test
    void api_Balances_ReturnsOneRowPerEmployee() throws Exception {
        mvc.perform(get("/api/employees/balances").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].vacationRemaining").exists());
    }

    // --- HTTP layer: status codes and error body ---

    @Test
    void api_ApproveUnknown_Returns404() throws Exception {
        mvc.perform(post("/api/leave-requests/999999/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Leave request 999999 not found"));
    }

    @Test
    void api_ApproveTwice_Returns409() throws Exception {
        Employee emp = employeeWithUsedDays(20, 0);
        LeaveRequest pending = savePending(emp, LocalDate.of(2026, 6, 1), 3);

        mvc.perform(post("/api/leave-requests/" + pending.getId() + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1));
        mvc.perform(post("/api/leave-requests/" + pending.getId() + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Leave request is already approved"));
    }

    @Test
    void api_CreateWithMissingFields_Returns400() throws Exception {
        mvc.perform(post("/api/leave-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\": 1, \"startDate\": \"2026-05-03\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void api_CreateValid_Returns201() throws Exception {
        Employee emp = employeeWithUsedDays(20, 0);

        mvc.perform(post("/api/leave-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\": " + emp.getId() + ", \"type\": 0, "
                                + "\"startDate\": \"2026-05-03\", \"endDate\": \"2026-05-04\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.employeeName").value("Test Emp"));
    }

    @Test
    void api_BalancesRejectsYearsOutsideSupportedRange() throws Exception {
        for (int year : List.of(0, -1, 10000, 1000000000)) {
            mvc.perform(get("/api/employees/balances").param("year", Integer.toString(year)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Year must be between 1 and 9999"));
        }
    }

    @Test
    void api_BalancesAcceptsYearBoundaries() throws Exception {
        for (int year : List.of(1, 9999)) {
            mvc.perform(get("/api/employees/balances").param("year", Integer.toString(year)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void api_InvalidYearParameterReturnsProblemDetail() throws Exception {
        mvc.perform(get("/api/employees/balances").param("year", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'year'"));
    }

    @Test
    void api_UnreadableRequestBodiesReturnProblemDetail() throws Exception {
        List<String> bodies = List.of(
                "{",
                """
                {"employeeId": 1, "type": 0, "startDate": "not-a-date", "endDate": "2026-05-04"}
                """,
                """
                {"employeeId": 1, "type": 99, "startDate": "2026-05-03", "endDate": "2026-05-04"}
                """);
        long before = leaveRequests.count();
        for (String body : bodies) {
            mvc.perform(post("/api/leave-requests")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(
                            "Invalid request body. Check the JSON, dates (yyyy-MM-dd), and leave type (0, 1, or 2)."));
        }
        assertEquals(before, leaveRequests.count());
    }

    // --- helpers ---

    private CreateLeaveRequestDto vacation(Employee emp, LocalDate start, LocalDate end) {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(start);
        dto.setEndDate(end);
        return dto;
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
        LeaveRequest r = savePending(emp, start, days);
        r.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(r);
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
}
