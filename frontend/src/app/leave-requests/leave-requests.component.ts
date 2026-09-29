import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { LEAVE_STATUS_LABELS, LEAVE_TYPE_LABELS, LeaveRequest, LeaveStatus } from '../models/leave-request.model';
import { LeaveRequestService } from '../services/leave-request.service';
import { apiErrorMessage } from '../services/api-error';

@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  private readonly leaveRequestService = inject(LeaveRequestService);
  private readonly destroyRef = inject(DestroyRef);

  readonly requests = signal<LeaveRequest[]>([]);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly LeaveStatus = LeaveStatus;
  readonly typeLabels = LEAVE_TYPE_LABELS;
  readonly statusLabels = LEAVE_STATUS_LABELS;

  ngOnInit(): void {
    this.load();
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

  approve(id: number): void {
    this.leaveRequestService.approve(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }
}
