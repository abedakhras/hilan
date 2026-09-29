import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, finalize, switchMap, tap, timer } from 'rxjs';
import { EmployeeBalance, LEAVE_STATUS_LABELS, LEAVE_TYPE_LABELS, LeaveRequest, LeaveStatus } from '../models/leave-request.model';
import { LeaveRequestService } from '../services/leave-request.service';
import { EmployeeService } from '../services/employee.service';
import { apiErrorMessage } from '../services/api-error';
import { LeaveRequestFormComponent } from '../leave-request-form/leave-request-form.component';

@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [DatePipe, LeaveRequestFormComponent],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  private readonly leaveRequestService = inject(LeaveRequestService);
  private readonly employeeService = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly requests = signal<LeaveRequest[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly balanceYear = new Date().getFullYear();
  readonly balances = signal<EmployeeBalance[]>([]);
  readonly balancesError = signal<string | null>(null);

  readonly approving = signal<ReadonlySet<number>>(new Set());
  readonly approveErrors = signal<Readonly<Record<number, string>>>({});
  readonly notice = signal<string | null>(null);
  private readonly notice$ = new Subject<string>();

  readonly LeaveStatus = LeaveStatus;
  readonly typeLabels = LEAVE_TYPE_LABELS;
  readonly statusLabels = LEAVE_STATUS_LABELS;

  constructor() {
    // Show the latest success message and hide it a few seconds later.
    this.notice$
      .pipe(
        tap((message) => this.notice.set(message)),
        switchMap(() => timer(4000)),
        takeUntilDestroyed()
      )
      .subscribe(() => this.notice.set(null));
  }

  ngOnInit(): void {
    this.load();
    this.loadBalances();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.leaveRequestService.getAll()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (requests) => this.requests.set(requests),
        error: (err) => this.loadError.set(apiErrorMessage(err, 'Could not load leave requests.'))
      });
  }

  loadBalances(): void {
    this.employeeService.getBalances(this.balanceYear)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (balances) => {
          this.balances.set(balances);
          this.balancesError.set(null);
        },
        error: (err) => this.balancesError.set(apiErrorMessage(err, 'Could not load balances.'))
      });
  }

  onCreated(request: LeaveRequest): void {
    this.requests.update((list) =>
      [request, ...list].sort((a, b) => b.startDate.localeCompare(a.startDate))
    );
    this.notice$.next(`Request #${request.id} submitted.`);
    this.loadBalances();
  }

  approve(request: LeaveRequest): void {
    const id = request.id;
    if (this.approving().has(id)) {
      return;
    }
    this.setApproving(id, true);
    this.setApproveError(id, null);

    this.leaveRequestService.approve(id)
      .pipe(
        finalize(() => this.setApproving(id, false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (updated) => {
          // Only the approved row changes, no need to reload the whole list.
          this.requests.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
          this.notice$.next(`Request #${updated.id} (${updated.employeeName}) approved.`);
          this.loadBalances();
        },
        error: (err) => this.setApproveError(id, apiErrorMessage(err, 'Could not approve this request.'))
      });
  }

  private setApproving(id: number, value: boolean): void {
    this.approving.update((current) => {
      const next = new Set(current);
      if (value) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  private setApproveError(id: number, message: string | null): void {
    this.approveErrors.update((current) => {
      const { [id]: _, ...rest } = current;
      return message ? { ...rest, [id]: message } : rest;
    });
  }
}
