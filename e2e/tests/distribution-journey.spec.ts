import { type Page } from '@playwright/test';
import { CONFERENTE, expect, get, login, post, seedSellableLot, test } from './support';

/**
 * Jornada de distribuição de ponta a ponta: encher → carregar → entregar → coletar → higienizar.
 *
 * <p>Fecha o item de E2E que o plano de testes da Sprint 20 pede e que ficou aberto no encerramento.
 * Retornáveis tinham teste de domínio, de integração e de store, e nenhuma jornada percorrendo o ciclo
 * inteiro — que é justamente onde o assunto vive: um keg atravessa lotes, clientes e meses, e o que se
 * quer provar é que ele volta ao começo sem perder o histórico pelo caminho.
 *
 * <p><strong>A separação de deveres exige duas pessoas de verdade.</strong> Quem monta a carga não a
 * libera, e a regra tem três camadas — o agregado, a alçada e um `CHECK` no banco. Este teste exercita a
 * recusa e o caminho depois dela, com o conferente do bootstrap local em contexto próprio: fingir a
 * segunda pessoa reaproveitando a sessão do primeiro provaria exatamente nada.
 */
test.describe('jornada de distribuição', () => {
  test('do keg cheio à coleta e à higienização, com a carga liberada por outra pessoa', async ({
    page,
    browser,
  }) => {
    await login(page);
    const sfx = Date.now().toString().slice(-8);

    // --- 1. Cerveja para pôr dentro: um lote acabado e liberado pela qualidade ---
    // A saída da casa cobra a assinatura da qualidade, e é a carga quem cobra: encher precede liberar,
    // expedir não.
    const lot = await seedSellableLot(page, sfx);

    // --- 2 e 3. O vasilhame: identidade, etiqueta, inspeção e cerveja dentro ---
    const keg = await kegCheio(page, sfx, lot.lotId);

    // Ler o código identifica, e não autoriza — é a mesma porta que o aplicativo da rua usa.
    const lido = await get(page, `/api/v1/containers/by-identifier?value=QR-${sfx}`);
    expect(lido.id, 'a etiqueta resolve para o contêiner').toBe(keg);

    const cheio = await get(page, `/api/v1/containers/${keg}`);
    expect(cheio.state).toBe('FILLED');

    // --- 4. Montar a carga: parada, vasilhame e motorista ---
    const cliente = await post(page, '/api/v1/crm/customers', { legalName: `Bar da Rota ${sfx}` });
    const carga = await post(page, '/api/v1/distribution/loads', {
      code: `CG-${sfx}`,
      scheduledFor: new Date().toISOString().slice(0, 10),
      capacityLiters: 1000,
    });
    const parada = await post(page, `/api/v1/distribution/loads/${carga}/stops`, {
      customerId: cliente,
      customerName: `Bar da Rota ${sfx}`,
      sequence: 1,
    });
    await post(page, `/api/v1/distribution/loads/${carga}/stops/${parada}/containers`, {
      containerId: keg,
    });
    const sessao = await get(page, '/api/v1/security/session');
    await post(page, `/api/v1/distribution/loads/${carga}/driver`, {
      driverId: sessao.userId,
      vehicle: 'ABC-1234',
    });

    // --- 5. Quem montou não libera, e a recusa é o ponto ---
    // A conferência existe para encontrar o erro de quem montou, e quem montou relê o próprio trabalho
    // enxergando o que quis colocar, e não o que colocou. Feita pela mesma pessoa, custa o mesmo tempo e
    // não encontra nada.
    const proprio = await page.request.post(`/api/v1/distribution/loads/${carga}/release`, {
      headers: await csrf(page),
    });
    expect(proprio.status(), 'quem planejou a carga não pode conferi-la').toBe(409);

    // --- 6. A outra pessoa libera, em sessão própria ---
    const contexto = await browser.newContext();
    const paginaConferente = await contexto.newPage();
    await login(paginaConferente, CONFERENTE);
    await post(paginaConferente, `/api/v1/distribution/loads/${carga}/release`);
    await contexto.close();

    const liberada = await get(page, `/api/v1/distribution/loads/${carga}`);
    expect(liberada.status).toBe('RELEASED');
    // A carga liberada congela: acrescentar um keg desfaria a conferência sem ninguém perceber, e o
    // papel que o motorista leva deixaria de descrever o que está no caminhão.
    expect(liberada.frozen, 'a carga conferida congela').toBe(true);

    // --- 7. A carga sai, e o keg vai junto ---
    await post(page, `/api/v1/distribution/loads/${carga}/depart`);
    const naRua = await get(page, `/api/v1/containers/${keg}`);
    expect(naRua.state, 'partir põe o vasilhame na rua').toBe('IN_TRANSIT');

    // --- 8. A entrega: prova append-only, e o keg chega ao cliente ---
    await post(page, `/api/v1/distribution/loads/${carga}/stops/${parada}/proof`, {
      outcome: 'DELIVERED',
      delivered: [keg],
      collected: [],
    });
    const noCliente = await get(page, `/api/v1/containers/${keg}`);
    expect(noCliente.state).toBe('AT_CUSTOMER');

    // Uma prova por parada: a segunda tentativa é o duplo clique do celular no meio da rua, e ela
    // viraria duas entregas para o mesmo cliente.
    const repetida = await page.request.post(
      `/api/v1/distribution/loads/${carga}/stops/${parada}/proof`,
      { headers: await csrf(page), data: { outcome: 'DELIVERED', delivered: [], collected: [] } },
    );
    expect(repetida.status(), 'uma prova por parada').toBe(409);

    // --- 9. A coleta é outro fato, e vem na viagem seguinte ---
    // O caminhão sai com cheios e volta com vazios: a carga de volta leva um segundo keg para o mesmo
    // bar e recolhe o primeiro. Uma carga só de coleta não sai — "uma carga sem nada dentro não sai" é
    // regra do agregado, e um caminhão liberado vazio some da rota do dia.
    const segundoKeg = await kegCheio(page, `${sfx}B`, lot.lotId);
    const volta = await post(page, '/api/v1/distribution/loads', {
      code: `CG-V${sfx}`,
      scheduledFor: new Date().toISOString().slice(0, 10),
      capacityLiters: 1000,
    });
    const paradaVolta = await post(page, `/api/v1/distribution/loads/${volta}/stops`, {
      customerId: cliente,
      customerName: `Bar da Rota ${sfx}`,
      sequence: 1,
    });
    await post(page, `/api/v1/distribution/loads/${volta}/stops/${paradaVolta}/containers`, {
      containerId: segundoKeg,
    });
    await post(page, `/api/v1/distribution/loads/${volta}/driver`, {
      driverId: sessao.userId,
      vehicle: 'ABC-1234',
    });
    const outroContexto = await browser.newContext();
    const outraPagina = await outroContexto.newPage();
    await login(outraPagina, CONFERENTE);
    await post(outraPagina, `/api/v1/distribution/loads/${volta}/release`);
    await outroContexto.close();
    await post(page, `/api/v1/distribution/loads/${volta}/depart`);

    // Entregar e coletar são fatos separados na mesma parada: um keg fica, o outro volta.
    await post(page, `/api/v1/distribution/loads/${volta}/stops/${paradaVolta}/proof`, {
      outcome: 'DELIVERED',
      delivered: [segundoKeg],
      collected: [keg],
    });

    // --- 10. O que voltou está sujo até alguém dizer o contrário ---
    // `RETURNED` não é `EMPTY`. Derivar a disponibilidade da chegada encheria com cerveja um vasilhame
    // que ninguém lavou, e o problema apareceria na boca do cliente.
    const recolhido = await get(page, `/api/v1/containers/${keg}`);
    expect(recolhido.state).toBe('RETURNED');
    expect(recolhido.fillable, 'o que voltou não está pronto para encher').toBe(false);

    // --- 11. A higienização diz COMO, e é isso que se audita ---
    // "Higienizado" sem método é um carimbo, e a pergunta real chega três meses depois, quando alguém
    // quer saber se aquele keg foi lavado antes da cerveja que o cliente reclamou.
    await post(page, `/api/v1/containers/${keg}/sanitations`, { method: 'soda 2% a 60 °C' });
    const higienizacoes = await get(page, `/api/v1/containers/${keg}/sanitations`);
    expect(higienizacoes.length).toBe(1);
    expect(higienizacoes[0].method).toBe('soda 2% a 60 °C');
    expect(higienizacoes[0].performedBy, 'quem lavou fica registrado').toBeTruthy();

    // --- 12. O histórico do vasilhame conta a volta inteira ---
    const enchimentos = await get(page, `/api/v1/containers/${keg}/fills`);
    expect(enchimentos.length, 'o enchimento continua no histórico depois da volta').toBe(1);
    expect(enchimentos[0].finishedLotId).toBe(lot.lotId);

    // --- 13. A tela mostra o keg onde alguém o procura ---
    await page.goto('/containers');
    await expect(page.getByRole('heading', { name: 'Contêineres', level: 1 })).toBeVisible();
    await expect(page.getByText(`KEG-${sfx}`, { exact: false }).first()).toBeVisible();
  });
});

