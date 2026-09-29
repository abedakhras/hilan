package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.EmployeeBalanceResponse;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.service.EmployeeBalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Read-only lookup so the UI can offer an employee picker.
@RestController
@RequestMapping("/api/employees")
public class EmployeesController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeBalanceService balanceService;

    public EmployeesController(EmployeeRepository employeeRepository, EmployeeBalanceService balanceService) {
        this.employeeRepository = employeeRepository;
        this.balanceService = balanceService;
    }

    // GET /api/employees
    @GetMapping
    public ResponseEntity<List<Employee>> getAll() {
        return ResponseEntity.ok(employeeRepository.findAll());
    }

    // GET /api/employees/balances?year=2026 (defaults to the current year)
    @GetMapping("/balances")
    public List<EmployeeBalanceResponse> balances(@RequestParam(required = false) Integer year) {
        return balanceService.getBalances(year != null ? year : LocalDate.now().getYear());
    }
}
