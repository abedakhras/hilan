package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.LeaveType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Incoming payload for creating a leave request.
public class CreateLeaveRequestDto {

    @NotNull(message = "is required")
    private Long employeeId;

    @NotNull(message = "is required")
    private LeaveType type;

    @NotNull(message = "is required")
    private LocalDate startDate;

    @NotNull(message = "is required")
    private LocalDate endDate;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LeaveType getType() { return type; }
    public void setType(LeaveType type) { this.type = type; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
