import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  ComplainantContact,
  Complaint,
  RegisterComplaintRequest,
} from '../domain/complaint.model';
import { ComplaintsApi } from './complaints.api';

interface ComplaintError {
  status?: number;
  error?: { code?: string; detail?: string; pendingActions?: string[]; currentStatus?: string };
}

/**
 * Estado das reclamações de campo (FLD-001).
 *
 * <p><strong>O contato não fica guardado junto com a reclamação, nem em cache.</strong> Ele vive num sinal
 * próprio que é limpo ao trocar de reclamação — porque manter dado pessoal em memória depois que a tela
 * saiu dele é a versão em cliente do problema que a separação de tabelas resolveu no servidor: o dado
 * disponível para quem não pediu.
 */
@Injectable()
export class ComplaintsStore {
  private readonly api = inject(ComplaintsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly complaints = signal<Complaint[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly registering = signal(false);
  readonly registerError = signal<string | null>(null);

  /** O contato exibido no momento, e de qual reclamação. Nunca dois ao mesmo tempo. */
  readonly contact = signal<ComplainantContact | null>(null);
  readonly contactFor = signal<string | null>(null);
  readonly contactError = signal<string | null>(null);

  readonly open = computed(() => this.complaints().filter(c => c.status !== 'CLOSED'));
  readonly closed = computed(() => this.complaints().filter(c => c.status === 'CLOSED'));

  /** As que exigem algo e ainda não têm destino: é a fila que não pode ser esquecida. */
  readonly blocked = computed(() => this.open().filter(c => c.pendingActions.length > 0));

  load(batchId?: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(batchId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: complaints => this.complaints.set(complaints),
        error: () => this.error.set('Não foi possível carregar as reclamações.'),
      });
  }

  register(request: RegisterComplaintRequest, onSuccess: () => void): void {
    this.registering.set(true);
    this.registerError.set(null);
    this.api
      .register(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.registering.set(false)),
      )
      .subscribe({
        next: complaint => {
          // O aviso diz o que passou a ser exigido, no momento em que ainda dá para agir.
          this.toast.success(
            complaint.requiredActions.length === 0
              ? 'Reclamação registrada.'
              : `Reclamação registrada. Exige ${complaint.requiredActions.length} ação(ões) antes ` +
                  'de poder ser encerrada.',
          );
          onSuccess();
          this.load();
        },
        error: (e: ComplaintError) => this.registerError.set(this.messageFor(e)),
      });
  }

  startAnalysis(id: string): void {
    this.run(this.api.startAnalysis(id));
  }

  fulfill(id: string, action: string, referenceId: string): void {
    this.run(this.api.fulfill(id, action, referenceId));
  }

  waive(id: string, action: string, justification: string): void {
    this.run(this.api.waive(id, action, justification));
  }

  close(id: string, note: string): void {
    this.run(this.api.close(id, note));
  }

  /**
   * Busca o contato.
   *
   * Sempre do servidor, nunca de cache: cada leitura precisa gerar seu registro de auditoria. Reaproveitar
   * um valor já carregado economizaria uma chamada e apagaria o rastro do segundo acesso.
   */
  loadContact(id: string): void {
    this.clearContact();
    this.contactFor.set(id);
    this.api
      .contact(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: contact => this.contact.set(contact),
        error: (e: ComplaintError) =>
          this.contactError.set(
            e.status === 403
              ? 'Ver dados pessoais exige permissão própria — e cada leitura fica registrada.'
              : 'Esta reclamação não tem contato registrado.',
          ),
      });
  }

  /** Some da tela e da memória. */
  clearContact(): void {
    this.contact.set(null);
    this.contactFor.set(null);
    this.contactError.set(null);
  }

  eraseContact(id: string): void {
    this.api
      .eraseContact(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(
            'Dados pessoais apagados. A reclamação e a investigação permanecem íntegras.',
          );
          this.clearContact();
        },
        error: (e: ComplaintError) => this.toast.error(this.messageFor(e)),
      });
  }

  private run(request: import('rxjs').Observable<Complaint>): void {
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.load(),
      error: (e: ComplaintError) => this.toast.error(this.messageFor(e)),
    });
  }

  private messageFor(e: ComplaintError): string {
    // `e.error` e não `e`: o HttpErrorResponse embrulha o corpo.
    const code = e.error?.code;
    if (code === 'pending_required_actions') {
      const pendentes = e.error?.pendingActions ?? [];
      return (
        `Não dá para encerrar: ${pendentes.length} ação(ões) exigida(s) sem destino. ` +
        'Atenda cada uma, ou dispense-a por escrito — a dispensa é registrada com seu nome.'
      );
    }
    if (code === 'unknown_complaint_batch') {
      return e.error?.detail ?? 'O lote informado não existe nesta cervejaria.';
    }
    if (code === 'illegal_complaint_transition') {
      return `A reclamação está em ${e.error?.currentStatus ?? 'outro estado'}. Recarregue a lista.`;
    }
    if (e.status === 403) {
      return 'Esta operação exige permissão própria.';
    }
    return e.error?.detail ?? 'Não foi possível concluir a operação.';
  }
}
