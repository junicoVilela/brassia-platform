import { Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from '../app.routes';
import { MENU, MenuNode } from './menu';

function flatten(nodes: readonly MenuNode[]): MenuNode[] {
  return nodes.flatMap(node => [node, ...flatten(node.items ?? [])]);
}

const all = flatten(MENU);
const links = all.filter(node => node.route);

/** Rotas declaradas dentro do shell autenticado, com a permissão do guarda. */
const declared = new Map<string, string | undefined>(
  ((routes.find(route => route.path === '')?.children ?? []) as Routes).map(route => [
    `/${route.path}`,
    route.data?.['permission'] as string | undefined,
  ]),
);

describe('MENU', () => {
  it('não repete id nem rota', () => {
    const ids = all.map(node => node.id);
    const paths = links.map(node => node.route);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it('agrupa todo item que não seja atalho de topo', () => {
    const topLevelLinks = MENU.filter(node => node.route).map(node => node.id);
    expect(topLevelLinks).toEqual(['scan', 'settings']);
    expect(MENU.filter(node => node.items).length).toBeGreaterThan(1);
  });

  it('todo grupo tem filhos e nenhum filho tem sub-filho', () => {
    for (const group of MENU.filter(node => node.items)) {
      expect(group.items?.length, group.id).toBeGreaterThan(0);
      expect(group.route, group.id).toBeUndefined();
      for (const child of group.items ?? []) {
        expect(child.route, child.id).toBeTruthy();
        expect(child.items, child.id).toBeUndefined();
      }
    }
  });

  it('aponta apenas para rotas existentes', () => {
    for (const link of links) {
      const known =
        declared.has(link.route!) ||
        [...declared.keys()].some(path => link.route!.startsWith(`${path}/`));
      expect(known, `rota desconhecida: ${link.route}`).toBe(true);
    }
  });

  /**
   * A permissão do menu tem de ser a mesma do `permissionGuard`: se divergir, o
   * item aparece e a tela responde 403 — ou some para quem tem acesso.
   */
  it('declara a mesma permissão que o guarda da rota', () => {
    for (const link of links) {
      if (!declared.has(link.route!)) {
        continue;
      }
      expect(link.permission ?? null, `permissão divergente em ${link.route}`).toBe(
        declared.get(link.route!) ?? null,
      );
    }
  });
});
