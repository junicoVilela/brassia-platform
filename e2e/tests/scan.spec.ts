import { expect, login, test } from './support';

/**
 * Leitura de código (INT-003).
 *
 * <p>O que só aparece aqui é a jornada que o QR de verdade produz: o aplicativo de câmera do telefone abre
 * `/scan?code=…`, e a tela resolve e navega sozinha. Não há leitor de câmera embutido — o QR contém um link,
 * e quem lê é o telefone.
 */
test.describe('abrir código', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('o link do QR abre a tela e navega para o destino', async ({ page }) => {
    // É o que a câmera do telefone faz: abre a URL do QR.
    await page.goto('/scan?code=' + encodeURIComponent('brassia://equipamento/TANQUE-01'));

    await page.waitForURL(/\/equipment/);
    expect(page.url()).toContain('ref=TANQUE-01');
  });

  test('a etiqueta de envase JÁ IMPRESSA, com sufixo, continua sendo lida', async ({ page }) => {
    // PKG-004 imprime `brassia://lote/<código>/envase/<plano>`. Recusar o sufixo invalidaria toda etiqueta
    // que já está colada numa caixa.
    await page.goto(
      '/scan?code=' + encodeURIComponent('brassia://lote/LOTE-2026-014/envase/ENV-3'),
    );

    await page.waitForURL(/\/production\/batches/);
    expect(page.url()).toContain('ref=LOTE-2026-014');
  });

  test('código digitado à mão funciona — para etiqueta rasgada ou no computador', async ({ page }) => {
    await page.goto('/scan');

    await page.getByLabel('Código').fill('brassia://op/OP-2026-7');
    await page.getByRole('button', { name: 'Abrir' }).click();

    await page.waitForURL(/\/brew-orders/);
  });

  test('código não reconhecido explica que o problema é a etiqueta', async ({ page }) => {
    await page.goto('/scan?code=' + encodeURIComponent('https://malicioso.example.com/lote/1'));

    await expect(page.getByText(/Este código não é reconhecido/)).toBeVisible();
    // Não navegou para lugar nenhum.
    expect(page.url()).toContain('/scan');
  });

  test('identificador com caminho é recusado — a etiqueta é entrada de terceiro', async ({ page }) => {
    await page.goto('/scan?code=' + encodeURIComponent('brassia://lote/../../admin'));

    await expect(page.getByText(/Este código não é reconhecido/)).toBeVisible();
  });

  test('a tela diz que o código não dá acesso por si só', async ({ page }) => {
    // A frase existe para quem opera entender o desenho: o QR é uma pergunta, não uma chave.
    await page.goto('/scan');

    await expect(page.getByText(/O código não dá acesso a nada por si só/)).toBeVisible();
  });
});
