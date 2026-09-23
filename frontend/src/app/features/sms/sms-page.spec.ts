import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { SmsMessage } from '../../core/api.service';
import { SmsPage } from './sms-page';

describe('SmsPage', () => {
  it('loads review queue and resolves a message', async () => {
    await TestBed.configureTestingModule({ imports: [SmsPage], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: () => true } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SmsPage);
    fixture.detectChanges();
    const sms: SmsMessage = { id: 'sms-1', employeeId: 'employee-1', sender: '****1111', content: null,
      recipient: '+48600000000', receivedAt: '2025-04-17T10:00:00Z', status: 'REVIEW_REQUIRED',
      reviewReason: 'AMBIGUOUS_CONTENT', resolution: null, version: 1 };
    http.expectOne(value => value.url === '/api/v1/sms').flush({ content: [sms], totalElements: 1, totalPages: 1 });
    http.expectOne(value => value.url === '/api/v1/employees/options').flush({ content: [{ id: 'employee-1', firstName: 'Anna', lastName: 'Nowak' }] });
    fixture.componentInstance.open(sms);
    http.expectOne('/api/v1/sms/sms-1').flush({ ...sms, sender: '+48601111111', content: 'nie będzie mnie' });
    fixture.componentInstance.category = 'ABSENCE';
    fixture.componentInstance.absenceDate = '2025-04-17';
    fixture.componentInstance.resolve();
    const decision = http.expectOne('/api/v1/sms/sms-1/resolve');
    expect(decision.request.body).toEqual({ version: 1, category: 'ABSENCE', employeeId: 'employee-1',
      workDate: null, startTime: null, endTime: null, absenceDate: '2025-04-17' });
    decision.flush({ ...sms, status: 'COMPLETED' });
    http.expectOne(value => value.url === '/api/v1/sms').flush({ content: [], totalElements: 0, totalPages: 0 });
    expect(fixture.componentInstance.selected()).toBeNull();
    http.verify();
  });
});
