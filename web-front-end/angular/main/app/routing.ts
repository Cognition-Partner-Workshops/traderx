import { Routes } from '@angular/router';
import { AccountComponent } from './accounts/account.component';
import { PageNotFoundComponent } from './page-not-found.component';
import { TradeComponent } from './trade/trade.component';
import { LoginComponent } from './auth/login.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
    { path: 'login', component: LoginComponent },
    { path: 'trade', component: TradeComponent, canActivate: [authGuard] },
    { path: 'account', component: AccountComponent, canActivate: [authGuard] },
    { path: '', redirectTo: '/trade', pathMatch: 'full' }, // redirect to `first-component`
    { path: '**', component: PageNotFoundComponent }
];
