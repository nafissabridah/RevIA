import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Utilisateur } from './models';

const CLE_JETON = 'revia.jeton';
const CLE_EXPIRATION = 'revia.expiration';

/** Session : jeton JWT (durée limitée) + utilisateur courant exposé en signal. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly utilisateur = signal<Utilisateur | null>(null);
  readonly estAdmin = computed(() => this.utilisateur()?.role === 'ADMIN');
  readonly estDeveloppeur = computed(() => this.utilisateur()?.role === 'DEVELOPPEUR');

  get jeton(): string | null {
    try {
      const jeton = localStorage.getItem(CLE_JETON);
      const exp = Number(localStorage.getItem(CLE_EXPIRATION) ?? 0);
      if (!jeton || Date.now() > exp) {
        return null;
      }
      return jeton;
    } catch {
      return null;
    }
  }

  estConnecte(): boolean {
    return this.jeton !== null;
  }

  connexion(email: string, motDePasse: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, motDePasse }).pipe(tap((r) => this.ouvrir(r)));
  }

  inscription(nom: string, email: string, motDePasse: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', { nom, email, motDePasse }).pipe(tap((r) => this.ouvrir(r)));
  }

  /** Recharge le profil (quota du jour, nombre de revues). */
  rafraichir(): Observable<Utilisateur> {
    return this.http.get<Utilisateur>('/api/me').pipe(tap((u) => this.utilisateur.set(u)));
  }

  deconnexion(redirection = true): void {
    try {
      localStorage.removeItem(CLE_JETON);
      localStorage.removeItem(CLE_EXPIRATION);
    } catch {
      /* stockage indisponible */
    }
    this.utilisateur.set(null);
    if (redirection) {
      this.router.navigate(['/connexion']);
    }
  }

  private ouvrir(r: AuthResponse): void {
    try {
      localStorage.setItem(CLE_JETON, r.token);
      localStorage.setItem(CLE_EXPIRATION, String(Date.now() + r.expireDansSecondes * 1000 - 30_000));
    } catch {
      /* navigation privée : la session reste en mémoire */
    }
    this.utilisateur.set(r.utilisateur);
  }
}
