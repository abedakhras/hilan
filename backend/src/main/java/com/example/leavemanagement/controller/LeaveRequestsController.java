package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.dto.LeaveRequestResponse;
import com.example.leavemanagement.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestsController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    public List<LeaveRequestResponse> getAll() {
        return leaveRequestService.getAll();
    }

    // GET /api/leave-requests/search?name=Dana
    @GetMapping("/search")
    public List<LeaveRequestResponse> search(@RequestParam String name) {
        return leaveRequestService.searchByEmployeeName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestResponse create(@Valid @RequestBody CreateLeaveRequestDto dto) {
        return leaveRequestService.create(dto);
    }

    @PostMapping("/{id}/approve")
    public LeaveRequestResponse approve(@PathVariable Long id) {
        return leaveRequestService.approve(id);
    }
}
