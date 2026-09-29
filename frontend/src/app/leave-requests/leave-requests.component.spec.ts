import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaveRequestsComponent } from './leave-requests.component';
import { LeaveRequest, LeaveStatus, LeaveType } from '../models/leave-request.model';
import { API_URL } from '../api.config';

describe('LeaveRequestsComponent', () => {
  let http: HttpTestingController;

  const pending: LeaveRequest = {
    id: 7,
    employeeId: 1,
    employeeName: 'Dana Levi',
    type: LeaveType.Vacation,
    startDate: '2026-06-01',
    endDate: '2026-06-02',
    days: 2,
    status: LeaveStatus.Pending
  };

  function setup() {
    const fixture = TestBed.createComponent(LeaveRequestsComponent);
    fixture.detectChanges();
    http.expectOne(`${API_URL}/leave-requests`).flush([pending]);
    http.expectOne(`${API_URL}/employees`).flush([]); // loaded by the embedded form
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LeaveRequestsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('updates only the approved row and shows a success message', () => {
    const fixture = setup();
    const component = fixture.componentInstance;

    component.approve(pending);
    expect(component.approving().has(7)).toBeTrue();

    http.expectOne(`${API_URL}/leave-requests/7/approve`).flush({ ...pending, status: LeaveStatus.Approved });

    expect(component.approving().has(7)).toBeFalse();
    expect(component.requests()[0].status).toBe(LeaveStatus.Approved);
    expect(component.notice()).toContain('#7');
    // no reload of the list
    http.expectNone(`${API_URL}/leave-requests`);
  });

  it('shows the server message on the row when approve fails', () => {
    const fixture = setup();
    const component = fixture.componentInstance;

    component.approve(pending);
    http.expectOne(`${API_URL}/leave-requests/7/approve`).flush(
      { status: 409, detail: 'Leave request is already approved' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(component.approving().has(7)).toBeFalse();
    expect(component.approveErrors()[7]).toBe('Leave request is already approved');
    expect(fixture.nativeElement.querySelector('.row-error').textContent).toContain('already approved');
  });

  it('ignores a second click while the first approve is still running', () => {
    const fixture = setup();
    const component = fixture.componentInstance;

    component.approve(pending);
    component.approve(pending);

    http.expectOne(`${API_URL}/leave-requests/7/approve`).flush({ ...pending, status: LeaveStatus.Approved });
  });
});
