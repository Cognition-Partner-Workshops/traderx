import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { AppComponent } from './app.component';
import { TradeModule } from './trade/trade.module';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi, withJsonpSupport } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthInterceptor } from './auth/auth.interceptor';
import { LoginComponent } from './auth/login.component';
import { CommonModule } from '@angular/common';
import { AgGridModule } from 'ag-grid-angular';
import { HeaderComponent } from './header/header.component';
import { AccountsModule } from './accounts/accounts.module';
import { RouterModule } from '@angular/router';
import { routes } from './routing';
import { PageNotFoundComponent } from './page-not-found.component';

@NgModule({ declarations: [AppComponent, HeaderComponent, PageNotFoundComponent, LoginComponent],
    bootstrap: [AppComponent], imports: [BrowserModule,
        CommonModule,
        FormsModule,
        TradeModule,
        AccountsModule,
        AgGridModule,
        RouterModule.forRoot(routes)], providers: [
        provideHttpClient(withInterceptorsFromDi(), withJsonpSupport()),
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
    ] })
export class AppModule { }
