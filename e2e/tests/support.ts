import { expect, test as base, type Page } from '@playwright/test';

/**
 * Admin criado pelo bootstrap do perfil `local` (ver `application-local.yml`).
 * Credenciais descartáveis, só de desenvolvimento — nunca valem fora dele.
 */
export const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL || 'admin@brassia.local',
  password: process.env.E2E_ADMIN_PASSWORD || 'admin-local-123',
};

/**
 * Mensagens de erro do console que não indicam defeito.
 *
 * 1. `Failed to load resource` é ruído de rede do navegador: 4xx esperado é
 *    comportamento da aplicação — senha errada devolve 401, cervejaria sem regra
 *    de rótulo devolve 400 — e cada teste verifica o que lhe importa com
 *    `waitForResponse`.
 * 2. Assets do tema Fila são pagos e não versionados (ver
 *    `frontend/THEME_SETUP.md`), então na CI eles não existem e o `ng serve`
 *    devolve o `index.html` no lugar. A aplicação funciona sem o visual do tema,
 *    que é justamente o previsto no THEME_SETUP — a jornada não depende dele.
 */
function ruidoEsperado(texto: string): boolean {
  return texto.startsWith('Failed to load resource') || texto.includes('/assets/fila/');
}

/**
 * `test` estendido: erro no console do navegador ou exceção não tratada reprova
 * o teste.
 *
 * Existe porque erro de render não aparece em asserção de conteúdo. Um `@for`
 * sobre valor não-iterável — endpoint paginado tratado como array — quebra a
 * lista em silêncio: a tela ainda mostra o estado vazio e um `toBeVisible` passa
 * sobre uma tela quebrada. Foi assim que o defeito das telas de envase e gases
 * atravessou os testes de unidade.
 */
export const test = base.extend<Record<string, never>>({
  page: async ({ page }, use) => {
    const erros: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error' && !ruidoEsperado(msg.text())) {
        erros.push(msg.text());
      }
    });
    page.on('pageerror', err => erros.push(String(err)));

    await use(page);

    expect(erros, `erros no console do navegador:\n${erros.join('\n')}`).toEqual([]);
  },
});

export { expect };

/**
 * Cabeçalho CSRF que o Angular mandaria.
 *
 * <p>Chamada direta pela `page.request` não passa pelo `HttpClient`, então o interceptor que copia o
 * cookie `XSRF-TOKEN` para o cabeçalho não roda — e o servidor responde 403. É um 403 de proteção CSRF,
 * não de permissão, e a confusão entre os dois custa tempo: parece que falta alçada quando falta
 * cabeçalho.
 */
export async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const token = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token.value } : {};
}

/** Faz login e espera o shell da aplicação aparecer. */
export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(ADMIN.email);
  await page.getByLabel('Senha').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.locator('#sidebar-area')).toBeVisible();
}

/**
 * Cria um lote de produção pronto para uso, pela API.
 *
 * <p>Existe para acabar com os `test.skip` que pulavam quando o ambiente não tinha lote. Um teste que
 * pula é pior que um teste ausente: ele aparece verde no relatório e não exercita nada — e quem lê a
 * suíte conclui que a jornada está coberta.
 *
 * <p>Semear custa alguns segundos e vale: o mesmo teste passa a valer em ambiente limpo, em CI e na
 * máquina de quem está depurando, sem depender de dado deixado por outra execução.
 */
export async function seedBatch(page: Page): Promise<{ recipeId: string; batchId: string }> {
  const sfx = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const headers = await csrfHeaders(page);

  const create = async (url: string, body: unknown): Promise<string> => {
    const response = await page.request.post(url, { headers, data: body });
    if (!response.ok()) {
      throw new Error(`falha ao semear em ${url}: HTTP ${response.status()} — ${await response.text()}`);
    }
    const text = await response.text();
    return text ? (JSON.parse(text).id ?? '') : '';
  };

  const equipmentId = await create('/api/v1/equipment', {
    code: `SEED-${sfx}`, name: 'Fervedor', capacityLiters: 500, deadSpaceLiters: 20,
    mashEfficiencyPercent: 72, boilOffLitersPerHour: 8,
  });
  const ing = (type: string, unit: string, attributes: unknown) =>
    create('/api/v1/catalog/ingredients', {
      type, code: `${type}-${sfx}`, name: `${type} ${sfx}`,
      useUnit: unit, purchaseUnit: unit, attributes,
    });
  const malt = await ing('MALT', 'KG', { potentialSg: '1.037', colorEbc: '4' });
  const hop = await ing('HOP', 'G', { alphaAcid: '12' });
  const yeast = await ing('YEAST', 'UNIT', { attenuation: '78' });

  const recipeId = await create('/api/v1/recipes', {
    name: `Semeada ${sfx}`, equipmentId, batchVolumeLiters: 400, boilTimeMinutes: 60, targetIbu: 30,
    items: [
      { ingredientId: malt, stage: 'MASH', quantity: 20, unit: 'KG' },
      { ingredientId: hop, stage: 'BOIL', quantity: 60, unit: 'G', timingMinutes: 60 },
      { ingredientId: yeast, stage: 'FERMENTATION', quantity: 1, unit: 'UNIT' },
    ],
  });
  await create(`/api/v1/recipes/${recipeId}/metrics`, {});
  await create(`/api/v1/recipes/${recipeId}/publish`, {});

  const orderId = await create('/api/v1/brew-orders', { recipeId, volumeLiters: 400 });
  await create(`/api/v1/brew-orders/${orderId}/release`, {
    assignedUserId: '00000000-0000-0000-0000-000000000001',
  });
  await create(`/api/v1/brew-orders/${orderId}/start`, {});

  // A listagem é paginada (REL-002): o array vem em `content`.
  const listing = await page.request.get('/api/v1/production/batches?page=0&size=100');
  const batches = JSON.parse(await listing.text()).content as { id: string; orderId: string }[];
  const batch = batches.find(b => b.orderId === orderId);
  if (!batch) {
    throw new Error('lote semeado não apareceu na listagem');
  }
  return { recipeId, batchId: batch.id };
}
