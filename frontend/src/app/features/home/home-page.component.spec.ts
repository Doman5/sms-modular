import { TestBed } from '@angular/core/testing';
import { HomePageComponent } from './home-page.component';

describe('HomePageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HomePageComponent] }).compileComponents();
  });

  it('renders the foundation dashboard without fake domain data', () => {
    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Witaj w SMS Modular');
    expect(fixture.nativeElement.textContent).toContain('Brak danych tenanta');
    expect(fixture.nativeElement.textContent).toContain('Moduły pojawią się po podłączeniu API');
  });
});
