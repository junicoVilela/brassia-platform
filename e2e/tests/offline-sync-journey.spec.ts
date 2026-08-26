import { type Browser, type Page } from '@playwright/test';
import { CONFERENTE, expect, get, login, post, seedSellableLot, test } from './support';

/**
 * A fila do aplicativo de rua: reenvio não duplica, e conflito espera gente.
 *
 * <p>Fecha a linha do roteiro de homologação que estava provada **só pelo backend**. `SyncIT` prova as
 * duas regras pela API. Nenhuma tinha sido vista numa tela — e a segunda **só existe** se alguém a vir:
 * um conflito que não aparece na tela do escritório é, na prática, um registro descartado, porque ninguém
 * vai decidir sobre o que não sabe que existe.
 *
 * <p>A primeira regra é sobre o aparelho: o entregador que aperta "sincronizar" duas vezes num sinal ruim
 * não pode registrar duas entregas para o mesmo cliente. A segunda é sobre o encontro de dois registros
 * do mesmo fato — o do aparelho e o do escritório —, e resolver sozinho seria escolher em silêncio qual
 * das duas versões do mundo vale.
 */
test.describe('fila offline da entrega', () => {
  test('o reenvio não duplica, e o conflito aparece na tela esperando decisão', async ({
    page,
    browser,
  }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);
    const cena = await cargaNaRua(page, browser, sfx);

    // --- 1. O aparelho sincroniza a entrega ---
    const aparelho = crypto.randomUUID();
    const operacao = crypto.randomUUID();
    const primeira = await sincroniza(page, aparelho, [entrega(operacao, cena)]);
    expect(primeira[0].status).toBe('APPLIED');
    const provaId = primeira[0].resultId;
    expect(provaId).toBeTruthy();

    // --- 2. O reenvio devolve DUPLICATE e o MESMO resultado ---
    // "Já registrei" e "registrei de novo" são coisas diferentes, e a segunda é uma entrega inventada.
    const reenvio = await sincroniza(page, aparelho, [entrega(operacao, cena)]);
    expect(reenvio[0].status).toBe('DUPLICATE');
    expect(reenvio[0].resultId, 'o reenvio aponta para a prova que já existe').toBe(provaId);

    // E a parada continua com UMA prova: o reenvio não escreveu nada.
    const provas = await get(page, `/api/v1/distribution/stops/${cena.parada}/proof`);
    expect(provas).toHaveLength(1);

    // --- 3. Na tela, a entrega registrada ---
    await abreACarga(page, sfx);
    await expect(page.getByText('Entregue', { exact: false }).first()).toBeVisible();

    // A fila de conflitos é da CERVEJARIA, e a suíte compartilha banco: o que se conta é o quanto ela
    // cresce por causa deste teste. Afirmar "a fila está vazia" passaria sozinho e falharia na fila —
    // ou, pior, o contrário.
    const antes = (await get(page, '/api/v1/distribution/sync/conflicts')).length;

    // --- 4. O conflito de verdade: outro aparelho registra a MESMA parada ---
    // O escritório já tem a versão do primeiro; o segundo chega com outra. Nada é sobrescrito.
    const conflitante = await sincroniza(page, crypto.randomUUID(), [recusa(crypto.randomUUID(), cena)]);
    expect(conflitante[0].status).toBe('CONFLICTED');
    expect(conflitante[0].reason, 'a recusa diz por que conflitou').toBeTruthy();

    // --- 5. E ele APARECE na tela, esperando decisão ---
    // Esta é a asserção que a linha do roteiro pede. Sem ela, "o conflito espera gente" seria uma
    // promessa de servidor: a fila existiria no banco e ninguém a veria.
    const depois = (await get(page, '/api/v1/distribution/sync/conflicts')).length;
    expect(depois, 'o conflito entrou na fila').toBe(antes + 1);

    await abreACarga(page, sfx);
    const aviso = page.locator('.alert-warning').filter({ hasText: 'esperando decisão' });
    await expect(aviso).toBeVisible();
    // O número na tela é o da fila: sem isto, o aviso poderia estar exibindo um total decorativo.
    await expect(aviso).toContainText(`${depois} registro(s)`);
    await expect(aviso).toContainText('Nada foi sobrescrito');

    // --- 6. E a prova original continua de pé ---
    // O contraponto: um conflito que "espera" mas apaga o que havia não seria espera nenhuma.
    const provasDepois = await get(page, `/api/v1/distribution/stops/${cena.parada}/proof`);
    expect(provasDepois).toHaveLength(1);
    expect(provasDepois[0].outcome, 'a versão do escritório continua valendo até alguém decidir')
      .toBe('DELIVERED');
  });
});

