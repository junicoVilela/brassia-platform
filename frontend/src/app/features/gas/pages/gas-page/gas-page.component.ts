import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { GasStore } from '../../data-access/gas.store';
import {
  BalanceInput,
  CONNECTION_STATUS_LABELS,
  CYLINDER_STATUS_LABELS,
  COMPONENT_KIND_LABELS,
  GasConnection,
  GasCylinder,
  GasType,
  ServiceLine,
} from '../../domain/gas.model';

@Component({
  selector: 'app-gas-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [GasStore],
  templateUrl: './gas-page.component.html',
})
export class GasPageComponent implements OnInit {
  protected readonly store = inject(GasStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('gas.manage');
  protected readonly cylinderLabels = CYLINDER_STATUS_LABELS;
  protected readonly connectionLabels = CONNECTION_STATUS_LABELS;
  protected readonly kindLabels = COMPONENT_KIND_LABELS;

  protected readonly cylinderForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    gasType: ['CO2' as GasType, Validators.required],
    capacityKg: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    tareKg: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    contentKg: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    requalificationDueOn: ['', Validators.required],
    location: ['', [Validators.required, Validators.maxLength(120)]],
  });

  protected readonly connectForm = this.fb.nonNullable.group({
    cylinderId: ['', Validators.required],
    regulatorId: ['', Validators.required],
    manifoldId: [''],
    pointOfUseEquipmentId: ['', Validators.required],
    workingPressureBar: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
  });

  // --- linha de serviço (GAS-002) ---

  protected readonly lineForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    pointOfUseEquipmentId: ['', Validators.required],
  });

  protected readonly tubingForm = this.fb.nonNullable.group({
    material: ['', [Validators.required, Validators.maxLength(60)]],
    internalDiameterMm: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    resistanceBarPerMeter: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
    referenceFlowLpm: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
  });

  /** O desnível pode ser negativo: a torneira pode ficar abaixo do barril. */
  protected readonly balanceForm = this.fb.nonNullable.group({
    targetCo2Volumes: this.fb.control<number | null>(2.5, [Validators.required, Validators.min(0.01)]),
    servingTempC: this.fb.control<number | null>(4, Validators.required),
    elevationMeters: this.fb.control<number | null>(0, Validators.required),
    residualPressureBar: this.fb.control<number | null>(0.069, [Validators.required, Validators.min(0)]),
    targetFlowLpm: this.fb.control<number | null>(1, [Validators.required, Validators.min(0.001)]),
    resistanceId: ['', Validators.required],
    appliedLengthMeters: this.fb.control<number | null>(null, [Validators.min(0.001)]),
    note: [''],
  });

  protected toggleLine(line: ServiceLine): void {
    this.store.toggleLine(line.id);
  }

  protected registerLine(): void {
    if (this.lineForm.invalid) {
      return;
    }
    const v = this.lineForm.getRawValue();
    this.store.registerServiceLine(v.code, v.name, v.pointOfUseEquipmentId,
      () => this.lineForm.reset({ code: '', name: '', pointOfUseEquipmentId: '' }));
  }

  protected registerTubing(): void {
    if (this.tubingForm.invalid) {
      return;
    }
    const v = this.tubingForm.getRawValue();
    this.store.registerTubing(v.material, v.internalDiameterMm!, v.resistanceBarPerMeter!,
      v.referenceFlowLpm!);
    this.tubingForm.reset({ material: '', internalDiameterMm: null, resistanceBarPerMeter: null,
      referenceFlowLpm: null });
  }

  protected balance(lineId: string): void {
    const input = this.balanceInput();
    if (input) {
      this.store.balance(lineId, input);
    }
  }

  /** Aplicar é ato explícito: o sistema calcula, quem monta a linha é a pessoa. */
  protected applyRevision(lineId: string): void {
    const input = this.balanceInput();
    const v = this.balanceForm.getRawValue();
    if (!input || !v.appliedLengthMeters) {
      return;
    }
    if (window.confirm(`Registrar a montagem de ${v.appliedLengthMeters} m nesta linha?`
        + ' Uma revisão nova é criada e a anterior é preservada.')) {
      this.store.applyRevision(lineId, {
        ...input,
        appliedLengthMeters: v.appliedLengthMeters,
        note: v.note.trim() || null,
      });
    }
  }

  private balanceInput(): BalanceInput | null {
    const v = this.balanceForm.getRawValue();
    if (!v.resistanceId || v.targetCo2Volumes === null || v.servingTempC === null
        || v.elevationMeters === null || v.residualPressureBar === null || v.targetFlowLpm === null) {
      return null;
    }
    return {
      targetCo2Volumes: v.targetCo2Volumes,
      servingTempC: v.servingTempC,
      elevationMeters: v.elevationMeters,
      residualPressureBar: v.residualPressureBar,
      targetFlowLpm: v.targetFlowLpm,
      resistanceId: v.resistanceId,
    };
  }

  protected equipmentName(equipmentId: string): string {
    const equipment = this.store.equipment().find(e => e.id === equipmentId);
    return equipment ? `${equipment.code} — ${equipment.name}` : '—';
  }

  ngOnInit(): void {
    this.store.load();
    this.store.loadReferences();
    this.store.loadServiceLines();
  }

  protected registerCylinder(): void {
    if (this.cylinderForm.invalid) {
      return;
    }
    const v = this.cylinderForm.getRawValue();
    this.store.registerCylinder(
      {
        code: v.code,
        gasType: v.gasType,
        capacityKg: v.capacityKg!,
        tareKg: v.tareKg!,
        contentKg: v.contentKg!,
        requalificationDueOn: v.requalificationDueOn,
        location: v.location,
      },
      () => this.cylinderForm.reset({ code: '', gasType: 'CO2', capacityKg: null, tareKg: null,
        contentKg: null, requalificationDueOn: '', location: '' }),
    );
  }

  protected connect(): void {
    if (this.connectForm.invalid) {
      return;
    }
    const v = this.connectForm.getRawValue();
    this.store.connect(
      {
        cylinderId: v.cylinderId,
        regulatorId: v.regulatorId,
        manifoldId: v.manifoldId || null,
        pointOfUseEquipmentId: v.pointOfUseEquipmentId,
        workingPressureBar: v.workingPressureBar!,
      },
      () => this.connectForm.reset({ cylinderId: '', regulatorId: '', manifoldId: '',
        pointOfUseEquipmentId: '', workingPressureBar: null }),
    );
  }

  /** Bloquear exige motivo — o domínio recusa sem ele, então pedimos aqui. */
  protected block(cylinder: GasCylinder): void {
    const reason = window.prompt(`Motivo do bloqueio do cilindro ${cylinder.code}:`);
    if (reason && reason.trim()) {
      this.store.setBlock(cylinder.id, true, reason.trim());
    }
  }

  protected unblock(cylinder: GasCylinder): void {
    this.store.setBlock(cylinder.id, false, null);
  }

  protected requalify(cylinder: GasCylinder): void {
    const dueOn = window.prompt(`Novo vencimento da requalificação de ${cylinder.code} (AAAA-MM-DD):`);
    if (dueOn && dueOn.trim()) {
      this.store.requalify(cylinder.id, dueOn.trim());
    }
  }

  protected refill(cylinder: GasCylinder): void {
    const contentKg = window.prompt(`Massa aferida após a recarga de ${cylinder.code} (kg):`);
    const value = Number(contentKg);
    if (contentKg && !Number.isNaN(value)) {
      this.store.refill(cylinder.id, value);
    }
  }

  /** O teste de vazamento é a evidência que libera a linha; reprovar exige observação. */
  protected leakTest(connection: GasConnection, passed: boolean): void {
    const method = window.prompt('Método do teste de vazamento:', 'espuma + queda de pressão');
    if (!method || !method.trim()) {
      return;
    }
    const drop = Number(window.prompt('Queda de pressão observada (bar):', passed ? '0' : '0.4'));
    if (Number.isNaN(drop)) {
      return;
    }
    let note: string | null = null;
    if (!passed) {
      note = window.prompt('Observação da reprovação (obrigatória):');
      if (!note || !note.trim()) {
        return;
      }
    }
    this.store.leakTest(connection.id, passed, method.trim(), drop, note);
  }

  protected pressure(connection: GasConnection): void {
    const bar = Number(window.prompt(`Pressão medida na linha (bar, teto ${connection.networkMaxPressureBar}):`));
    if (!Number.isNaN(bar) && bar > 0) {
      this.store.pressure(connection.id, bar, null);
    }
  }

  protected consumption(connection: GasConnection): void {
    const kg = Number(window.prompt('Consumo de gás (kg):'));
    if (!Number.isNaN(kg) && kg > 0) {
      this.store.consumption(connection.id, kg, null);
    }
  }

  protected disconnect(connection: GasConnection): void {
    const reason = window.prompt('Motivo da desconexão:');
    if (reason && reason.trim()) {
      this.store.disconnect(connection.id, reason.trim());
    }
  }

  protected cylinderCode(cylinderId: string): string {
    return this.store.cylinders().find(c => c.id === cylinderId)?.code ?? '—';
  }

  protected componentCode(componentId: string | null): string {
    if (!componentId) {
      return '—';
    }
    return this.store.components().find(c => c.id === componentId)?.code ?? '—';
  }

  protected equipmentCode(equipmentId: string): string {
    return this.store.equipment().find(e => e.id === equipmentId)?.code ?? '—';
  }
}
