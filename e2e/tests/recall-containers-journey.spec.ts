import { type Page } from '@playwright/test';
import { expect, get, login, post, seedSellableLot, test } from './support';

/**
 * O recall alcança os vasilhames, e a tela mostra.
 *
 * <p>Fecha o `DEB-TRC-003` e a última linha do roteiro de homologação que estava provada **só pelo
 * backend**. `RecallIT#oSimuladoAlcancaOsConteineresDoLote` já provava que a API devolve os nós
 * `CONTAINER` no escopo — e **a tela nunca os mostrava**: o dossiê exibia "Destinos", que vêm de
 * expedição.
 *
 * <p><strong>O vasilhame retornável não passa por expedição nenhuma.</strong> Ele sai cheio na carga e
 * volta vazio. Numa casa que opera retornável, os kegs no cliente são exatamente o que o recall precisa
 * recolher — e quem conduzisse um recall por aquela tela recolheria as caixas vendidas e deixaria os kegs
 * onde estavam, com a informação a um campo de distância.
 *
 * <p>O contraponto é o mesmo do teste de API, e é ele que distingue "achou" de "listou tudo": um terceiro
 * keg, cheio de <em>outro</em> lote, **não** pode aparecer. Sem ele, uma tela que despejasse o inventário
 * inteiro passaria — e mandaria a operação recolher cerveja boa.
 */
test.describe('recall e vasilhames', () => {
  test('os kegs do lote afetado aparecem na tela, e o de outro lote não', async ({ page }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);

    // --- 1. Dois lotes: o que vai ser recolhido e o que não tem nada a ver ---
    const afetado = await seedSellableLot(page, `A${sfx}`);
    const outro = await seedSellableLot(page, `B${sfx}`);

    const kegA = await kegCheio(page, `A1${sfx}`, afetado.lotId);
    const kegB = await kegCheio(page, `A2${sfx}`, afetado.lotId);
    const kegDeFora = await kegCheio(page, `B1${sfx}`, outro.lotId);

    // --- 2. O recall, aberto sobre o lote de produção afetado ---
    const recall = await post(page, '/api/v1/traceability/recalls', {
      nodeType: 'BATCH',
      nodeId: afetado.batchId,
      reason: `vidro na linha de envase ${sfx}`,
    });

    // --- 3. A API alcança os vasilhames — a premissa, que o RecallIT já garante ---
    const dossie = await get(page, `/api/v1/traceability/recalls/${recall}`);
    const noEscopo = (dossie.scope as { node: { type: string; id: string } }[])
      .filter(a => a.node.type === 'CONTAINER')
      .map(a => a.node.id);
    expect(noEscopo).toContain(kegA);
    expect(noEscopo).toContain(kegB);
    expect(noEscopo, 'o keg de outro lote não entra no escopo').not.toContain(kegDeFora);

    // --- 4. E AGORA A TELA MOSTRA ---
    // Era isto que faltava: o dossiê carregava o escopo e o template nunca o renderizava.
    await page.goto('/traceability/recalls');
    await expect(page.getByRole('heading', { name: 'Recalls' })).toBeVisible();
    // O dossiê abre pelo botão do item da lista, e renderiza dentro do próprio item.
    const linha = page.locator('.list-group-item').filter({ hasText: dossie.recall.code });
    await linha.getByRole('button', { name: 'Abrir dossiê' }).click();

    const alcance = linha.locator('h3').filter({ hasText: 'O que este recall alcança' });
    await expect(alcance).toBeVisible();

    // O número que a operação de rua vai procurar, em destaque.
    await expect(alcance).toContainText('2 vasilhame(s) a recolher');

    // E os códigos, que é como o operador reconhece o keg no chão — não o UUID.
    await expect(linha).toContainText(`KEG-A1${sfx}`);
    await expect(linha).toContainText(`KEG-A2${sfx}`);

    // --- 5. O contraponto na tela: o keg de outro lote NÃO está lá ---
    // Sem esta linha, uma tela que listasse o inventário inteiro passaria no teste.
    await expect(linha).not.toContainText(`KEG-B1${sfx}`);
  });
});

/** Um keg etiquetado, inspecionado e cheio do lote informado. */
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
