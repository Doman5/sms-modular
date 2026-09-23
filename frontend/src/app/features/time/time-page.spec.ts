import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { TimePage } from './time-page';

describe('TimePage', () => {
  it('loads a month and sends a manual work entry', async () => {
    await TestBed.configureTestingModule({ imports: [TimePage], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: () => true } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TimePage);
    fixture.componentInstance.month = '2025-04';
    fixture.detectChanges();
    http.expectOne(value => value.url === '/api/v1/employees/options').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/work-days').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/work-days/summary').flush({ month: '2025-04', dayCount: 0, totalMinutes: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brak wpisów czasu pracy');
    fixture.componentInstance.openCreate();
    fixture.componentInstance.formEmployeeId = 'employee-1';
    fixture.componentInstance.formDate = '2025-04-17';
    fixture.componentInstance.save();
    const created = http.expectOne('/api/v1/employees/employee-1/work-days');
    expect(created.request.body).toEqual({ workDate: '2025-04-17', intervals: [{ startTime: '08:00', endTime: '16:00' }] });
    created.flush({});
    http.expectOne(value => value.url === '/api/v1/work-days').flush({ content: [], totalElements: 0, totalPages: 0 });
    http.expectOne(value => value.url === '/api/v1/work-days/summary').flush({ month: '2025-04', dayCount: 1, totalMinutes: 480 });
    expect(fixture.componentInstance.summary()?.totalMinutes).toBe(480);
    http.verify();
  });
});
