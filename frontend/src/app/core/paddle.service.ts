import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { langueCourante } from './langue.service';
import { AbonnementConfig, Plan } from './models';

/** Script officiel Paddle.js v2 (chargé uniquement si le paiement est configuré côté serveur). */
export const PADDLE_JS = 'https://cdn.paddle.com/paddle/v2/paddle.js';

/** Quotas par défaut (avant la réponse du serveur, ou serveur injoignable). */
export const QUOTAS_PAR_DEFAUT: Record<Plan, number> = { GRATUIT: 20, PRO: 200, EQUIPE: 1000 };

interface PaddleJs {
  Environment: { set(env: string): void };
  Initialize(opts: { token: string; eventCallback?: (e: { name?: string }) => void }): void;
  Checkout: { open(opts: unknown): void };
}

declare global {
  interface Window { Paddle?: PaddleJs }
}

/**
 * Paiement Paddle Billing (marchand officiel : TVA mondiale, factures, moyens de paiement).
 * Le navigateur ne reçoit que le jeton client PUBLIC ; l'offre du compte n'est modifiée que par le webhook signé
 * reçu par le serveur, jamais par le navigateur.
 */
@Injectable({ providedIn: 'root' })
export class PaddleService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly config = signal<AbonnementConfig | null>(null);
  /** Paiement en ligne disponible (sinon : inscription + contact par e-mail, comme avant). */
  readonly actif = computed(() => !!this.config()?.actif);
  readonly quotas = computed(() => this.config()?.quotas ?? QUOTAS_PAR_DEFAUT);
  /** Vrai entre la fin du checkout et l'arrivée du webhook (l'offre n'est pas encore à jour). */
  readonly enAttente = signal(false);

  private config$?: Observable<AbonnementConfig | null>;
  private script?: Promise<PaddleJs>;

  charger(): Observable<AbonnementConfig | null> {
    this.config$ ??= this.api.abonnementConfig().pipe(
      catchError(() => of(null)),
      tap((c) => this.config.set(c)),
      shareReplay(1),
    );
    return this.config$;
  }

  prixDisponible(plan: Plan): boolean {
    const c = this.config();
    return !!c?.actif && !!(plan === 'PRO' ? c.prixPro : plan === 'EQUIPE' ? c.prixEquipe : '');
  }

  /** Ouvre le checkout Paddle (overlay) pour l'utilisateur connecté. */
  async ouvrirCheckout(plan: Plan): Promise<void> {
    const c = this.config();
    const u = this.auth.utilisateur();
    const priceId = plan === 'PRO' ? c?.prixPro : plan === 'EQUIPE' ? c?.prixEquipe : '';
    if (!c?.actif || !u || !priceId) {
      throw new Error('Paiement indisponible');
    }
    const paddle = await this.paddleJs(c);
    paddle.Checkout.open({
      items: [{ priceId, quantity: 1 }],
      customer: { email: u.email },
      // Relu par le webhook pour rattacher l'abonnement au bon compte
      customData: { utilisateurId: String(u.id), email: u.email, plan },
      settings: { displayMode: 'overlay', locale: langueCourante(), theme: 'light' },
    });
  }

  private paddleJs(c: AbonnementConfig): Promise<PaddleJs> {
    this.script ??= new Promise<PaddleJs>((ok, ko) => {
      const init = () => {
        const p = window.Paddle;
        if (!p) {
          ko(new Error('Paddle.js indisponible'));
          return;
        }
        if (c.environnement === 'sandbox') {
          p.Environment.set('sandbox');
        }
        p.Initialize({ token: c.clientToken, eventCallback: (e) => this.evenement(e.name) });
        ok(p);
      };
      if (window.Paddle) {
        init();
        return;
      }
      const s = document.createElement('script');
      s.src = PADDLE_JS;
      s.async = true;
      s.onload = init;
      s.onerror = () => {
        this.script = undefined;
        ko(new Error('Paddle.js indisponible'));
      };
      document.head.appendChild(s);
    });
    return this.script;
  }

  /** Après paiement : l'offre change à réception du webhook ; on recharge le profil quelques fois. */
  private evenement(nom?: string): void {
    if (nom !== 'checkout.completed') {
      return;
    }
    this.enAttente.set(true);
    const planAvant = this.auth.utilisateur()?.plan;
    let essais = 0;
    const relire = () => {
      essais++;
      this.auth.rafraichir().subscribe({
        next: (u) => {
          if (u.plan !== planAvant || essais >= 6) {
            this.enAttente.set(false);
          } else {
            setTimeout(relire, 3000);
          }
        },
        error: () => this.enAttente.set(false),
      });
    };
    setTimeout(relire, 2000);
  }
}
