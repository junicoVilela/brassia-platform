import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { AuthApi } from './auth.api';
import { LoginRequest, LoginResult, MfaLoginRequest, SessionUser, isMfaRequired } from './session-user.model';

/** Estado de autenticação da aplicação (sessão via cookie no servidor). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly userState = signal<SessionUser | null>(null);
  /** true quando a sessão já foi consultada ao menos uma vez. */
  private readonly resolved = signal(false);

  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.userState() !== null);

  /** true se o principal atual possui a permissão informada. */
  hasPermission(permission: string): boolean {
    return this.userState()?.permissions.includes(permission) ?? false;
  }

  /** true se o principal possui ao menos uma das permissões informadas. */
  hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(permission => this.hasPermission(permission));
  }

  /**
   * Faz login: garante o token CSRF e autentica. Guarda o principal quando a
   * sessão conclui; se a conta exigir MFA, devolve `MFA_REQUIRED` sem autenticar.
   */
  login(request: LoginRequest): Observable<LoginResult> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.login(request)),
      tap(result => this.storeIfSession(result)),
    );
  }

  /** Conclui o login em duas etapas com o código do segundo fator (TOTP/recuperação). */
  completeMfa(request: MfaLoginRequest): Observable<SessionUser> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.completeMfa(request)),
      tap(user => {
        this.userState.set(user);
        this.resolved.set(true);
      }),
    );
  }

  private storeIfSession(result: LoginResult): void {
    if (!isMfaRequired(result)) {
      this.userState.set(result);
      this.resolved.set(true);
    }
  }

  logout(): Observable<void> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.logout()),
      tap(() => this.userState.set(null)),
    );
  }

  /** Troca a cervejaria ativa da sessão. */
  switchBrewery(breweryId: string): Observable<SessionUser> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.switchBrewery(breweryId)),
      tap(user => this.userState.set(user)),
    );
  }

  /**
   * Resolve a sessão atual (uma vez): consulta o servidor se ainda não foi
   * resolvida. Emite o usuário (ou null se não autenticado).
   */
  ensureSession(): Observable<SessionUser | null> {
    if (this.resolved()) {
      return of(this.userState());
    }
    return this.api.session().pipe(
      tap(user => this.userState.set(user)),
      map(user => user as SessionUser | null),
      catchError(() => {
        this.userState.set(null);
        return of(null);
      }),
      tap(() => this.resolved.set(true)),
    );
  }
}
