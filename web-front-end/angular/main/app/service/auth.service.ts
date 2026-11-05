import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from 'main/environments/environment';

export interface AuthSession {
    accessToken: string;
    expiresAt: string;
    logonId: string;
    fullName?: string;
    roles: string[];
}

const STORAGE_KEY = 'traderx.session';

/**
 * Holds the caller's TraderX bearer token (issued by people-service POST /Auth/Login).
 * Every HTTP call and the trade-feed socket handshake present this token.
 */
@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly sessionSubject = new BehaviorSubject<AuthSession | null>(AuthService.restore());
    readonly session$: Observable<AuthSession | null> = this.sessionSubject.asObservable();

    constructor(private http: HttpClient) { }

    get session(): AuthSession | null {
        return this.sessionSubject.value;
    }

    get token(): string | null {
        return this.session?.accessToken ?? null;
    }

    get isAuthenticated(): boolean {
        const session = this.session;
        return !!session && new Date(session.expiresAt).getTime() > Date.now();
    }

    hasRole(role: string): boolean {
        return !!this.session?.roles?.includes(role);
    }

    login(logonId: string, password: string): Observable<AuthSession> {
        return this.http.post<AuthSession>(`${environment.peopleUrl}/Auth/Login`, { logonId, password }).pipe(
            tap((session) => this.store(session))
        );
    }

    logout() {
        sessionStorage.removeItem(STORAGE_KEY);
        this.sessionSubject.next(null);
    }

    private store(session: AuthSession) {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.sessionSubject.next(session);
    }

    private static restore(): AuthSession | null {
        try {
            const raw = sessionStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return null;
            }
            const session = JSON.parse(raw) as AuthSession;
            return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
        } catch {
            return null;
        }
    }
}
