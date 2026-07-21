# Tema Fila — setup (asset pago, não versionado)

A interface usa o tema **Fila — Multipurpose Bootstrap 5 Admin Dashboard**
(ThemeForest). Por ser um asset **pago**, seus arquivos **não são versionados**
(o repositório é público). Cada ambiente precisa colocá-los localmente.

## Onde colocar

Copie o subconjunto abaixo da pasta comprada (`fila/assets/…`) para
`frontend/public/assets/fila/`, preservando a estrutura:

```
frontend/public/assets/fila/
├── css/
│   ├── style.css          # tema (já embute o Bootstrap 5)
│   ├── sidebar-menu.css
│   ├── simplebar.css
│   └── remixicon.css
├── fonts/                 # fontes do remixicon (woff2, woff, ttf, eot, svg)
├── images/                # logo-icon.png, favicon.png
└── js/
    └── bootstrap.bundle.min.js
```

## Como é carregado

`src/app/layout/theme-loader.ts` injeta esses CSS/JS em runtime (chamado no
`main.ts`). Não estão no `index.html` de propósito: assim o build/CI **não
depende** dos arquivos pagos. Se ausentes, os recursos apenas retornam 404 e a
aplicação continua funcionando (sem o visual do tema).

## Deploy

O pipeline de deploy deve providenciar a mesma pasta `public/assets/fila/` a
partir de uma fonte privada (não do git). O `ng build` copia `public/` para
`dist/brassia-web/browser/`.
