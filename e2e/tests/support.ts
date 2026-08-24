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
 * A segunda pessoa, criada pelo mesmo bootstrap do perfil `local`.
 *
 * <p>Existe para os fluxos que exigem duas: a carga é planejada por alguém e **liberada por outro**
 * (LOG-001), e com um usuário só a jornada termina na recusa — que é a metade menos interessante, porque
 * o que ninguém conseguia exercitar era o caminho depois dela.
 */
export const CONFERENTE = {
  email: process.env.E2E_CHECKER_EMAIL || 'conferente@brassia.local',
  password: process.env.E2E_CHECKER_PASSWORD || 'conferente-local-123',
};

/**
 * A pessoa de **pouca alçada**, criada pelo mesmo bootstrap do perfil `local`.
 *
 * <p>Admin e conferente têm as mesmas permissões — eles existem para provar *pessoas diferentes*. Esta
 * existe para provar *permissões diferentes*: ela lê lote e custo, e não fecha custo. Sem ela, a recusa
 * por permissão só podia ser exercitada pela API, e nenhum teste a via chegar a quem opera.
 */
export const OPERADOR = {
  email: process.env.E2E_OPERATOR_EMAIL || 'operador@brassia.local',
  password: process.env.E2E_OPERATOR_PASSWORD || 'operador-local-123',
};

/**
 * A pessoa da **outra cervejaria**, criada pelo mesmo bootstrap do perfil `local`.
 *
 * <p>Mesma alçada do admin, casa diferente: o que se prova com ela é que a **cervejaria** separa, e não a
 * permissão. Se ela também tivesse pouca alçada, levaria 403 por permissão e o teste de isolamento
 * passaria sem nunca ter exercitado isolamento nenhum.
 */
