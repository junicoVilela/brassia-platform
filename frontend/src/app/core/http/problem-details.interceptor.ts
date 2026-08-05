import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ProblemDetails } from './problem-details';

/**
 * Desembrulha o corpo do erro: quem trata recebe o **Problem Details**, não o `HttpErrorResponse`.
 *
 * <p>Isto define o contrato dos stores, e vale escrever por extenso porque a confusão custou caro:
 * o que chega ao `error:` do subscribe tem `code`, `detail`, `status` e as extensões **no primeiro
 * nível**. Ler `e.error?.code` não dá erro de compilação nem de teste — só devolve `undefined`, e a
 * tela cai calada na mensagem genérica. Sete stores estavam assim, e os testes de unidade
 * concordavam com eles porque simulavam o erro na forma errada. Quem pegou foi o E2E da TRC-001,
 * contra a stack real.
 *
 * <p>Sem corpo (falha de rede, 502 de gateway), repassa o `HttpErrorResponse` — daí `status`
 * continuar sendo a única coisa que dá para checar nesses casos.
 */
export const problemDetailsInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const problem = error.error as ProblemDetails | undefined;
      return throwError(() => problem ?? error);
    }),
  );