const SYNC = '/api/v1/distribution/sync';

async function sincroniza(page: Page, aparelho: string, operacoes: unknown[]) {
  const resposta = await page.request.post(SYNC, {
    headers: await csrf(page),
    data: { deviceId: aparelho, operations: operacoes },
  });
  expect(resposta.ok(), `sincronizar respondeu ${resposta.status()}`).toBe(true);
  return resposta.json();
}

function entrega(operacaoId: string, cena: Cena) {
  return {
    clientOperationId: operacaoId,
    sequence: 1,
    loadId: cena.carga,
    stopId: cena.parada,
    outcome: 'DELIVERED',
    occurredAt: new Date().toISOString(),
    delivered: [cena.keg],
    collected: [],
  };
}

/** A mesma parada, com outro desfecho: é isso que faz o segundo registro conflitar com o primeiro. */
function recusa(operacaoId: string, cena: Cena) {
  return {
    clientOperationId: operacaoId,
    sequence: 1,
    loadId: cena.carga,
    stopId: cena.parada,
    outcome: 'REFUSED',
    occurredAt: new Date().toISOString(),
    delivered: [],
    collected: [],
    note: 'cliente recusou',
  };
}

async function abreACarga(page: Page, sfx: string): Promise<void> {
  await page.goto('/distribution/loads');
  await expect(page.getByRole('heading', { name: 'Cargas', level: 1 })).toBeVisible();
  await page.getByRole('row').filter({ hasText: `CG-${sfx}` })
    .getByRole('button', { name: 'Abrir' }).click();
}

interface Cena {
  carga: string;
  parada: string;
  keg: string;
}

/** Uma carga liberada por outra pessoa, na rua, com uma parada — o estado em que o aparelho sincroniza. */
async function cargaNaRua(page: Page, browser: Browser, sfx: string): Promise<Cena> {
  const lote = await seedSellableLot(page, sfx);
  const cliente = await post(page, '/api/v1/crm/customers', { legalName: `Bar Offline ${sfx}` });
  const carga = await post(page, '/api/v1/distribution/loads', {
    code: `CG-${sfx}`,
    scheduledFor: new Date().toISOString().slice(0, 10),
    capacityLiters: 1000,
  });
  const parada = await post(page, `/api/v1/distribution/loads/${carga}/stops`, {
    customerId: cliente,
    customerName: `Bar Offline ${sfx}`,
    sequence: 1,
  });

  const keg = await post(page, '/api/v1/containers', {
    code: `KEG-${sfx}`,
    kind: 'KEG',
    nominalCapacityLiters: 50,
    ownership: 'OWN',
  });
  const agora = new Date();
  await post(page, `/api/v1/containers/${keg}/inspections`, {
    performedAt: agora.toISOString(),
    validUntil: new Date(agora.getTime() + 365 * 24 * 60 * 60 * 1000).toISOString(),
  });
  await post(page, `/api/v1/containers/${keg}/fills`, {
    finishedLotId: lote.lotId,
    volumeLiters: 50,
  });
  await post(page, `/api/v1/distribution/loads/${carga}/stops/${parada}/containers`, {
    containerId: keg,
  });

  const sessao = await get(page, '/api/v1/security/session');
  await post(page, `/api/v1/distribution/loads/${carga}/driver`, {
    driverId: sessao.userId,
    vehicle: 'ABC-1234',
  });

  const contexto = await browser.newContext();
  try {
    const conferente = await contexto.newPage();
    await login(conferente, CONFERENTE);
    await post(conferente, `/api/v1/distribution/loads/${carga}/release`);
  } finally {
    await contexto.close();
  }
  await post(page, `/api/v1/distribution/loads/${carga}/depart`);

  return { carga, parada, keg };
}

async function csrf(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const token = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token.value } : {};
}
