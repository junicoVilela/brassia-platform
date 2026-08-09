import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { Evidence, IndexRequest, KnowledgeDocument } from '../domain/knowledge.model';
import { KnowledgeApi } from './knowledge.api';

interface KnowledgeError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado da base de conhecimento (RAG-001).
 *
 * <p>Busca e listagem são estados separados de propósito: uma busca vazia é resposta legítima ("não há
 * fonte para isto") e não pode limpar a lista de documentos nem parecer um erro. Misturar os dois faria
 * "não achei" e "não carregou" virarem a mesma tela.
 */
@Injectable()
export class KnowledgeStore {
  private readonly api = inject(KnowledgeApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly documents = signal<KnowledgeDocument[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly indexing = signal(false);
  readonly indexError = signal<string | null>(null);

  readonly hits = signal<Evidence[]>([]);
  readonly searching = signal(false);
  readonly searchError = signal<string | null>(null);
  /** Nulo antes da primeira busca: "ainda não perguntei" não é "não achei". */
  readonly lastQuestion = signal<string | null>(null);

  readonly searchedWithoutResult = computed(
    () => this.lastQuestion() !== null && !this.searching() && this.hits().length === 0,
  );

  /** Só as versões vigentes — é o que responde sobre hoje. */
  readonly currentDocuments = computed(() => this.documents().filter(doc => doc.current));

  /** As substituídas, que continuam respondendo sobre o período em que valeram. */
  readonly supersededDocuments = computed(() => this.documents().filter(doc => !doc.current));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .documents()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: documents => this.documents.set(documents),
        error: () => this.error.set('Não foi possível carregar a base de conhecimento.'),
      });
  }

  index(request: IndexRequest): void {
    this.indexing.set(true);
    this.indexError.set(null);
    this.api
      .index(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.indexing.set(false)),
      )
      .subscribe({
        next: document => {
          this.toast.success(
            document.version > 1
              ? `Versão ${document.version} indexada; a anterior foi encerrada.`
              : 'Documento indexado.',
          );
          this.load();
        },
        error: (e: KnowledgeError) => this.indexError.set(this.messageFor(e)),
      });
  }

  search(question: string, onDate: string | null, equipmentId: string | null): void {
    this.searching.set(true);
    this.searchError.set(null);
    this.lastQuestion.set(question);
    this.api
      .search(question, onDate, equipmentId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.searching.set(false)),
      )
      .subscribe({
        next: hits => this.hits.set(hits),
        error: (e: KnowledgeError) => {
          this.hits.set([]);
          this.searchError.set(this.messageFor(e));
        },
      });
  }

  private messageFor(e: KnowledgeError): string {
    if (e.status === 403) {
      return 'Indexar documento é alçada própria, separada da de consultar.';
    }
    if (e.status === 400) {
      return 'Confira os campos: documento sem texto indexável não entra na base.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
