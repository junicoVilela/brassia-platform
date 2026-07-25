import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RecoveryApi } from '../data-access/recovery.api';

type VerifyState = 'loading' | 'success' | 'error' | 'no-token';

@Component({
  selector: 'app-verify-email-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './verify-email-page.component.html',
})
export class VerifyEmailPageComponent implements OnInit {
  private readonly recovery = inject(RecoveryApi);
  private readonly route = inject(ActivatedRoute);

  protected readonly state = signal<VerifyState>('loading');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!token) {
      this.state.set('no-token');
      return;
    }
    this.recovery.confirmEmailVerification(token).subscribe({
      next: () => this.state.set('success'),
      error: () => this.state.set('error'),
    });
  }
}
