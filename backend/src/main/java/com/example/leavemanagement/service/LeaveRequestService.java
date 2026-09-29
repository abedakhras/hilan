package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;

    public LeaveRequestService(EmployeeRepository employees, LeaveRequestRepository leaveRequests) {
        this.employees = employees;
        this.leaveRequests = leaveRequests;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getAll() {
        return leaveRequests.findAllByOrderByStartDateDesc().stream()
                .map(LeaveRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> searchByEmployeeName(String name) {
        return leaveRequests.findByEmployee_NameContainingIgnoreCaseOrderByStartDateDesc(name).stream()
                .map(LeaveRequestResponse::from)
                .toList();
    }

    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new InvalidRequestException("End date can't be before start date");
        }

        Employee employee = employees.findById(dto.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee " + dto.getEmployeeId() + " not found"));

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        if (dto.getType() == LeaveType.VACATION) {
            long left = employee.getAnnualQuota() - usedVacationDays(employee.getId(), dto.getStartDate().getYear());
            if (days > left) {
                throw new InvalidRequestException(
                        "Not enough vacation balance - requested " + days + " days, " + Math.max(left, 0) + " left");
            }
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setEmployee(employee);
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        return LeaveRequestResponse.from(leaveRequests.save(request));
    }

    @Transactional
    public LeaveRequestResponse approve(Long id) {
        LeaveRequest request = leaveRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Leave request " + id + " not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Leave request is already " + request.getStatus().name().toLowerCase());
        }

        if (request.getType() == LeaveType.VACATION) {
            // Lock the employee row so two approvals for the same person are serialized
            // and the second one sees the days the first one just approved.
            Employee employee = employees.findByIdForUpdate(request.getEmployeeId()).orElseThrow();
            long used = usedVacationDays(employee.getId(), request.getStartDate().getYear());
            if (used + request.getDays() > employee.getAnnualQuota()) {
                throw new ConflictException("Not enough vacation balance to approve this request");
            }
        }

        request.setStatus(LeaveStatus.APPROVED);
        return LeaveRequestResponse.from(request);
    }

    private long usedVacationDays(Long employeeId, int year) {
        return leaveRequests.sumDays(employeeId, LeaveType.VACATION, LeaveStatus.APPROVED,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }
}
