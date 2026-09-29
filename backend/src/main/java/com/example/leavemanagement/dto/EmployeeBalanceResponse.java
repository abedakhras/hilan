package com.example.leavemanagement.dto;

// Days are counted per calendar year by the request's start date.
// "used" = approved, "pending" = waiting for approval (not deducted yet).
public record EmployeeBalanceResponse(
        Long employeeId,
        String employeeName,
        int year,
        int vacationQuota,
        long vacationUsed,
        long vacationPending,
        long vacationRemaining,
        long sickUsed,
        long unpaidUsed
) {
}
