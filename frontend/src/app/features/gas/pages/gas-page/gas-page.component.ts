import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { GasStore } from '../../data-access/gas.store';
import {
  CONNECTION_STATUS_LABELS,
  CYLINDER_STATUS_LABELS,
  COMPONENT_KIND_LABELS,
  GasConnection,
  GasCylinder,
  GasType,
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

  ngOnInit(): void {
    this.store.load();
    this.store.loadReferences();
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
