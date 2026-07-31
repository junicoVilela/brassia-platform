import { Routes } from '@angular/router';
import { ProfilesPageComponent } from './pages/profiles-page/profiles-page.component';
import { ReadingsPageComponent } from './pages/readings-page/readings-page.component';

export const FERMENTATION_ROUTES: Routes = [{ path: '', component: ProfilesPageComponent }];

export const FERMENTATION_READING_ROUTES: Routes = [{ path: '', component: ReadingsPageComponent }];
