import { type Page } from '@playwright/test';
import { CONFERENTE, expect, get, login, post, seedSellableLot, test } from './support';

/**
 * Consentimento na entrega: a assinatura é dado pessoal, e a operação não depende dela.
 *
 * <p>Fecha a linha do roteiro de homologação que estava provada **só pelo backend**. `DeliveryIT` prova
 * as três regras pela API — a entrega acontece sem assinatura, a mídia não existe sem finalidade, e a
 * chave do arquivo não sai na listagem. Nenhuma delas tinha sido vista numa tela.
 *
 * <p><strong>A diferença é o que faz a regra valer na prática.</strong> Uma tela que exigisse o nome de
 * quem assinou para o botão funcionar transformaria o dado pessoal em obrigatório, com a API impecável e
 * a regra revogada na ponta — e é na ponta que o entregador está, com o cliente esperando na porta.
 *
 * <p>A outra metade é a finalidade: quem opera **não digita** para que serve a assinatura, e não deveria
 * mesmo — pedir isso a cada parada produziria "entrega" mil vezes e um cheque em branco na milésima. A
 * tela a fornece, e o teste cobra que ela chegue gravada.
 */
test.describe('consentimento na entrega', () => {
  test('a entrega acontece sem assinatura, e com ela a finalidade vai junto sem ninguém digitar', async ({
    page,
    browser,
  }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);

    // --- 1. Uma carga na rua, com duas paradas: uma sem assinatura, outra com ---
    const cena = await cargaNaRua(page, browser, sfx);

    await page.goto('/distribution/loads');
    await expect(page.getByRole('heading', { name: 'Cargas', level: 1 })).toBeVisible();
    await page.getByRole('row').filter({ hasText: `CG-${sfx}` })
      .getByRole('button', { name: 'Abrir' }).click();

    // --- 2. A primeira parada, SEM assinatura: o campo fica em branco ---
    // Este é o teste inteiro. Se o botão exigisse o nome, o dado pessoal seria obrigatório na prática.
    await page.getByRole('button', { name: 'Registrar entrega' }).first().click();
    await expect(page.getByLabel('Quem assinou')).toHaveValue('');
    await page.getByRole('button', { name: 'Registrar', exact: true }).click();

    await expect(page.getByText('Entregue', { exact: false }).first()).toBeVisible();
    // E a tela não inventa assinatura nenhuma para a parada que não teve.
    await expect(page.getByText('assinatura de')).toHaveCount(0);

    // --- 3. A prova gravada não tem mídia, e a entrega valeu ---
    const semAssinatura = await get(page, `/api/v1/distribution/loads/${cena.carga}/proofs`);
    expect(semAssinatura).toHaveLength(1);
    expect(semAssinatura[0].outcome).toBe('DELIVERED');
    expect(semAssinatura[0].mediaKind, 'sem consentimento não se guarda mídia').toBeFalsy();

    // --- 4. A segunda parada, COM assinatura ---
    await page.getByRole('button', { name: 'Registrar entrega' }).first().click();
    await page.getByLabel('Quem assinou').fill('Bruno do Bar');
    await page.getByRole('button', { name: 'Registrar', exact: true }).click();

    await expect(page.getByText('assinatura de Bruno do Bar')).toBeVisible();

    // --- 5. A finalidade foi gravada, e ninguém a digitou ---
    // É a regra "a mídia não existe sem finalidade" vista do lado de quem opera: ele não tem como
    // produzir uma assinatura sem finalidade, porque a tela não oferece esse caminho.
    const comAssinatura = await get(page, `/api/v1/distribution/loads/${cena.carga}/proofs`);
    const assinada = comAssinatura.find((p: { mediaKind: string | null }) => p.mediaKind);
    expect(assinada, 'a segunda parada guardou a assinatura').toBeTruthy();
    expect(assinada.mediaKind).toBe('SIGNATURE');
    expect(assinada.consentedByName).toBe('Bruno do Bar');
    expect(assinada.mediaPurpose, 'a finalidade acompanha a mídia').toBeTruthy();

    // --- 6. A chave do arquivo NÃO chega ao navegador ---
    // A listagem diz que existe assinatura e de quem; onde ela está guardada é outra conversa, e quem
    // tem a listagem não tem o arquivo. Sem isto, "a mídia é protegida" seria promessa de servidor.
    expect(JSON.stringify(comAssinatura))
      .not.toContain(`assinatura/${cena.carga}`);
    expect(JSON.stringify(comAssinatura)).not.toContain('storageKey');
  });
});

/**
 * Uma carga liberada por outra pessoa, na rua, com duas paradas prontas para registrar.
 *
 * <p>O preparo vai pela API — montar carga tem jornada própria em `distribution-journey`, e repeti-la
 * aqui testaria a distribuição em vez do consentimento. O que esta jornada faz **na tela** é a parte que
 * só existe no fim: o formulário da prova.
 */
async function cargaNaRua(
  page: Page,
  browser: import('@playwright/test').Browser,
  sfx: string,
): Promise<{ carga: string }> {
  const lote = await seedSellableLot(page, sfx);
  const cliente = await post(page, '/api/v1/crm/customers', { legalName: `Bar do Consentimento ${sfx}` });
  const carga = await post(page, '/api/v1/distribution/loads', {
    code: `CG-${sfx}`,
    scheduledFor: new Date().toISOString().slice(0, 10),
    capacityLiters: 1000,
  });

  for (const [i, marca] of ['A', 'B'].entries()) {
    const parada = await post(page, `/api/v1/distribution/loads/${carga}/stops`, {
      customerId: cliente,
      customerName: `Bar do Consentimento ${sfx}`,
      sequence: i + 1,
    });
    await post(page, `/api/v1/distribution/loads/${carga}/stops/${parada}/containers`, {
      containerId: await kegCheio(page, `${sfx}${marca}`, lote.lotId),
    });
  }

  const sessao = await get(page, '/api/v1/security/session');
  await post(page, `/api/v1/distribution/loads/${carga}/driver`, {
    driverId: sessao.userId,
    vehicle: 'ABC-1234',
  });

  // Quem monta não libera: a segunda pessoa, em contexto próprio.
  const contexto = await browser.newContext();
  try {
    const conferente = await contexto.newPage();
    await login(conferente, CONFERENTE);
    await post(conferente, `/api/v1/distribution/loads/${carga}/release`);
  } finally {
    await contexto.close();
  }
  await post(page, `/api/v1/distribution/loads/${carga}/depart`);

  return { carga };
}

/** Um keg etiquetado, inspecionado e cheio — as três coisas que encher exige. */
async function kegCheio(page: Page, sfx: string, finishedLotId: string): Promise<string> {
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
  await post(page, `/api/v1/containers/${keg}/fills`, { finishedLotId, volumeLiters: 50 });
  return keg;
}
