import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Employee } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { EmployeeDetailPage } from './employee-detail-page';

const employee: Employee = {
  id: 'employee-1', firstName: 'Jan', lastName: 'Nowak', phone: '501234567', normalizedPhone: '+48501234567',
  email: null, position: 'Kierowca', note: null, status: 'INACTIVE', employmentDate: '2024-01-01',
  createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z', version: 2,
};

describe('EmployeeDetailPage', () => {
  it('loads the card and activates the employee with its current version', async () => {
    await TestBed.configureTestingModule({ imports: [EmployeeDetailPage], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: () => true } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: employee.id }) } } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(EmployeeDetailPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/employees/employee-1').flush(employee);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Jan Nowak');
    fixture.componentInstance.changeStatus(employee);
    const request = http.expectOne('/api/v1/employees/employee-1/activate');
    expect(request.request.body).toEqual({ version: 2 });
    request.flush({ ...employee, status: 'ACTIVE', version: 3 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aktywny');
    http.verify();
  });
});
