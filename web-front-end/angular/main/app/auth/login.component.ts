import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html'
})
export class LoginComponent {
    logonId = '';
    password = '';
    error = '';
    busy = false;

    constructor(private auth: AuthService, private router: Router, private route: ActivatedRoute) { }

    submit() {
        if (!this.logonId || !this.password) {
            this.error = 'Logon id and password are required';
            return;
        }
        this.busy = true;
        this.error = '';
        this.auth.login(this.logonId, this.password).subscribe({
            next: () => {
                const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/trade';
                this.router.navigateByUrl(returnUrl);
            },
            error: (err) => {
                this.busy = false;
                this.error = err?.status === 401 ? 'Unknown user or wrong password' : 'Login failed, please try again';
            }
        });
    }
}
