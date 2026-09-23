import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { UsersPage } from './users-page';

describe('UsersPage', () => {
  it('filters the visible page and opens user details', async () => {
    await TestBed.configureTestingModule({ imports: [UsersPage], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { has: () => true } },
    ] }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/users?page=0').flush({ content: [
      { id: '1', displayName: 'Anna Nowak', email: 'anna@example.test', roleId: 'owner', status: 'ACTIVE' },
      { id: '2', displayName: 'Jan Kowalski', email: 'jan@example.test', roleId: 'owner', status: 'DISABLED' },
    ], totalPages: 1 });
    http.expectOne('/api/v1/roles').flush([{ id: 'owner', name: 'Administrator', code: 'OWNER', permissions: [] }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.filteredUsers().length).toBe(2);
    fixture.componentInstance.search = 'anna';
    fixture.detectChanges();
    expect(fixture.componentInstance.filteredUsers().map(user => user.displayName)).toEqual(['Anna Nowak']);
    fixture.componentInstance.select(fixture.componentInstance.filteredUsers()[0]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.user-drawer').textContent).toContain('Anna Nowak');
    fixture.componentInstance.closeDrawer();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.user-drawer')).toBeNull();
    http.verify();
  });
});
