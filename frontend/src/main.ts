import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { loadFilaTheme } from './app/layout/theme-loader';
import { initThemeMode } from './app/core/theme/theme-mode.service';

loadFilaTheme();
initThemeMode();

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
