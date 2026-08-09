import { Routes } from '@angular/router';
import { AssessmentPageComponent } from './pages/assessment-page/assessment-page.component';
import { CopilotPageComponent } from './pages/copilot-page/copilot-page.component';
import { GatewayPageComponent } from './pages/gateway-page/gateway-page.component';

export const AI_ROUTES: Routes = [{ path: '', component: GatewayPageComponent }];

export const COPILOT_ROUTES: Routes = [{ path: '', component: CopilotPageComponent }];

export const ASSESSMENT_ROUTES: Routes = [{ path: '', component: AssessmentPageComponent }];
