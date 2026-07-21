import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { loadFilaTheme } from './app/layout/theme-loader';

loadFilaTheme();

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
