package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.EmployeeBalanceResponse;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository.DaysTotal;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmployeeBalanceService {

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;

    public EmployeeBalanceService(EmployeeRepository employees, LeaveRequestRepository leaveRequests) {
        this.employees = employees;
        this.leaveRequests = leaveRequests;
    }

    @Transactional(readOnly = true)
    public List<EmployeeBalanceResponse> getBalances(int year) {
        Map<Long, List<DaysTotal>> totalsByEmployee = leaveRequests
                .sumDaysPerEmployee(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .collect(Collectors.groupingBy(DaysTotal::getEmployeeId));

        return employees.findAll(Sort.by("name")).stream()
                .map(e -> toBalance(e, year, totalsByEmployee.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private EmployeeBalanceResponse toBalance(Employee employee, int year, List<DaysTotal> totals) {
        long vacationUsed = days(totals, LeaveType.VACATION, LeaveStatus.APPROVED);
        return new EmployeeBalanceResponse(
                employee.getId(),
                employee.getName(),
                year,
                employee.getAnnualQuota(),
                vacationUsed,
                days(totals, LeaveType.VACATION, LeaveStatus.PENDING),
                Math.max(employee.getAnnualQuota() - vacationUsed, 0),
                days(totals, LeaveType.SICK, LeaveStatus.APPROVED),
                days(totals, LeaveType.UNPAID, LeaveStatus.APPROVED));
    }

    private static long days(List<DaysTotal> totals, LeaveType type, LeaveStatus status) {
        return totals.stream()
                .filter(t -> t.getType() == type && t.getStatus() == status)
                .mapToLong(DaysTotal::getDays)
                .sum();
    }
}
