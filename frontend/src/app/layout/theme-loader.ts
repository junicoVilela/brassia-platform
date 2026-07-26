/**
 * Carrega os assets do tema Fila (Bootstrap 5 admin) em runtime, a partir de
 * `public/assets/fila/`. Feito em runtime — e não no index.html — porque o tema
 * é pago e não é versionado (ver THEME_SETUP.md): assim o build/CI não depende
 * dos arquivos. Se ausentes, os recursos apenas 404 sem quebrar a aplicação.
 */
const STYLES = [
  'assets/fila/css/remixicon.css',
  'assets/fila/css/simplebar.css',
  'assets/fila/css/sidebar-menu.css',
  'assets/fila/css/style.css',
];
const SCRIPTS = ['assets/fila/js/bootstrap.bundle.min.js'];

export function loadFilaTheme(): void {
  // Inserimos o CSS do tema ANTES do bundle de estilos do Angular (`styles.scss`),
  // que o CLI já injeta no `<head>` do index.html. Assim os nossos overrides em
  // `styles.scss` vencem a cascata naturalmente (mesma especificidade → o que
  // carrega depois vence), sem precisar de `!important`. Inserir cada link antes
  // do mesmo nó âncora preserva a ordem entre os arquivos do tema.
  const head = document.head;
  const anchor = head.firstChild;
  for (const href of STYLES) {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    head.insertBefore(link, anchor);
  }
  for (const src of SCRIPTS) {
    const script = document.createElement('script');
    script.src = src;
    script.defer = true;
    document.body.appendChild(script);
  }
}
