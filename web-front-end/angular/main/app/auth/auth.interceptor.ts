import { Injectable } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../service/auth.service';

/** Attaches the TraderX bearer token to every backend call and drops the session on 401. */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
    constructor(private auth: AuthService, private router: Router) { }

    intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
        const token = this.auth.token;
        const isLogin = request.url.endsWith('/Auth/Login');
        const authenticated = token && !isLogin
            ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
            : request;
        return next.handle(authenticated).pipe(
            catchError((error: HttpErrorResponse) => {
                if (error.status === 401 && !isLogin) {
                    this.auth.logout();
                    this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
                }
                return throwError(() => error);
            })
        );
    }
}
