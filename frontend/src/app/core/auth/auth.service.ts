import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { OfflineRunbookStore } from '../offline/offline-runbook.store';
import { AuthApi } from './auth.api';
import { LoginRequest, LoginResult, MfaLoginRequest, SessionUser, isMfaRequired } from './session-user.model';

/** Estado de autenticação da aplicação (sessão via cookie no servidor). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly offlineRunbooks = inject(OfflineRunbookStore);
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

  /**
   * Encerra a sessão e **apaga o que ficou no aparelho** (PWA-001).
   *
   * As verificações de dono e cervejaria do `OfflineRunbookStore` já impediriam outra pessoa de ler os
   * roteiros salvos, mas impedir a leitura não basta: o dado continuaria no disco, e um tablet de chão de
   * fábrica se perde. Sair da conta tem que significar que não sobrou nada.
   *
   * A limpeza acontece mesmo se a chamada ao servidor falhar — o `finalize` cobre erro e sucesso. Ficar
   * sem rede na hora de sair é justamente quando alguém entrega o aparelho para o próximo turno.
   */
  logout(): Observable<void> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.logout()),
      tap(() => this.userState.set(null)),
      finalize(() => this.offlineRunbooks.clearAll()),
    );
  }

  /**
   * Troca a cervejaria ativa da sessão.
   *
   * Os roteiros salvos são apagados junto: eles pertencem à cervejaria anterior, e deixá-los no disco
   * guardaria dado de uma cervejaria enquanto se opera outra.
   */
  switchBrewery(breweryId: string): Observable<SessionUser> {
    return this.api.csrf().pipe(
      switchMap(() => this.api.switchBrewery(breweryId)),
      tap(user => {
        this.userState.set(user);
        this.offlineRunbooks.clearAll();
      }),
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
