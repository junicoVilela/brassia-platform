import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { SensorsStore } from '../../data-access/sensors.store';
import {
  DeviceStatus,
  MEASURE_ICONS,
  MEASURE_LABELS,
  MEASURE_UNITS,
  Measure,
  PAYLOAD_FORMAT_LABELS,
  PayloadFormat,
  QUALITY_LABELS,
  STATUS_LABELS,
  SensorDevice,
  SensorReading,
  UNIT_LABELS,
} from '../../domain/sensor.model';

/**
 * Sensores vistos por quem opera (INT-001).
 *
 * <p>Três coisas que a tela precisa deixar claras, e nenhuma é detalhe de layout:
 *
 * <p><strong>Leitura sinalizada é leitura, não erro.</strong> Ela aparece na série, no lugar dela, marcada
 * com o motivo. Escondê-la deixaria um buraco na curva — e um buraco é indistinguível de "o sensor não
 * mediu", que é a leitura errada da situação.
 *
 * <p><strong>Qualidade e atraso são colunas separadas.</strong> Uma leitura pode ter valor perfeito e ter
 * chegado três horas tarde; outra pode chegar na hora com o sensor fora d'água. As providências são
 * diferentes — rede e sensor —, e uma coluna só obrigaria a adivinhar qual.
 *
 * <p><strong>Revogar não se parece com pausar.</strong> Pausar é manutenção e volta com um clique; revogar
 * descontinua a identidade e não tem volta. Por isso o botão pede confirmação e diz isso com todas as
 * letras.
 */
@Component({
  selector: 'app-sensors-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [SensorsStore],
  templateUrl: './sensors-page.component.html',
})
export class SensorsPageComponent implements OnInit {
  protected readonly store = inject(SensorsStore);
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly measureLabels = MEASURE_LABELS;
  protected readonly measureIcons = MEASURE_ICONS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly qualityLabels = QUALITY_LABELS;
  protected readonly unitLabels = UNIT_LABELS;
  protected readonly measures = Object.keys(MEASURE_LABELS) as Measure[];
  protected readonly formatLabels = PAYLOAD_FORMAT_LABELS;
  protected readonly formats = Object.keys(PAYLOAD_FORMAT_LABELS) as PayloadFormat[];

  protected readonly registerForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    measure: ['TEMPERATURE' as Measure, [Validators.required]],
    unit: ['C', [Validators.required]],
    expectedIntervalSeconds: [300],
    payloadFormat: ['CANONICAL' as PayloadFormat, [Validators.required]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  /** As unidades mudam com a grandeza — oferecer PSI para temperatura seria oferecer um erro. */
  protected unitsFor(measure: Measure): string[] {
    return MEASURE_UNITS[measure];
  }

  protected onMeasureChange(): void {
    const measure = this.registerForm.controls.measure.value;
    this.registerForm.controls.unit.setValue(MEASURE_UNITS[measure][0]);
  }

  protected register(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
    const value = this.registerForm.getRawValue();
    this.store.register({
      code: value.code,
      name: value.name,
      measure: value.measure,
      unit: value.unit,
      equipmentId: null,
      expectedIntervalSeconds: value.expectedIntervalSeconds || null,
      payloadFormat: value.payloadFormat,
    });
    this.registerForm.reset({
      measure: 'TEMPERATURE',
      unit: 'C',
      expectedIntervalSeconds: 300,
      payloadFormat: 'CANONICAL',
    });
  }

  protected select(device: SensorDevice): void {
    this.store.select(device.id);
  }

  protected pause(device: SensorDevice): void {
    this.store.changeStatus(device, 'PAUSED');
  }

  protected resume(device: SensorDevice): void {
    this.store.changeStatus(device, 'ACTIVE');
  }

  /**
   * Revogar pede confirmação porque é irreversível.
   *
   * <p>A confirmação diz o que acontece, não "tem certeza?": quem lê precisa saber que a identidade não
   * volta e que o caminho de volta é cadastrar outro dispositivo.
   */
  protected revoke(device: SensorDevice): void {
    const confirmed = window.confirm(
      `Revogar ${device.code}?\n\n` +
        'A identidade é descontinuada e não volta a operar. As leituras já recebidas continuam no ' +
        'histórico. Para voltar a medir, será preciso cadastrar outro dispositivo.',
    );
    if (confirmed) {
      this.store.changeStatus(device, 'REVOKED');
    }
  }

  protected statusClass(status: DeviceStatus): string {
    return status === 'ACTIVE' ? 'bg-success' : status === 'PAUSED' ? 'bg-warning' : 'bg-secondary';
  }

  protected qualityClass(reading: SensorReading): string {
    return reading.quality === 'GOOD' ? 'bg-success-subtle text-success-emphasis' : 'bg-danger-subtle text-danger-emphasis';
  }

  /** Atraso legível: "3 min", "2 h 10 min". Segundos crus não respondem "isso é muito?". */
  protected humanDelay(seconds: number): string {
    const abs = Math.abs(seconds);
    if (abs < 60) {
      return `${abs}s`;
    }
    const minutes = Math.floor(abs / 60);
    if (minutes < 60) {
      return `${minutes} min`;
    }
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
  }

  protected intervalLabel(device: SensorDevice): string {
    return device.expectedIntervalSeconds === null
      ? 'sem frequência definida'
      : `a cada ${this.humanDelay(device.expectedIntervalSeconds)}`;
  }
}
