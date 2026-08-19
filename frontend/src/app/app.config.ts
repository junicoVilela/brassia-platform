import {
  ApplicationConfig,
  LOCALE_ID,
  isDevMode,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { problemDetailsInterceptor } from './core/http/problem-details.interceptor';

/**
 * O locale é registrado uma vez, no carregamento do módulo.
 *
 * <p><strong>Sem isto, todo `| number` e todo `| date` sem formato caem no `en-US` padrão do Angular</strong>
 * — e o sistema mostrava `200.00` onde a casa lê `200,00`. Num sistema que fala de dinheiro, litro e
 * densidade o tempo inteiro, ponto e vírgula trocados não são estética: `1.234` é mil e duzentos para o
 * servidor e um e pouco para quem lê, e é o operador que decide com base no que está na tela.
 */
registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    // A aplicação já declarava `pt-BR` no `index.html` e no E2E; o que faltava era dizer ao Angular.
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    provideHttpClient(withInterceptors([problemDetailsInterceptor])),
    /**
     * Service worker (PWA-001).
     *
     * <p><strong>O `ngsw-config.json` não tem `dataGroups`, e a ausência é a decisão.</strong> Cachear
     * respostas de API por padrão de URL resolveria "ler offline" em três linhas — e junto guardaria
     * *tudo* que casasse com o padrão, de forma invisível, num armazenamento que sobrevive ao logout e é
     * legível por quem usar o aparelho depois. Um tablet de chão de fábrica é compartilhado por turno;
     * um cache de "o que passou pela API" acaba guardando o custo do lote porque alguém abriu a tela uma
     * vez. O que o service worker cacheia aqui é só a aplicação — código e assets, que são públicos.
     *
     * <p>O dado do roteiro é guardado pelo `OfflineRunbookStore`, que exige escolha explícita, carimba
     * dono e cervejaria, vence em doze horas e é apagado no logout.
     *
     * <p>`registerWhenStable:30000` adia o registro até a aplicação estabilizar: registrar durante o
     * carregamento compete por banda justamente com o que a pessoa está esperando ver.
     */
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
