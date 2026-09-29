import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Employee, EmployeeBalance } from '../models/leave-request.model';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${API_URL}/employees`);
  }

  getBalances(year: number): Observable<EmployeeBalance[]> {
    return this.http.get<EmployeeBalance[]>(`${API_URL}/employees/balances`, { params: { year } });
  }
}
