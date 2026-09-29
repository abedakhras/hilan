import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaveRequestFormComponent } from './leave-request-form.component';
import { LeaveRequest, LeaveStatus, LeaveType } from '../models/leave-request.model';
import { API_URL } from '../api.config';

describe('LeaveRequestFormComponent', () => {
  let http: HttpTestingController;

  function setup() {
    const fixture = TestBed.createComponent(LeaveRequestFormComponent);
    fixture.detectChanges();
    http.expectOne(`${API_URL}/employees`).flush([{ id: 1, name: 'Dana Levi', annualQuota: 20 }]);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LeaveRequestFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('does not send anything and shows errors when the form is empty', () => {
    const fixture = setup();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    const errors = fixture.nativeElement.querySelectorAll('.field-error');
    expect(errors.length).toBe(4);
    http.expectNone(`${API_URL}/leave-requests`);
  });

  it('blocks an end date before the start date', () => {
    const fixture = setup();
    const component = fixture.componentInstance;
    component.form.setValue({ employeeId: 1, type: LeaveType.Vacation, startDate: '2026-05-07', endDate: '2026-05-03' });

    component.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("End date can't be before the start date");
    expect(component.requestedDays).toBeNull();
    http.expectNone(`${API_URL}/leave-requests`);
  });

  it('posts a valid request, emits it and resets the form', () => {
    const fixture = setup();
    const component = fixture.componentInstance;
    let emitted: LeaveRequest | undefined;
    component.created.subscribe((r) => (emitted = r));
    component.form.setValue({ employeeId: 1, type: LeaveType.Sick, startDate: '2026-05-03', endDate: '2026-05-04' });

    component.submit();

    const req = http.expectOne(`${API_URL}/leave-requests`);
    expect(req.request.body).toEqual({ employeeId: 1, type: LeaveType.Sick, startDate: '2026-05-03', endDate: '2026-05-04' });
    const created: LeaveRequest = {
      id: 10, employeeId: 1, employeeName: 'Dana Levi', type: LeaveType.Sick,
      startDate: '2026-05-03', endDate: '2026-05-04', days: 2, status: LeaveStatus.Pending
    };
    req.flush(created);

    expect(emitted).toEqual(created);
    expect(component.form.value.employeeId).toBeNull();
    expect(component.submitting()).toBeFalse();
  });

  it('shows the server message when the API rejects the request', () => {
    const fixture = setup();
    const component = fixture.componentInstance;
    component.form.setValue({ employeeId: 1, type: LeaveType.Vacation, startDate: '2026-05-03', endDate: '2026-05-10' });

    component.submit();
    http.expectOne(`${API_URL}/leave-requests`).flush(
      { status: 400, detail: 'Not enough vacation balance - requested 8 days, 2 left' },
      { status: 400, statusText: 'Bad Request' }
    );
    fixture.detectChanges();

    expect(component.serverError()).toContain('2 left');
    expect(fixture.nativeElement.querySelector('.form-error').textContent).toContain('Not enough vacation balance');
  });
});
