import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AbsencePage } from './absence-page';

describe('AbsencePage', () => {
  it('loads the calendar and marks a range of days', async () => {
    await TestBed.configureTestingModule({ imports: [AbsencePage], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: () => true } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AbsencePage);
    fixture.componentInstance.month = '2025-04';
    fixture.detectChanges();
    http.expectOne(value => value.url === '/api/v1/employees/options').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/absence-days').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/absence-days/calendar').flush([]);
    fixture.componentInstance.openCreate();
    fixture.componentInstance.formEmployeeId = 'employee-1';
    fixture.componentInstance.dateFrom = '2025-04-17';
    fixture.componentInstance.dateTo = '2025-04-18';
    fixture.componentInstance.save();
    const created = http.expectOne('/api/v1/absence-days');
    expect(created.request.body).toEqual({ employeeId: 'employee-1', dateFrom: '2025-04-17', dateTo: '2025-04-18', note: null });
    created.flush([]);
    http.expectOne(value => value.url === '/api/v1/absence-days').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/absence-days/calendar').flush([{ date: '2025-04-17', count: 1 }]);
    expect(fixture.componentInstance.count('2025-04-17')).toBe(1);
    http.verify();
  });
});
