import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountStatus } from '../../domain/user.model';
import { UsersStore } from '../../data-access/users.store';

@Component({
  selector: 'app-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [UsersStore],
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <h1 class="h4 mb-0 fw-semibold">Usuários</h1>
    </div>

    <div class="card border-0 rounded-10 mb-4">
      <div class="card-body p-4">
        <h2 class="h6 fw-semibold mb-3">Convidar usuário</h2>
        <form [formGroup]="form" (ngSubmit)="invite()" class="row g-3 align-items-end">
          <div class="col-sm-5">
            <label class="form-label" for="email">E-mail</label>
            <input id="email" type="email" class="form-control" formControlName="email"
                   placeholder="pessoa@cervejaria.com">
          </div>
          <div class="col-sm-5">
            <label class="form-label" for="displayName">Nome</label>
            <input id="displayName" type="text" class="form-control" formControlName="displayName"
                   placeholder="Nome de exibição">
          </div>
          <div class="col-sm-2 d-grid">
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid || store.submitting()">
              <i class="ri-user-add-line me-1"></i> Convidar
            </button>
          </div>
        </form>
      </div>
    </div>

    @if (store.actionError(); as actionError) {
      <div class="alert alert-danger" role="alert">{{ actionError }}</div>
    }

    <div class="card border-0 rounded-10">
      <div class="card-body p-4">
        @if (store.loading()) {
          <p class="text-muted mb-0"><span class="spinner-border spinner-border-sm me-2"></span>Carregando…</p>
        } @else if (store.error(); as error) {
          <div class="alert alert-danger mb-0" role="alert">{{ error }}</div>
        } @else if (store.empty()) {
          <p class="text-muted mb-0">Nenhum usuário cadastrado ainda.</p>
        } @else {
          <div class="table-responsive">
            <table class="table align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col">Nome</th>
                  <th scope="col">E-mail</th>
                  <th scope="col">Status</th>
                  <th scope="col" class="text-end">Ações</th>
                </tr>
              </thead>
              <tbody>
                @for (user of store.items(); track user.id) {
                  <tr>
                    <td class="fw-medium">{{ user.displayName }}</td>
                    <td>{{ user.email }}</td>
                    <td><span class="badge" [class]="badgeClass(user.status)">{{ user.status }}</span></td>
                    <td class="text-end">
                      @if (user.status === 'ACTIVE') {
                        <button type="button" class="btn btn-sm btn-outline-warning me-1"
                                [disabled]="store.submitting()" (click)="store.block(user.id)">Bloquear</button>
                      }
                      @if (user.status === 'LOCKED') {
                        <button type="button" class="btn btn-sm btn-outline-success me-1"
                                [disabled]="store.submitting()" (click)="store.unblock(user.id)">Desbloquear</button>
                      }
                      @if (user.status !== 'DISABLED') {
                        <button type="button" class="btn btn-sm btn-outline-danger"
                                [disabled]="store.submitting()" (click)="store.disable(user.id)">Desativar</button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
})
export class UsersPageComponent implements OnInit {
  protected readonly store = inject(UsersStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    displayName: ['', [Validators.required, Validators.maxLength(160)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected invite(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.invite(this.form.getRawValue(), () => this.form.reset());
  }

  protected badgeClass(status: AccountStatus): string {
    switch (status) {
      case 'ACTIVE': return 'text-bg-success';
      case 'INVITED': return 'text-bg-info';
      case 'LOCKED': return 'text-bg-warning';
      case 'DISABLED': return 'text-bg-secondary';
    }
  }
}
