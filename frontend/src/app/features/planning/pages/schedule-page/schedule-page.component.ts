import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ScheduleEntry } from '../../domain/schedule.model';
import { PlanningStore } from '../../data-access/planning.store';

interface DayGroup {
  day: string;
  items: ScheduleEntry[];
}

@Component({
  selector: 'app-schedule-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [PlanningStore],
  templateUrl: './schedule-page.component.html',
})
export class SchedulePageComponent implements OnInit {
  protected readonly store = inject(PlanningStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    recipeId: ['', Validators.required],
    equipmentId: ['', Validators.required],
    assignedUserId: ['', Validators.required],
    plannedVolumeLiters: [0, [Validators.required, Validators.min(0.001)]],
    scheduledStart: ['', Validators.required],
    scheduledEnd: ['', Validators.required],
  });

  /** Entradas agrupadas por dia, ordenadas — a visão de calendário/agenda. */
  protected readonly days = computed<DayGroup[]>(() => {
    const groups = new Map<string, ScheduleEntry[]>();
    for (const entry of [...this.store.entries()].sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart))) {
      const day = entry.scheduledStart.slice(0, 10);
      (groups.get(day) ?? groups.set(day, []).get(day)!).push(entry);
    }
    return [...groups.entries()].map(([day, items]) => ({ day, items }));
  });

  ngOnInit(): void {
    this.store.load();
  }

  /** Só permite simular quando equipamento e janela estão preenchidos. */
  protected canSimulate(): boolean {
    const v = this.form.getRawValue();
    return !!v.equipmentId && !!v.scheduledStart && !!v.scheduledEnd;
  }

  protected simulate(): void {
    if (!this.canSimulate()) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.simulate({
      equipmentId: v.equipmentId,
      scheduledStart: toIso(v.scheduledStart),
      scheduledEnd: toIso(v.scheduledEnd),
    });
  }

  protected schedule(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.create({
      recipeId: v.recipeId,
      equipmentId: v.equipmentId,
      assignedUserId: v.assignedUserId,
      plannedVolumeLiters: v.plannedVolumeLiters,
      scheduledStart: toIso(v.scheduledStart),
      scheduledEnd: toIso(v.scheduledEnd),
    }, () => this.form.reset({ plannedVolumeLiters: 0 }));
  }
}

/** Converte um valor de `datetime-local` (hora local) para ISO-8601 (Instant). */
function toIso(value: string): string {
  return value ? new Date(value).toISOString() : '';
}
