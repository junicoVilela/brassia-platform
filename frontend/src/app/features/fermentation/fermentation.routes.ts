import { Routes } from '@angular/router';
import { ProfilesPageComponent } from './pages/profiles-page/profiles-page.component';
import { ReadingsPageComponent } from './pages/readings-page/readings-page.component';
import { SchedulePageComponent } from './pages/schedule-page/schedule-page.component';
import { YeastPageComponent } from './pages/yeast-page/yeast-page.component';

export const FERMENTATION_ROUTES: Routes = [{ path: '', component: ProfilesPageComponent }];

export const FERMENTATION_READING_ROUTES: Routes = [{ path: '', component: ReadingsPageComponent }];

export const FERMENTATION_YEAST_ROUTES: Routes = [{ path: '', component: YeastPageComponent }];

export const FERMENTATION_SCHEDULE_ROUTES: Routes = [{ path: '', component: SchedulePageComponent }];
