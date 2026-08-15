import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CrmStore } from '../../data-access/crm.store';
import {
  BASIS_LABELS,
  Contact,
  ContactPurpose,
  Customer,
  PURPOSE_LABELS,
  PurposeState,
} from '../../domain/customer.model';

/**
 * Clientes, contatos e consentimentos (CRM-001).
 *
 * <p>A responsabilidade da tela que não é cadastrar: <strong>deixar visível de onde vem o direito de
 * falar com cada pessoa</strong>. "Avisos da venda" aparece permitido sem ninguém ter consentido, e um
 * operador que não entenda por quê vai concluir que o sistema está errado — ou pior, vai achar que pode
 * mandar oferta pelo mesmo motivo. Por isso cada finalidade mostra a base legal ao lado, e só as que
 * dependem de consentimento têm botão.
 *
 * <p><strong>Anonimizar pede confirmação porque é irreversível</strong>, e a confirmação diz o que
 * sobrevive: a linha e o histórico de decisões ficam, a pessoa some. Um "tem certeza?" genérico não
 * informa nada a quem está prestes a apagar um dado que não volta.
 */
@Component({
  selector: 'app-customers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [CrmStore],
  templateUrl: './customers-page.component.html',
})
export class CustomersPageComponent implements OnInit {
  protected readonly store = inject(CrmStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly purposeLabels = PURPOSE_LABELS;
  protected readonly basisLabels = BASIS_LABELS;

  protected readonly showCustomerForm = signal(false);
  protected readonly showContactForm = signal(false);
  protected readonly consentFor = signal<{ contact: Contact; purpose: ContactPurpose } | null>(null);
  protected readonly confirmAnonymize = signal<Contact | null>(null);

  protected readonly canManage = this.auth.hasPermission('crm.customer.manage');
  protected readonly canAnonymize = this.auth.hasPermission('crm.contact.anonymize');
  protected readonly canSetRetention = this.auth.hasPermission('crm.retention.manage');

  protected readonly customerForm = this.fb.nonNullable.group({
    legalName: ['', [Validators.required, Validators.maxLength(200)]],
    tradeName: ['', Validators.maxLength(200)],
    taxId: ['', Validators.maxLength(40)],
  });

  protected readonly contactForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: ['', Validators.maxLength(40)],
    role: ['', Validators.maxLength(80)],
  });

  /**
   * O instante da decisão é editável, e o padrão é agora.
   *
   * <p>Quem registra pelo telefone uma decisão tomada na semana passada precisa poder dizer isso. Se o
   * campo não existisse, o livro passaria a contar a história da digitação em vez da do mundo — e é
   * justamente o livro que responde "ela aceitava quando mandamos aquilo?".
   */
  protected readonly consentForm = this.fb.nonNullable.group({
    decidedAt: [this.nowLocal(), Validators.required],
    source: ['', [Validators.required, Validators.maxLength(200)]],
  });

  protected readonly retentionForm = this.fb.nonNullable.group({
    days: [365, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected select(customer: Customer): void {
    this.store.select(customer);
    this.showContactForm.set(false);
  }

  protected submitCustomer(): void {
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }
    const v = this.customerForm.getRawValue();
    this.store.createCustomer(v.legalName, v.tradeName || null, v.taxId || null);
    this.customerForm.reset({ legalName: '', tradeName: '', taxId: '' });
    this.showCustomerForm.set(false);
  }

  protected submitContact(): void {
    const customer = this.store.selected();
    if (!customer || this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }
    const v = this.contactForm.getRawValue();
    this.store.createContact(customer.id, {
      name: v.name,
      email: v.email || null,
      phone: v.phone || null,
      role: v.role || null,
    });
    this.contactForm.reset({ name: '', email: '', phone: '', role: '' });
    this.showContactForm.set(false);
  }

  protected openConsent(contact: Contact, purpose: ContactPurpose): void {
    this.consentForm.reset({ decidedAt: this.nowLocal(), source: '' });
    this.consentFor.set({ contact, purpose });
  }

  protected submitConsent(granted: boolean): void {
    const target = this.consentFor();
    if (!target || this.consentForm.invalid) {
      this.consentForm.markAllAsTouched();
      return;
    }
    const v = this.consentForm.getRawValue();
    // O campo é datetime-local, que devolve hora local sem fuso. Mandar o texto cru gravaria a hora de
    // São Paulo como se fosse UTC, e a decisão apareceria três horas adiantada no histórico.
    this.store.recordConsent(
      target.contact,
      target.purpose,
      granted,
      new Date(v.decidedAt).toISOString(),
      v.source,
    );
    this.consentFor.set(null);
  }

  protected doAnonymize(): void {
    const contact = this.confirmAnonymize();
    if (contact) {
      this.store.anonymize(contact);
      this.confirmAnonymize.set(null);
    }
  }

  protected submitRetention(): void {
    if (this.retentionForm.invalid) {
      this.retentionForm.markAllAsTouched();
      return;
    }
    this.store.saveRetention(this.retentionForm.getRawValue().days);
  }

  /** Só as finalidades que dependem de consentimento ganham botão. */
  protected consentable(purposes: PurposeState[]): PurposeState[] {
    return purposes.filter(p => p.basis === 'CONSENT');
  }

  protected contractual(purposes: PurposeState[]): PurposeState[] {
    return purposes.filter(p => p.basis === 'CONTRACT');
  }

  private nowLocal(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}
