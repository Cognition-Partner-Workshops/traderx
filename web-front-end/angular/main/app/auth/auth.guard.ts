import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

/** Routes guarded by this require a live session; anonymous users are sent to /login. */
export const authGuard: CanActivateFn = (route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.isAuthenticated) {
        return true;
    }
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
