import { DestroyRef, Injectable, inject, signal } from '@angular/core';

/**
 * Se há rede, do ponto de vista do navegador (PWA-001).
 *
 * <p><strong>`navigator.onLine` responde uma pergunta mais fraca do que parece.</strong> Ele diz que a
 * máquina tem *alguma* interface ativa, não que o servidor está alcançável: o wi-fi da cervejaria pode
 * estar conectado e sem saída para a internet, e `onLine` continua `true`. Por isso ele é usado aqui como
 * **dica de interface**, para explicar o que a tela está mostrando — nunca como autoridade sobre o que
 * fazer. Quem decide se a requisição funcionou é a requisição.
 *
 * <p>O caminho inverso é confiável e é o que importa: `onLine === false` significa que *não* há rede, e
 * nesse caso vale a pena nem tentar.
 */
@Injectable({ providedIn: 'root' })
export class NetworkStatusService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly onlineState = signal(this.currentlyOnline());

  readonly online = this.onlineState.asReadonly();

  constructor() {
    const goOnline = () => this.onlineState.set(true);
    const goOffline = () => this.onlineState.set(false);
    globalThis.addEventListener?.('online', goOnline);
    globalThis.addEventListener?.('offline', goOffline);
    this.destroyRef.onDestroy(() => {
      globalThis.removeEventListener?.('online', goOnline);
      globalThis.removeEventListener?.('offline', goOffline);
    });
  }

  private currentlyOnline(): boolean {
    // Ausente em ambiente de teste e em SSR: na dúvida, assume-se que há rede. O contrário faria a
    // aplicação abrir em modo offline sem motivo.
    return globalThis.navigator?.onLine ?? true;
  }
}
