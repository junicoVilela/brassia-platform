import { type Page } from '@playwright/test';
import { expect, login, test } from './support';

/**
 * Jornada de negócio de ponta a ponta: do insumo recebido ao simulado de recall.
 *
 * <p>Fecha o item de E2E do DoD que ficou aberto no aceite da sprint 11: o harness cobria navegação,
 * sessão e integração frontend↔API, e não um fluxo de negócio completo. Este cobre — e o simulado de
 * recall é o lugar natural para isso, porque ele é, por definição, um fluxo com começo, meio e prova
 * de que funcionou.
 *
 * <p>O preparo do cenário vai pela API (mesma sessão do navegador, mesma stack, mesmo banco), porque
 * clicar em quinze telas para chegar ao lote envasado testaria formulários que já têm teste próprio.
 * O que a jornada verifica na interface é o que só existe no fim da cadeia: a genealogia mostrando o
 * caminho inteiro e o relatório do simulado medindo a cobertura.
 */
test.describe('jornada de negócio', () => {
  test('do insumo ao simulado de recall, com a cadeia inteira visível na tela', async ({ page }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);
    // Datas relativas ao agora: a validade do CIP é parâmetro da casa (PRM-001), e um envase
    // planejado para daqui a duas semanas venceria a liberação de limpeza feita hoje.
    const start = new Date(Date.now() + 60 * 60 * 1000);
    const end = new Date(Date.now() + 5 * 60 * 60 * 1000);
    const today = new Date().toISOString().slice(0, 10);

    // --- 1. Cadastro: equipamento e insumos ---
    const kettle = await post(page, '/api/v1/equipment', {
      code: `EQ-K${sfx}`,
      name: 'Panela',
      capacityLiters: 500,
      deadSpaceLiters: 20,
      mashEfficiencyPercent: 72,
      boilOffLitersPerHour: 8,
    });
    const fermenter = await post(page, '/api/v1/equipment', {
      code: `EQ-F${sfx}`,
      name: 'Fermentador',
      capacityLiters: 500,
      deadSpaceLiters: 20,
      mashEfficiencyPercent: 72,
      boilOffLitersPerHour: 8,
    });
    const line = await post(page, '/api/v1/equipment', {
      code: `EQ-L${sfx}`,
      name: 'Linha',
      capacityLiters: 500,
      deadSpaceLiters: 20,
      mashEfficiencyPercent: 72,
      boilOffLitersPerHour: 8,
    });

    const malt = await ingredient(page, 'MALT', 'KG', { potentialSg: '1.037', colorEbc: '4' }, sfx);
    const hop = await ingredient(page, 'HOP', 'G', { alphaAcid: '12' }, sfx);
    const yeast = await ingredient(page, 'YEAST', 'UNIT', { attenuation: '78' }, sfx);
    const can = await ingredient(page, 'PACKAGING', 'UNIT', { volumeMl: '355', material: 'lata' }, sfx);

    // --- 2. Recebimento de insumo: é daqui que a rastreabilidade parte ---
    await receive(page, malt, 500, 'KG', sfx);
    await receive(page, hop, 5000, 'G', sfx);
    await receive(page, yeast, 50, 'UNIT', sfx);
    await receive(page, can, 2000, 'UNIT', sfx);

    // --- 3. Receita publicada ---
    const recipe = await post(page, '/api/v1/recipes', {
      name: `Jornada ${sfx}`,
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
    await post(page, `/api/v1/recipes/${recipe}/metrics`, {});
    await post(page, `/api/v1/recipes/${recipe}/publish`, {});

    // --- 4. Ordem, reserva de insumo e início: o lote nasce aqui ---
    const order = await post(page, '/api/v1/brew-orders', { recipeId: recipe, volumeLiters: 400 });
    await post(page, `/api/v1/brew-orders/${order}/release`, {
      assignedUserId: '00000000-0000-0000-0000-000000000001',
    });
    await post(page, `/api/v1/brew-orders/${order}/reserve-stock`, {});
    await post(page, `/api/v1/brew-orders/${order}/start`, {});

    // A listagem é paginada (REL-002): o array vem em `content`.
    const batches = await get(page, '/api/v1/production/batches');
    const batch = batches.content.find((b: { orderId: string }) => b.orderId === order);
    expect(batch, 'a ordem iniciada deve ter gerado um lote').toBeTruthy();

    await post(page, `/api/v1/production/batches/${batch.id}/transfer`, {
      destinationEquipmentId: fermenter,
      volumeLiters: 390,
      ogSg: 1.052,
      lossesLiters: 8,
    });

    // --- 5. Linha limpa, plano de envase e execução: nasce o lote de produto acabado ---
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

    const lots = await get(page, `/api/v1/packaging/finished-lots?batchId=${batch.id}`);
    expect(lots.length, 'o envase executado deve ter gerado lote de produto acabado').toBe(1);
    const finishedLot = lots[0];

    // --- 6. Expedição: a metade de fora da fábrica ---
    await post(page, '/api/v1/packaging/shipments', {
      finishedLotId: finishedLot.id,
      destination: `Distribuidora ${sfx}`,
      contact: '(11) 99999-0000',
      units: 200,
      shippedOn: today,
    });

    // --- 7. A genealogia mostra a cadeia inteira, do insumo à expedição ---
    await page.goto(`/traceability/genealogy?nodeType=BATCH&nodeId=${batch.id}&direction=BOTH`);
    await expect(page.getByRole('heading', { name: 'Genealogia' })).toBeVisible();
    await expect(page.getByText('Produto acabado', { exact: false }).first()).toBeVisible();
    await expect(page.getByText(`Distribuidora ${sfx}`, { exact: false }).first()).toBeVisible();

    // --- 8. Produto acabado: 190 das 390 unidades ainda sem destino ---
    await page.goto('/packaging/finished-lots');
    await expect(page.getByText(finishedLot.code, { exact: false }).first()).toBeVisible();
    await expect(page.getByText('190 un sem destino', { exact: false }).first()).toBeVisible();

    // --- 9. Simulado: o exercício mede o que saiu, não o que ficou na fábrica ---
    const drill = await post(page, '/api/v1/traceability/recall-drills', {
      nodeType: 'BATCH',
      nodeId: batch.id,
      note: `jornada ${sfx}`,
    });
    await page.goto('/traceability/recall-drills');
    await expect(page.getByRole('heading', { name: 'Simulados de recall' })).toBeVisible();

    const report = await get(page, `/api/v1/traceability/recall-drills/${drill}`);
    // 200 unidades saíram; as 190 que ficaram não são objeto de recall.
    expect(report.unitsInScope).toBe(200);
    expect(report.destinationsReached).toBe(1);

    await post(page, `/api/v1/traceability/recall-drills/${drill}/finish`, {
      unitsLocated: 150,
      summary: 'uma ligação; 50 latas já vendidas ao consumidor',
      correctiveActions: 'registrar expedição das 190 unidades restantes',
    });

    // --- 10. O relatório mede: 75% localizado, e o que fazer para melhorar ---
    await page.reload();
    await expect(page.getByText('75%', { exact: false }).first()).toBeVisible();

    const finished = await get(page, `/api/v1/traceability/recall-drills/${drill}`);
    expect(finished.drill.status).toBe('FINISHED');
    expect(finished.drill.locatedPercent).toBe(75);
    // O simulado não recolheu nada: nenhum recall de verdade foi aberto no caminho.
    const recalls = await get(page, '/api/v1/traceability/recalls');
    expect(recalls.length).toBe(0);
  });
});

/** POST autenticado pela sessão do navegador, com o token CSRF que o Angular mandaria. */
async function post(page: Page, url: string, body: unknown): Promise<string> {
  const response = await page.request.post(url, { data: body, headers: await csrf(page) });
  expect(response.ok(), `${url} respondeu ${response.status()}: ${await response.text()}`).toBe(true);
  const text = await response.text();
  return text ? (JSON.parse(text).id ?? '') : '';
}

async function get(page: Page, url: string) {
  const response = await page.request.get(url);
  expect(response.ok(), `${url} respondeu ${response.status()}`).toBe(true);
  return response.json();
}

async function csrf(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const token = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token.value } : {};
}

async function ingredient(
  page: Page,
  type: string,
  unit: string,
  attributes: Record<string, string>,
  sfx: string,
): Promise<string> {
  return post(page, '/api/v1/catalog/ingredients', {
    type,
    code: `${type.charAt(0)}-${sfx}`,
    name: `${type} ${sfx}`,
    useUnit: unit,
    purchaseUnit: unit,
    attributes,
  });
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