/**
 * Um keg pronto para sair: etiquetado, inspecionado e cheio do lote informado.
 *
 * <p>Encher exige as três coisas juntas — condição boa, estado vazio e **inspeção válida**. O vasilhame
 * nasce sem inspeção de propósito: tratar a ausência como aprovação deixaria a frota nova inteira fora
 * de qualquer controle.
 */
async function kegCheio(page: Page, sfx: string, finishedLotId: string): Promise<string> {
  const keg = await post(page, '/api/v1/containers', {
    code: `KEG-${sfx}`,
    kind: 'KEG',
    nominalCapacityLiters: 50,
    ownership: 'OWN',
  });
  await post(page, `/api/v1/containers/${keg}/identifiers`, {
    value: `QR-${sfx}`,
    technology: 'QR',
  });
  const agora = new Date();
  await post(page, `/api/v1/containers/${keg}/inspections`, {
    performedAt: agora.toISOString(),
    validUntil: new Date(agora.getTime() + 365 * 24 * 60 * 60 * 1000).toISOString(),
  });
  await post(page, `/api/v1/containers/${keg}/fills`, { finishedLotId, volumeLiters: 50 });
  return keg;
}

/** O cabeçalho CSRF, para as chamadas cujo status importa e que por isso não passam pelo `post`. */
async function csrf(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies();
  const token = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  return token ? { 'X-XSRF-TOKEN': token.value } : {};
}
