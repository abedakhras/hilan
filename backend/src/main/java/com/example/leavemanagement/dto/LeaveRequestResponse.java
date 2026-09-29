package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;

import java.time.LocalDate;

public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeName,
        LeaveType type,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        LeaveStatus status
) {
    public static LeaveRequestResponse from(LeaveRequest r) {
        return new LeaveRequestResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getEmployee() != null ? r.getEmployee().getName() : null,
                r.getType(),
                r.getStartDate(),
                r.getEndDate(),
                r.getDays(),
                r.getStatus());
    }
}
