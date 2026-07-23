import { Routes } from '@angular/router';
import { BlendPageComponent } from './pages/blend-page/blend-page.component';
import { WaterPageComponent } from './pages/water-page/water-page.component';

export const WATER_ROUTES: Routes = [
  { path: '', component: WaterPageComponent },
  { path: 'blend', component: BlendPageComponent },
];
