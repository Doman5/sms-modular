import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { PageRequest, PageResponse } from '../../core/api/api.models';
import {
  CreateTenantRequestDto,
  Tenant,
  TenantPage,
  TenantResponseDto,
  TenantSettingsPatchDto,
  mapTenantResponse,
} from './tenancy.models';

const TENANT_URL = '/api/v1/tenant';
const PLATFORM_TENANTS_URL = '/api/platform/v1/tenants';

@Injectable({ providedIn: 'root' })
export class TenancyApiService {
  private readonly http = inject(HttpClient);

  getTenant(): Observable<Tenant> {
    return this.http.get<TenantResponseDto>(TENANT_URL).pipe(map(mapTenantResponse));
  }

  updateTenant(patch: TenantSettingsPatchDto): Observable<Tenant> {
    return this.http.patch<TenantResponseDto>(TENANT_URL, patch).pipe(map(mapTenantResponse));
  }

  listPlatformTenants(page: PageRequest): Observable<TenantPage> {
    const params = new HttpParams()
      .set('page', page.page.toString())
      .set('size', page.size.toString());

    return this.http.get<PageResponse<TenantResponseDto>>(PLATFORM_TENANTS_URL, { params }).pipe(
      map((response) => ({
        ...response,
        items: response.items.map(mapTenantResponse),
      })),
    );
  }

  getPlatformTenant(tenantId: string): Observable<Tenant> {
    return this.http
      .get<TenantResponseDto>(`${PLATFORM_TENANTS_URL}/${tenantId}`)
      .pipe(map(mapTenantResponse));
  }

  createPlatformTenant(request: CreateTenantRequestDto): Observable<Tenant> {
    return this.http
      .post<TenantResponseDto>(PLATFORM_TENANTS_URL, request)
      .pipe(map(mapTenantResponse));
  }

  updatePlatformTenant(tenantId: string, patch: TenantSettingsPatchDto): Observable<Tenant> {
    return this.http
      .patch<TenantResponseDto>(`${PLATFORM_TENANTS_URL}/${tenantId}`, patch)
      .pipe(map(mapTenantResponse));
  }

  suspendPlatformTenant(tenantId: string): Observable<Tenant> {
    return this.lifecycle(tenantId, 'suspend');
  }

  activatePlatformTenant(tenantId: string): Observable<Tenant> {
    return this.lifecycle(tenantId, 'activate');
  }

  closePlatformTenant(tenantId: string): Observable<Tenant> {
    return this.lifecycle(tenantId, 'close');
  }

  private lifecycle(
    tenantId: string,
    action: 'suspend' | 'activate' | 'close',
  ): Observable<Tenant> {
    return this.http
      .post<TenantResponseDto>(`${PLATFORM_TENANTS_URL}/${tenantId}/${action}`, {})
      .pipe(map(mapTenantResponse));
  }
}
