// The API sends both enums as numbers (see LeaveType / LeaveStatus on the backend).
export enum LeaveType {
  Vacation = 0,
  Sick = 1,
  Unpaid = 2
}

export enum LeaveStatus {
  Pending = 0,
  Approved = 1,
  Rejected = 2
}

export const LEAVE_TYPE_LABELS: Record<LeaveType, string> = {
  [LeaveType.Vacation]: 'Vacation',
  [LeaveType.Sick]: 'Sick',
  [LeaveType.Unpaid]: 'Unpaid'
};

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  [LeaveStatus.Pending]: 'Pending',
  [LeaveStatus.Approved]: 'Approved',
  [LeaveStatus.Rejected]: 'Rejected'
};

export interface Employee {
  id: number;
  name: string;
  annualQuota: number;
}

// Per calendar year. "used" = approved days, "pending" = waiting for approval.
export interface EmployeeBalance {
  employeeId: number;
  employeeName: string;
  year: number;
  vacationQuota: number;
  vacationUsed: number;
  vacationPending: number;
  vacationRemaining: number;
  sickUsed: number;
  unpaidUsed: number;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  type: LeaveType;
  startDate: string; // yyyy-MM-dd
  endDate: string;
  days: number;
  status: LeaveStatus;
}

export interface CreateLeaveRequest {
  employeeId: number;
  type: LeaveType;
  startDate: string;
  endDate: string;
}
