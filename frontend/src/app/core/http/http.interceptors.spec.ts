import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AUTH_TOKEN_PROVIDER, AuthTokenProvider } from './auth-token-provider';
import { authTokenInterceptor } from './auth-token.interceptor';
import {
  CORRELATION_ID_HEADER,
  CORRELATION_ID_PROVIDER,
  CorrelationIdProvider,
} from './correlation-id-provider';
import { ApiProblemError } from './problem-details.error';
import { problemDetailsInterceptor } from './problem-details.interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  const tokenProvider: AuthTokenProvider = {
    getAccessToken: () => 'token-for-test',
  };
  const correlationProvider: CorrelationIdProvider = {
    generate: () => '8d3d1dc4-fc59-4f07-8936-ecf7bc9d0a1f',
    isValid: (value) => value === '8d3d1dc4-fc59-4f07-8936-ecf7bc9d0a1f',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: AUTH_TOKEN_PROVIDER, useValue: tokenProvider },
        { provide: CORRELATION_ID_PROVIDER, useValue: correlationProvider },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds bearer authentication and generates a correlation id', () => {
    http.get('/api/v1/foundation').subscribe();

    const request = controller.expectOne('/api/v1/foundation');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-for-test');
    expect(request.request.headers.get(CORRELATION_ID_HEADER)).toBe(
      '8d3d1dc4-fc59-4f07-8936-ecf7bc9d0a1f',
    );
    request.flush({});
  });

  it('preserves a valid correlation id and does not overwrite explicit authorization', () => {
    http
      .get('/api/v1/foundation', {
        headers: new HttpHeaders({
          [CORRELATION_ID_HEADER]: '8d3d1dc4-fc59-4f07-8936-ecf7bc9d0a1f',
          Authorization: 'Basic explicit',
        }),
      })
      .subscribe();

    const request = controller.expectOne('/api/v1/foundation');
    expect(request.request.headers.get(CORRELATION_ID_HEADER)).toBe(
      '8d3d1dc4-fc59-4f07-8936-ecf7bc9d0a1f',
    );
    expect(request.request.headers.get('Authorization')).toBe('Basic explicit');
    request.flush({});
  });
});

describe('problemDetailsInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemDetailsInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('preserves RFC 9457 code, message, correlation id and field errors', () => {
    let received: unknown;
    http.get('/api/v1/foundation').subscribe({ error: (error: unknown) => (received = error) });

    const request = controller.expectOne('/api/v1/foundation');
    request.flush(
      {
        type: 'https://sms-modular.dev/problems/validation',
        title: 'Validation failed',
        status: 422,
        detail: 'Nieprawidłowe dane.',
        code: 'VALIDATION_ERROR',
        message: 'Formularz zawiera błędy.',
        correlationId: '1b0f17c3-03a4-4cb6-bb0c-38e5aa0f64b8',
        fieldErrors: [{ field: 'name', message: 'Pole jest wymagane.' }],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(received).toEqual(jasmine.any(ApiProblemError));
    const problem = (received as ApiProblemError).problem;
    expect(problem.status).toBe(422);
    expect(problem.code).toBe('VALIDATION_ERROR');
    expect(problem.message).toBe('Formularz zawiera błędy.');
    expect(problem.correlationId).toBe('1b0f17c3-03a4-4cb6-bb0c-38e5aa0f64b8');
    expect(problem.errors[0]).toEqual({ field: 'name', messages: ['Pole jest wymagane.'] });
  });

  it('keeps explicit 401 and 403 problem contracts', () => {
    const responses = [
      { status: 401, code: 'UNAUTHORIZED', message: 'Sesja wygasła.' },
      { status: 403, code: 'FORBIDDEN', message: 'Brak dostępu.' },
    ];

    for (const response of responses) {
      let received: unknown;
      http.get(`/api/v1/foundation/${response.status}`).subscribe({
        error: (error: unknown) => (received = error),
      });
      const request = controller.expectOne(`/api/v1/foundation/${response.status}`);
      request.flush(response, { status: response.status, statusText: 'Error' });

      expect(received).toEqual(jasmine.any(ApiProblemError));
      expect((received as ApiProblemError).problem.status).toBe(response.status);
      expect((received as ApiProblemError).problem.code).toBe(response.code);
      expect((received as ApiProblemError).problem.message).toBe(response.message);
    }
  });

  it('maps a network failure to a retryable application error', () => {
    let received: unknown;
    http.get('/api/v1/foundation').subscribe({ error: (error: unknown) => (received = error) });

    const request = controller.expectOne('/api/v1/foundation');
    request.error(new ProgressEvent('error'));

    expect(received).toEqual(jasmine.any(ApiProblemError));
    expect((received as ApiProblemError).problem.code).toBe('NETWORK_ERROR');
    expect((received as ApiProblemError).problem.status).toBe(0);
  });
});