export const VIZINHA = {
  email: process.env.E2E_NEIGHBOUR_EMAIL || 'vizinha@brassia.local',
  password: process.env.E2E_NEIGHBOUR_PASSWORD || 'vizinha-local-123',
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

/** POST autenticado pela sessão do navegador, com o token CSRF que o Angular mandaria. */
export async function post(page: Page, url: string, body?: unknown): Promise<string> {
  // Sem corpo é diferente de corpo vazio: endpoint que não declara `@RequestBody` recusa `{}` com 400,
  // e a mensagem ("Requisição inválida") não diz que o problema é o corpo que ninguém pediu.
  const response = await page.request.post(url, {
    ...(body === undefined ? {} : { data: body }),
    headers: await csrfHeaders(page),
  });
  expect(response.ok(), `${url} respondeu ${response.status()}: ${await response.text()}`).toBe(true);
  const text = await response.text();
  return text ? (JSON.parse(text).id ?? '') : '';
}

export async function put(page: Page, url: string, body: unknown): Promise<void> {
  const response = await page.request.put(url, { data: body, headers: await csrfHeaders(page) });
  expect(response.ok(), `${url} respondeu ${response.status()}: ${await response.text()}`).toBe(true);
}

export async function get(page: Page, url: string) {
  const response = await page.request.get(url);
  expect(response.ok(), `${url} respondeu ${response.status()}`).toBe(true);
  return response.json();
}

/** Faz login e espera o shell da aplicação aparecer. */
export async function login(page: Page, quem = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(quem.email);
  await page.getByLabel('Senha').fill(quem.password);
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

/**
 * Um lote de produto acabado pronto para vender: envasado, com validade apurada e liberado.
 *
 * <p>São os três atos que a `SAL-001-B` exige de um lote vendável, e nenhum deles é dispensável: encher
 * precede liberar, e quem cobra a assinatura da qualidade é a saída da casa.
 */
export async function seedSellableLot(
  page: Page,
  sfx: string,
): Promise<{ recipeId: string; containerId: string; lotId: string }> {
  const start = new Date(Date.now() + 60 * 60 * 1000);
  const end = new Date(Date.now() + 5 * 60 * 60 * 1000);

  const equip = (nome: string) =>
    post(page, '/api/v1/equipment', {
      code: `EQ-${nome}${sfx}`,
      name: nome,
      capacityLiters: 500,
      deadSpaceLiters: 20,
      mashEfficiencyPercent: 72,
      boilOffLitersPerHour: 8,
    });
  const kettle = await equip('K');
  const fermenter = await equip('F');
  const line = await equip('L');

  const ing = (type: string, unit: string, attributes: Record<string, string>) =>
    post(page, '/api/v1/catalog/ingredients', {
      type,
      code: `${type.charAt(0)}-${sfx}`,
      name: `${type} ${sfx}`,
      useUnit: unit,
      purchaseUnit: unit,
      attributes,
    });
  const malt = await ing('MALT', 'KG', { potentialSg: '1.037', colorEbc: '4' });
  const hop = await ing('HOP', 'G', { alphaAcid: '12' });
  const yeast = await ing('YEAST', 'UNIT', { attenuation: '78' });
  const can = await ing('PACKAGING', 'UNIT', { volumeMl: '355', material: 'lata' });

  await receive(page, malt, 500, 'KG', sfx);
  await receive(page, hop, 5000, 'G', sfx);
  await receive(page, yeast, 50, 'UNIT', sfx);
  await receive(page, can, 2000, 'UNIT', sfx);

  const recipeId = await post(page, '/api/v1/recipes', {
    name: `Comercial ${sfx}`,
    equipmentId: kettle,
    batchVolumeLiters: 400,
    boilTimeMinutes: 60,
    targetIbu: 30,
    items: [
      { ingredientId: malt, stage: 'MASH', quantity: 20, unit: 'KG' },
      { ingredientId: hop, stage: 'BOIL', quantity: 60, unit: 'G', timingMinutes: 60 },
      { ingredientId: yeast, stage: 'FERMENTATION', quantity: 1, unit: 'UNIT' },
    ],
  });
  await post(page, `/api/v1/recipes/${recipeId}/metrics`, {});
  await post(page, `/api/v1/recipes/${recipeId}/publish`, {});

  const order = await post(page, '/api/v1/brew-orders', { recipeId, volumeLiters: 400 });
  await post(page, `/api/v1/brew-orders/${order}/release`, {
    assignedUserId: '00000000-0000-0000-0000-000000000001',
  });
  await post(page, `/api/v1/brew-orders/${order}/reserve-stock`, {});
  await post(page, `/api/v1/brew-orders/${order}/start`, {});

  const batches = await get(page, '/api/v1/production/batches?page=0&size=100');
  const batch = batches.content.find((b: { orderId: string }) => b.orderId === order);
  expect(batch, 'a ordem iniciada deve ter gerado um lote').toBeTruthy();

  await post(page, `/api/v1/production/batches/${batch.id}/transfer`, {
    destinationEquipmentId: fermenter,
    volumeLiters: 390,
    ogSg: 1.052,
    lossesLiters: 8,
  });

  await releaseCleaning(page, line, sfx);
  const plan = await post(page, '/api/v1/packaging/plans', {
    code: `ENV-${sfx}`,
    batchId: batch.id,
    containerId: can,
    plannedUnits: 400,
    lineEquipmentId: line,
    plannedStart: start.toISOString(),
    plannedEnd: end.toISOString(),
  });
  for (const item of ['CONTAINER_INSPECTED', 'SEAL_TEST', 'GAS_SUPPLY']) {
    await post(page, `/api/v1/packaging/plans/${plan}/checklist`, { item });
  }
  await post(page, `/api/v1/packaging/plans/${plan}/reserve`, {});
  await post(page, `/api/v1/packaging/plans/${plan}/execution`, {
    inputVolumeLiters: 145,
    producedUnits: 390,
    rejectedUnits: 5,
  });

  // Frescor e validade: o sistema se recusa a inventar prazo, então ou há política que sustente a
  // recomendação, ou alguém assume por escrito. Sem validade, o lote não é vendável.
  await put(page, `/api/v1/packaging/plans/${plan}/freshness`, {
    dissolvedOxygenPpb: 30,
    totalPackageOxygenPpb: 50,
    purgeMethod: 'CO2 counter-pressure',
    purgeVerified: true,
    sealCheckMethod: 'torque',
    sealCheckPassed: true,
  });
  await post(page, `/api/v1/packaging/plans/${plan}/freshness/override`, {
    shelfLifeDays: 180,
    reason: 'validade definida na jornada comercial',
  });

  const lots = await get(page, `/api/v1/packaging/finished-lots?batchId=${batch.id}`);
  expect(lots.length, 'o envase executado deve ter gerado lote de produto acabado').toBe(1);
  await post(page, `/api/v1/packaging/finished-lots/${lots[0].id}/release`, {});

  return { recipeId, containerId: can, lotId: lots[0].id };
}

async function receive(
  page: Page,
  ingredientId: string,
  quantity: number,
  unit: string,
  sfx: string,
): Promise<void> {
  const supplier = await post(page, '/api/v1/suppliers', {
    name: `Fornecedor ${sfx}-${ingredientId.slice(0, 4)}`,
    code: `S-${sfx}-${ingredientId.slice(0, 4)}`,
  });
  await post(page, '/api/v1/inventory/lots', {
    ingredientId,
    supplierId: supplier,
    quantity,
    unit,
    unitCost: 1.5,
    supplierLotCode: `F-${sfx}`,
    expiryDate: '2028-01-01',
    inspection: 'APPROVED',
  });
}

/** A linha só recebe envase com ciclo de limpeza liberado — é a evidência, não um "ok" digitado. */
async function releaseCleaning(page: Page, equipmentId: string, sfx: string): Promise<void> {
  const code = `CIP-${sfx}`;
  const procedure = await post(page, '/api/v1/sanitation/procedures', {
    code,
    name: 'CIP linha',
    steps: [
      {
        sequence: 1,
        method: 'CIP',
        product: 'soda',
        concentrationMinPct: 1.0,
        concentrationMaxPct: 3.0,
        tempMinC: 50,
        tempMaxC: 70,
        timeMinutes: 15,
        evidenceRequired: false,
      },
    ],
  });
  await post(page, `/api/v1/sanitation/procedures/${procedure}/publish`, {});
  const cycle = await post(page, '/api/v1/sanitation/cycles', { procedureCode: code, equipmentId });
  await post(page, `/api/v1/sanitation/cycles/${cycle}/steps`, {
    sequence: 1,
    measuredConcentrationPct: 2.0,
    measuredTempC: 60,
    measuredTimeMinutes: 20,
  });
  await post(page, `/api/v1/sanitation/cycles/${cycle}/complete`, {});
  await post(page, `/api/v1/sanitation/cycles/${cycle}/verification`, {
    rinseOk: true,
    visualOk: true,
    atpRlu: 40,
    atpThreshold: 100,
    microOk: true,
  });
  await post(page, `/api/v1/sanitation/cycles/${cycle}/release`, {});
}

export function ontem(): string {
  return new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}
