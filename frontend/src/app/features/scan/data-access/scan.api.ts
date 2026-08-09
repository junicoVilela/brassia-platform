import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** O que o servidor devolve para um código reconhecido (INT-003). */
export interface ScanResolution {
  type: string;
  identifier: string;
  route: string;
}

@Injectable({ providedIn: 'root' })
export class ScanApi {
  private readonly http = inject(HttpClient);

  /**
   * Resolve um código lido.
   *
   * <p>É `GET` porque ler não altera nada — e é isso que permite ao QR conter um link que o aplicativo de
   * câmera do telefone abre sozinho, sem instalar nada e sem biblioteca de leitura do nosso lado.
   */
  resolve(code: string): Observable<ScanResolution> {
    return this.http.get<ScanResolution>('/api/v1/integration/scan', { params: { code } });
  }
}
