import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../core/auth/auth.service';
import { ShellComponent } from './shell.component';

function authStub(permissions: string[]) {
  return {
    user: signal(null),
    hasPermission: (permission: string) => permissions.includes(permission),
    hasAnyPermission: (list: string[]) => list.some(permission => permissions.includes(permission)),
  };
}

async function render(permissions: string[], url = '/'): Promise<ComponentFixture<ShellComponent>> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [ShellComponent],
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: authStub(permissions) },
    ],
  }).compileComponents();

  await TestBed.inject(Router).navigateByUrl(url);
  const fixture = TestBed.createComponent(ShellComponent);
  fixture.detectChanges();
  return fixture;
}

function labels(fixture: ComponentFixture<ShellComponent>, selector: string): string[] {
  return [...fixture.nativeElement.querySelectorAll(selector)].map((element: Element) =>
    element.textContent!.trim(),
  );
}

describe('ShellComponent — menu agrupado', () => {
  it('mostra os grupos e esconde os itens sem permissão', async () => {
    const fixture = await render([]);
    const groups = labels(fixture, '.menu-inner > .menu-item > .menu-toggle .title');

    // Sem nenhuma permissão restam só os grupos cujos itens são livres.
    expect(groups).toEqual(['Cadastros', 'Receitas e água']);
    expect(labels(fixture, '.menu-sub .title')).not.toContain('Recalls');
    // "Abrir código" é livre; "Configurações" exige permissão de segurança.
    expect(labels(fixture, '.menu-inner > .menu-item > a.menu-link .title')).toEqual(['Abrir código']);
  });

  it('revela o grupo quando a sessão ganha a permissão de um filho', async () => {
    const fixture = await render(['traceability.recall.read']);
    expect(labels(fixture, '.menu-inner > .menu-item > .menu-toggle .title')).toContain('Rastreabilidade');
    expect(labels(fixture, '.menu-sub .title')).toContain('Recalls');
    expect(labels(fixture, '.menu-sub .title')).not.toContain('Quarentenas');
  });

  it('abre sozinho o grupo da rota atual e mantém os outros fechados', async () => {
    const fixture = await render(['production.batch.read'], '/production/batches');
    const open = [...fixture.nativeElement.querySelectorAll('.menu-inner > .menu-item.open')];

    expect(open).toHaveLength(1);
    expect(open[0].querySelector('.menu-toggle .title').textContent.trim()).toBe('Fabricação');
    expect(open[0].querySelector('.menu-toggle').getAttribute('aria-expanded')).toBe('true');
  });

  it('destaca o grupo da rota na sidebar recolhida, onde o submenu não aparece', async () => {
    const fixture = await render(['production.batch.read'], '/production/batches');
    const group = (): HTMLElement =>
      fixture.nativeElement.querySelector('.menu-inner > .menu-item.open > .menu-toggle');

    // Aberta, o próprio filho ativo dá a pista: o grupo não se destaca.
    expect(group().classList).not.toContain('active');

    fixture.nativeElement.querySelector('.header-burger-menu').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.getAttribute('sidebar-data-theme')).toBe('sidebar-hide');
    expect(group().classList).toContain('active');
  });

  it('abre e fecha o grupo no clique', async () => {
    const fixture = await render([]);
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('.menu-inner > .menu-item .menu-toggle');

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.closest('.menu-item')!.classList).toContain('open');

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.closest('.menu-item')!.classList).not.toContain('open');
  });
});
