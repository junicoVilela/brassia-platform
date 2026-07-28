import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CyclesStore } from '../../data-access/cycles.store';

@Component({
  selector: 'app-cycles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [CyclesStore],
  templateUrl: './cycles-page.component.html',
})
export class CyclesPageComponent implements OnInit {
  protected readonly store = inject(CyclesStore);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly form = this.fb.nonNullable.group({
    procedureCode: ['', Validators.required],
    equipmentId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.store.load();
    if (this.store.canExecute) {
      this.store.loadOptions();
    }
  }

  protected start(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.start(this.form.getRawValue(), id => {
      this.form.reset({ procedureCode: '', equipmentId: '' });
      this.router.navigate(['/sanitation/cycles', id]);
    });
  }

  protected open(id: string): void {
    this.router.navigate(['/sanitation/cycles', id]);
  }
}
