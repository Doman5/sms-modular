import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { PageRequest, PageResponse } from '../../core/api/api.models';
import {
  DeadLetter,
  DeadLetterPage,
  DeadLetterResponseDto,
  mapDeadLetter,
} from './integration-runtime.models';

const BASE_URL = '/api/platform/v1/integration-runtime/dead-letters';

@Injectable({ providedIn: 'root' })
export class IntegrationRuntimeApiService {
  private readonly http = inject(HttpClient);

  list(tenantId: string, page: PageRequest): Observable<DeadLetterPage> {
    return this.http
      .get<PageResponse<DeadLetterResponseDto>>(BASE_URL, { params: this.params(tenantId, page) })
      .pipe(map((response) => ({ ...response, items: response.items.map(mapDeadLetter) })));
  }

  get(tenantId: string, id: string): Observable<DeadLetter> {
    return this.http
      .get<DeadLetterResponseDto>(`${BASE_URL}/${id}`, {
        params: new HttpParams().set('tenantId', tenantId),
      })
      .pipe(map(mapDeadLetter));
  }

  retry(tenantId: string, id: string): Observable<DeadLetter> {
    return this.http
      .post<DeadLetterResponseDto>(`${BASE_URL}/${id}/retry`, null, {
        params: new HttpParams().set('tenantId', tenantId),
      })
      .pipe(map(mapDeadLetter));
  }

  private params(tenantId: string, page: PageRequest): HttpParams {
    return new HttpParams().set('tenantId', tenantId).set('page', page.page).set('size', page.size);
  }
}
