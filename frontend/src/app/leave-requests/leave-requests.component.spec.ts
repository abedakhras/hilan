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
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);
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

  for (const includesCreated of [false, true]) {
    it(`keeps a new request when loading finishes (server includes it: ${includesCreated})`, () => {
      const fixture = TestBed.createComponent(LeaveRequestsComponent);
      fixture.detectChanges();
      const listRequest = http.expectOne(`${API_URL}/leave-requests`);
      http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);
      http.expectOne(`${API_URL}/employees`).flush([]);
      const created = { ...pending, id: 8, startDate: '2026-07-01', endDate: '2026-07-02' };

      fixture.componentInstance.onCreated(created);
      http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);
      listRequest.flush(includesCreated ? [created, pending] : [pending]);

      expect(fixture.componentInstance.requests().map((r) => r.id)).toEqual([8, 7]);
      expect(fixture.componentInstance.loading()).toBeFalse();
    });
  }

  it('cancels an older balance refresh when a new refresh starts', () => {
    const component = setup().componentInstance;
    component.loadBalances();
    const older = http.expectOne((r) => r.url === `${API_URL}/employees/balances`);

    component.loadBalances();
    expect(older.cancelled).toBeTrue();
    const latest = [{
      employeeId: 1, employeeName: 'Dana Levi', year: component.balanceYear,
      vacationQuota: 20, vacationUsed: 8, vacationPending: 0,
      vacationRemaining: 12, sickUsed: 0, unpaidUsed: 0
    }];
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush(latest);

    expect(component.balances()).toEqual(latest);
  });

  it('can refresh balances again after an error', () => {
    const component = setup().componentInstance;
    component.loadBalances();
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush(
      { detail: 'Temporary failure' }, { status: 500, statusText: 'Server Error' }
    );
    expect(component.balancesError()).toBe('Temporary failure');

    component.loadBalances();
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);

    expect(component.balancesError()).toBeNull();
    expect(component.balances()).toEqual([]);
  });

  it('updates only the approved row and shows a success message', () => {
    const fixture = setup();
    const component = fixture.componentInstance;

    component.approve(pending);
    expect(component.approving().has(7)).toBeTrue();

    http.expectOne(`${API_URL}/leave-requests/7/approve`).flush({ ...pending, status: LeaveStatus.Approved });

    expect(component.approving().has(7)).toBeFalse();
    expect(component.requests()[0].status).toBe(LeaveStatus.Approved);
    expect(component.notice()).toContain('#7');
    // balances are refreshed after an approve
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);
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
    expect(component.approving().has(pending.id)).toBeTrue();

    http.expectOne(`${API_URL}/leave-requests/7/approve`).flush({ ...pending, status: LeaveStatus.Approved });
    http.expectOne((r) => r.url === `${API_URL}/employees/balances`).flush([]);
  });
});
