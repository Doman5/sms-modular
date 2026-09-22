import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { PageResponse, PageRequest } from '../../core/api/api.models';
import {
  AuditLog,
  AuditLogPage,
  AuditLogQuery,
  AuditLogResponseDto,
  mapAuditLogResponse,
} from './audit.models';

const AUDIT_URL = '/api/v1/audit-logs';
const PLATFORM_AUDIT_URL = '/api/platform/v1/audit-logs';

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);

  list(page: PageRequest, query: AuditLogQuery = {}): Observable<AuditLogPage> {
    return this.http
      .get<PageResponse<AuditLogResponseDto>>(AUDIT_URL, {
        params: this.params(page, query),
      })
      .pipe(map((response) => this.mapPage(response)));
  }

  get(auditId: string): Observable<AuditLog> {
    return this.http
      .get<AuditLogResponseDto>(`${AUDIT_URL}/${auditId}`)
      .pipe(map(mapAuditLogResponse));
  }

  listPlatform(
    tenantId: string,
    page: PageRequest,
    query: AuditLogQuery = {},
  ): Observable<AuditLogPage> {
    return this.http
      .get<PageResponse<AuditLogResponseDto>>(PLATFORM_AUDIT_URL, {
        params: this.params(page, query).set('tenantId', tenantId),
      })
      .pipe(map((response) => this.mapPage(response)));
  }

  getPlatform(tenantId: string, auditId: string): Observable<AuditLog> {
    return this.http
      .get<AuditLogResponseDto>(`${PLATFORM_AUDIT_URL}/${auditId}`, {
        params: new HttpParams().set('tenantId', tenantId),
      })
      .pipe(map(mapAuditLogResponse));
  }

  private params(page: PageRequest, query: AuditLogQuery): HttpParams {
    let params = new HttpParams().set('page', page.page).set('size', page.size);
    for (const [key, value] of Object.entries(query)) {
      if (value) {
        params = params.set(key, value);
      }
    }
    return params;
  }

  private mapPage(response: PageResponse<AuditLogResponseDto>): AuditLogPage {
    return {
      ...response,
      items: response.items.map(mapAuditLogResponse),
    };
  }
}
