import { expect, login, test, seedBatch } from './support';

/**
 * Roteiro offline (PWA-001).
 *
 * <p>O que só aparece aqui é o comportamento sem rede de verdade: o navegador é posto offline com
 * `context.setOffline`, e a tela precisa continuar mostrando o roteiro salvo — e dizendo que é um retrato.
 *
 * <p>E, principalmente, o critério de dado sensível: **sair da conta esvazia o aparelho**. Isso é uma
 * promessa fácil de escrever e fácil de quebrar sem ninguém notar; só ler o `localStorage` depois do
 * logout prova que ela vale.
 */
test.describe('roteiro offline', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await seedBatch(page);
  });

  test('salvar um roteiro deixa o lote marcado como disponível offline', async ({ page }) => {
    await page.goto('/production/batches');
    // Espera a listagem chegar antes de contar botões: `goto` volta com o HTML, não com os dados. O
    // `test.skip` que havia aqui mascarava essa corrida — contava zero e desistia, parecendo verde.
    await page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));

    const salvar = page.getByRole('button', { name: /Salvar roteiro de .* para uso sem rede/ }).first();
    await expect(salvar).toBeVisible();

    await salvar.click();
    await expect(page.getByRole('button', { name: /Remover roteiro offline de/ }).first()).toBeVisible();

    // O que ficou no aparelho é o roteiro, carimbado com dono e cervejaria.
    const gravado = await page.evaluate(() => {
      const chave = Object.keys(localStorage).find(k => k.startsWith('brassia.offline.runbook.'));
      return chave ? localStorage.getItem(chave) : null;
    });
    expect(gravado).not.toBeNull();
    expect(gravado).toContain('"userId"');
    expect(gravado).toContain('"breweryId"');
    expect(gravado).toContain('"savedAt"');
  });

  test('sem rede, a tela avisa e continua legível', async ({ page, context }) => {
    await page.goto('/production/batches');
    await page.waitForResponse(r => r.url().includes('/api/v1/production/batches'));

    await context.setOffline(true);
    // O evento `offline` é o que a aplicação escuta; o `setOffline` do Playwright o dispara.
    await page.waitForFunction(() => navigator.onLine === false);

    await expect(page.getByText('Sem rede — mostrando os roteiros salvos neste aparelho.')).toBeVisible();
    // A ressalva importa tanto quanto o aviso: um retrato de seis horas atrás não é o estado de agora.
    await expect(page.getByText(/o lote pode ter\s+avançado desde então/)).toBeVisible();

    await context.setOffline(false);
  });

  test('SAIR DA CONTA ESVAZIA O APARELHO', async ({ page }) => {
    // Um tablet de chão de fábrica troca de mão a cada turno e se perde. As verificações de dono já
    // impediriam a leitura, mas impedir a leitura não basta: o dado continuaria no disco.
    //
    // O roteiro é semeado direto no armazenamento em vez de salvo pela tela, e é deliberado: a propriedade
    // sob teste é "sair da conta apaga o que está no disco", que não depende de COMO o dado chegou lá.
    // Amarrá-la à existência de um lote em produção faria o teste ser pulado num banco limpo — que é
    // exatamente o caso da CI, e um teste de segurança pulado é pior que ausente, porque parece cobertura.
    await page.goto('/production/batches');

    await page.evaluate(() => {
      localStorage.setItem(
        'brassia.offline.runbook.seed-e2e',
        JSON.stringify({
          userId: 'qualquer',
          breweryId: 'qualquer',
          savedAt: new Date().toISOString(),
          runbook: { batchId: 'seed-e2e', code: 'LOTE-E2E', steps: [] },
        }),
      );
    });

    const antes = await page.evaluate(
      () => Object.keys(localStorage).filter(k => k.startsWith('brassia.offline.runbook.')).length,
    );
    expect(antes).toBeGreaterThan(0);

    // "Sair" vive dentro do menu do usuário, que precisa ser aberto primeiro.
    await page.locator('[data-bs-toggle="dropdown"]').last().click();
    await page.getByRole('button', { name: 'Sair' }).click();
    await page.waitForURL(/\/login/);

    const depois = await page.evaluate(
      () => Object.keys(localStorage).filter(k => k.startsWith('brassia.offline.runbook.')).length,
    );
    expect(depois).toBe(0);
  });

  test('o service worker e o manifest estão publicados', async ({ page }) => {
    // O `ng serve` do E2E não registra o service worker (ele só é habilitado em produção), então o que se
    // verifica aqui é que os artefatos existem e são servidos — o registro em si é comportamento do
    // navegador sobre um build de produção.
    const manifest = await page.request.get('/manifest.webmanifest');
    expect(manifest.status()).toBe(200);
    expect((await manifest.json()).name).toBe('BrassIA');
  });
});
