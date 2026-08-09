import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  DeviceStatus,
  RegisterDeviceRequest,
  SensorDevice,
  SensorReading,
} from '../domain/sensor.model';
import { SensorsApi } from './sensors.api';

/**
 * O erro como o Angular o entrega.
 *
 * <p>O Problem Details do servidor chega em `error`, não na raiz — `HttpErrorResponse` reserva o topo
 * para o status HTTP e embrulha o corpo. Ler `e.code` direto compila, sempre devolve `undefined` e faz
 * toda recusa cair na mensagem genérica, que é o defeito mais silencioso possível: a tela funciona e
 * nunca diz a coisa certa.
 */
interface SensorError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/** Quantas horas para trás a tela mostra por padrão. */
const WINDOW_HOURS = 24;

/**
 * Estado dos sensores (INT-001).
 *
 * <p>Dispositivos e leituras são estados separados porque falham separado: a lista de dispositivos pode
 * carregar enquanto a série de um deles não, e trocar isso por um único `loading` faria a tela inteira
 * piscar por causa de um sensor só.
 */
@Injectable()
export class SensorsStore {
  private readonly api = inject(SensorsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly devices = signal<SensorDevice[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly registering = signal(false);
  readonly registerError = signal<string | null>(null);

  readonly selectedId = signal<string | null>(null);
  readonly readings = signal<SensorReading[]>([]);
  readonly loadingReadings = signal(false);
  readonly readingsError = signal<string | null>(null);

  readonly selected = computed(() => this.devices().find(d => d.id === this.selectedId()) ?? null);

  readonly activeDevices = computed(() => this.devices().filter(d => d.status === 'ACTIVE'));
  readonly inactiveDevices = computed(() => this.devices().filter(d => d.status !== 'ACTIVE'));

  /**
   * As leituras que merecem atenção.
   *
   * Qualidade e atraso são somados aqui porque, para quem opera, a pergunta é uma só: "algo está errado
   * com este sensor?". Eles continuam distintos na linha da tabela, onde a causa importa.
   */
  readonly flaggedReadings = computed(() =>
    this.readings().filter(r => r.quality !== 'GOOD' || r.late),
  );

  readonly hasFlagged = computed(() => this.flaggedReadings().length > 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .devices()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: devices => this.devices.set(devices),
        error: () => this.error.set('Não foi possível carregar os dispositivos.'),
      });
  }

  register(request: RegisterDeviceRequest): void {
    this.registering.set(true);
    this.registerError.set(null);
    this.api
      .register(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.registering.set(false)),
      )
      .subscribe({
        next: device => {
          this.toast.success(`Dispositivo ${device.code} cadastrado.`);
          this.load();
        },
        error: (e: SensorError) => this.registerError.set(this.messageFor(e)),
      });
  }

  changeStatus(device: SensorDevice, target: DeviceStatus): void {
    this.api
      .changeStatus(device.id, target, device.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.toast.success(
            updated.status === 'REVOKED'
              ? `${updated.code} revogado. A identidade não volta a operar.`
              : `${updated.code} agora está ${updated.status === 'ACTIVE' ? 'ativo' : 'pausado'}.`,
          );
          this.load();
        },
        error: (e: SensorError) => this.toast.error(this.messageFor(e)),
      });
  }

  select(deviceId: string): void {
    this.selectedId.set(deviceId);
    this.loadReadings(deviceId);
  }

  loadReadings(deviceId: string): void {
    this.loadingReadings.set(true);
    this.readingsError.set(null);
    const to = new Date();
    const from = new Date(to.getTime() - WINDOW_HOURS * 3600 * 1000);
    this.api
      .readings(deviceId, from.toISOString(), to.toISOString())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loadingReadings.set(false)),
      )
      .subscribe({
        next: readings => this.readings.set(readings),
        error: (e: SensorError) => {
          this.readings.set([]);
          this.readingsError.set(this.messageFor(e));
        },
      });
  }

  private messageFor(e: SensorError): string {
    // O código do domínio primeiro: ele diz o que aconteceu, enquanto o status diz apenas a categoria.
    // Duas recusas bem diferentes compartilham o 409 — dispositivo inativo e versão desatualizada — e
    // decidir pelo status mandaria quem opera procurar o problema no lugar errado.
    const code = e.error?.code;
    if (code === 'sensor_device_inactive') {
      return 'Este dispositivo não está aceitando leituras.';
    }
    if (code === 'unknown_sensor_device') {
      return 'Este dispositivo não está cadastrado nesta cervejaria.';
    }
    if (e.status === 403) {
      return 'Revogar um dispositivo é alçada própria, separada da de administrá-lo.';
    }
    if (e.status === 409) {
      return 'O dispositivo foi alterado por outra pessoa. Recarregue e tente novamente.';
    }
    if (e.status === 400) {
      return 'Confira os campos: a unidade precisa ser compatível com a grandeza.';
    }
    return e.error?.detail ?? 'Não foi possível concluir a operação.';
  }
}
