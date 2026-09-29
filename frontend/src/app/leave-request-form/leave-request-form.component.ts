import { Component, DestroyRef, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Employee, LEAVE_TYPE_LABELS, LeaveRequest, LeaveType } from '../models/leave-request.model';
import { EmployeeService } from '../services/employee.service';
import { LeaveRequestService } from '../services/leave-request.service';
import { apiErrorMessage } from '../services/api-error';
import { dateRangeValidator, daysBetweenInclusive } from './date-range.validator';

@Component({
  selector: 'app-leave-request-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './leave-request-form.component.html',
  styleUrls: ['./leave-request-form.component.css']
})
export class LeaveRequestFormComponent implements OnInit {
  @Output() readonly created = new EventEmitter<LeaveRequest>();

  private readonly fb = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly leaveRequestService = inject(LeaveRequestService);
  private readonly destroyRef = inject(DestroyRef);

  readonly employees = signal<Employee[]>([]);
  readonly employeesError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly serverError = signal<string | null>(null);

  readonly typeOptions = [LeaveType.Vacation, LeaveType.Sick, LeaveType.Unpaid];
  readonly typeLabels = LEAVE_TYPE_LABELS;

  readonly form = this.fb.group(
    {
      employeeId: this.fb.control<number | null>(null, Validators.required),
      type: this.fb.control<LeaveType | null>(null, Validators.required),
      startDate: this.fb.nonNullable.control('', Validators.required),
      endDate: this.fb.nonNullable.control('', Validators.required)
    },
    { validators: dateRangeValidator('startDate', 'endDate') }
  );

  ngOnInit(): void {
    this.employeeService.getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (employees) => this.employees.set(employees),
        error: (err) => this.employeesError.set(apiErrorMessage(err, 'Could not load employees.'))
      });
  }

  showError(name: 'employeeId' | 'type' | 'startDate' | 'endDate'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  get showDateRangeError(): boolean {
    const { startDate, endDate } = this.form.controls;
    return this.form.hasError('dateRange') && (startDate.touched || endDate.touched || this.submitted());
  }

  get requestedDays(): number | null {
    const { startDate, endDate } = this.form.getRawValue();
    if (!startDate || !endDate || this.form.hasError('dateRange')) {
      return null;
    }
    return daysBetweenInclusive(startDate, endDate);
  }

  submit(): void {
    this.submitted.set(true);
    this.serverError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.submitting.set(true);

    this.leaveRequestService
      .create({
        employeeId: value.employeeId!,
        type: value.type!,
        startDate: value.startDate,
        endDate: value.endDate
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (created) => {
          this.created.emit(created);
          this.form.reset();
          this.submitted.set(false);
        },
        error: (err) => this.serverError.set(apiErrorMessage(err, 'Could not submit the request.'))
      });
  }
}
