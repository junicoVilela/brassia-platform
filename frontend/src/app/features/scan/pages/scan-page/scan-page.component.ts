import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ScanApi } from '../../data-access/scan.api';

/**
 * Abre o que um código aponta (INT-003).
 *
 * <p><strong>Não há leitor de câmera aqui, e é uma decisão.</strong> O QR contém um link para esta rota, e
 * quem lê é o aplicativo de câmera que já vem no telefone — o mesmo que qualquer pessoa usa para ler um QR
 * de restaurante. Embutir um leitor significaria uma biblioteca a mais, permissão de câmera a pedir, e uma
 * experiência pior que a nativa em troca de nada.
 *
 * <p>A tela também aceita o código digitado, que é o caminho para quando a etiqueta está rasgada ou o
 * telefone não tem câmera — e é o que torna a funcionalidade utilizável num desktop.
 *
 * <p><strong>O 403 é dito com todas as letras.</strong> Quem apontou a câmera para uma etiqueta real
 * precisa saber que ela existe e que a alçada é que falta — mandar para uma tela vazia faria a pessoa
 * procurar o problema na etiqueta.
 */
@Component({
  selector: 'app-scan-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, LoadingIndicatorComponent],
  templateUrl: './scan-page.component.html',
})
export class ScanPageComponent implements OnInit {
  private readonly api = inject(ScanApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly resolving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required]],
  });

  ngOnInit(): void {
    // O QR aponta para /scan?code=brassia://…, então o link aberto pela câmera já traz o código.
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) {
      this.form.controls.code.setValue(code);
      this.resolve();
    }
  }

  protected resolve(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.resolving.set(true);
    this.error.set(null);
    this.api.resolve(this.form.getRawValue().code).subscribe({
      next: resolution => {
        this.resolving.set(false);
        void this.router.navigate([resolution.route], {
          queryParams: { ref: resolution.identifier },
        });
      },
      error: (response: HttpErrorResponse) => {
        this.resolving.set(false);
        this.error.set(this.messageFor(response));
      },
    });
  }

  private messageFor(response: HttpErrorResponse): string {
    if (response.status === 403) {
      // Dito com todas as letras: a etiqueta é real, o que falta é alçada.
      return 'Este código é válido, mas você não tem permissão para abrir o que ele aponta. Peça acesso a quem administra a cervejaria.';
    }
    if (response.status === 422) {
      return 'Este código não é reconhecido pelo sistema. Confira se a etiqueta está legível e inteira.';
    }
    if (response.status === 401) {
      return 'Sua sessão expirou. Entre novamente e leia o código de novo.';
    }
    return 'Não foi possível abrir este código agora.';
  }
}
