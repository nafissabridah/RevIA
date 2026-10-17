import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';

/** S'authentifier / S'inscrire (cas d'utilisation « S'inscrire » étend « S'authentifier »). */
@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ecran">
      <section class="presentation">
        <div class="eyebrow">Mini-projet · Sujet 4</div>
        <h1 class="marque">Rev<span>IA</span></h1>
        <p class="accroche">Votre assistant intelligent de revue de code</p>
        <p class="texte">Il détecte les bugs, les mauvaises pratiques et les failles de sécurité, puis propose une correction expliquée.</p>
        <div class="apercu">
          <div class="fenetre">
            <div class="pastilles"><i></i><i></i><i></i><span>UserService.java</span></div>
            <pre><span class="n">3</span>  sql = <span class="s">"SELECT * FROM users WHERE id="</span> + id;</pre>
          </div>
          <div class="alerte">
            <b>Injection SQL détectée · ligne 3</b>
            <span>La valeur « id » est collée dans la requête. Utilisez une requête paramétrée (PreparedStatement).</span>
          </div>
          <div class="score">38<small>/ 100</small></div>
        </div>
      </section>

      <section class="formulaire">
        <form class="carte boite" [formGroup]="form" (ngSubmit)="valider()" novalidate>
          <h2>{{ inscription() ? 'Créer un compte' : 'Connexion' }}</h2>
          <p class="aide">{{ inscription() ? 'Gratuit · 50 revues par jour' : 'Heureux de vous revoir 👋' }}</p>

          @if (inscription()) {
            <div class="champ">
              <label for="nom">Nom</label>
              <input id="nom" class="input" formControlName="nom" autocomplete="name" placeholder="Sarra A." />
              @if (invalide('nom')) { <span class="erreur-champ">Le nom doit faire entre 2 et 80 caractères.</span> }
            </div>
          }
          <div class="champ">
            <label for="email">E-mail</label>
            <input id="email" class="input" type="email" formControlName="email" autocomplete="email" placeholder="vous@exemple.fr" />
            @if (invalide('email')) { <span class="erreur-champ">Saisissez un e-mail valide.</span> }
          </div>
          <div class="champ">
            <label for="mdp">Mot de passe</label>
            <input id="mdp" class="input" type="password" formControlName="motDePasse"
                   [attr.autocomplete]="inscription() ? 'new-password' : 'current-password'" />
            @if (invalide('motDePasse')) {
              <span class="erreur-champ">{{ inscription() ? '8 caractères minimum, avec au moins une lettre et un chiffre.' : 'Saisissez votre mot de passe.' }}</span>
            }
          </div>

          @if (erreur()) { <div class="message-erreur" role="alert">{{ erreur() }}</div> }

          <button class="btn primaire large" type="submit" [disabled]="envoi()">
            @if (envoi()) { <span class="spinner"></span> }
            {{ inscription() ? "S'inscrire" : 'Se connecter' }}
          </button>

          <p class="bascule">
            @if (inscription()) {
              Déjà un compte ? <a routerLink="/connexion">Se connecter</a>
            } @else {
              Pas encore de compte ? <a routerLink="/inscription">S'inscrire</a>
            }
          </p>
        </form>
        <p class="pied">Une IA ne remplace pas le relecteur humain : elle l'aide à se concentrer sur l'essentiel.</p>
      </section>
    </div>
  `,
  styles: [`
    .ecran {
      min-height: 100vh; display: grid; grid-template-columns: 1.15fr 1fr; color: #fff;
      background: radial-gradient(900px 500px at 75% 10%, #2a2a7a 0%, transparent 60%),
                  radial-gradient(700px 500px at 10% 100%, #2b2160 0%, transparent 60%), #0b0e1d;
    }
    .presentation { padding: 9vh 6vw; display: flex; flex-direction: column; justify-content: center; }
    .eyebrow { letter-spacing: .25em; font-size: 12px; color: var(--accent-ia); font-weight: 700; text-transform: uppercase; }
    .marque { font-size: 92px; font-weight: 800; line-height: 1; margin: 18px 0 18px; letter-spacing: -.03em; }
    .marque span { color: var(--accent-ia); }
    .accroche { font-size: 24px; margin: 0 0 12px; }
    .texte { color: #b9bedf; font-size: 15px; max-width: 520px; line-height: 1.55; margin: 0; }
    .apercu { position: relative; margin-top: 36px; max-width: 520px; }
    .fenetre { background: #141831; border: 1px solid #262b52; border-radius: 12px; overflow: hidden; }
    .pastilles { display: flex; align-items: center; gap: 6px; padding: 10px 14px; border-bottom: 1px solid #262b52; color: #9aa0c8; font: 12px var(--mono); }
    .pastilles i { width: 10px; height: 10px; border-radius: 50%; background: #ef4444; }
    .pastilles i:nth-child(2) { background: #f59e0b; }
    .pastilles i:nth-child(3) { background: #22c55e; margin-right: 8px; }
    pre { margin: 0; padding: 12px 14px; background: rgba(229, 72, 77, .15); border-left: 3px solid #e5484d; font-size: 12.5px; color: #e5e7ff; white-space: pre-wrap; }
    .n { color: #6b7298; }
    .s { color: #fde68a; }
    .alerte { margin-top: 12px; background: #2e2a7a; border: 1px solid #5b5bf6; border-radius: 12px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #d9dcff; }
    .alerte b { color: #fff; font-size: 14px; }
    .score { position: absolute; top: -26px; right: -18px; width: 82px; height: 82px; border-radius: 50%; background: #e11d48; display: grid; place-items: center; align-content: center; font-size: 28px; font-weight: 800; box-shadow: 0 10px 30px rgba(225, 29, 72, .4); }
    .score small { font-size: 11px; font-weight: 500; }
    .formulaire { display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 32px; }
    .boite { width: 100%; max-width: 400px; padding: 30px; color: var(--texte); display: flex; flex-direction: column; gap: 14px; }
    h2 { font-size: 22px; }
    .aide { margin: -8px 0 4px; color: var(--texte-2); font-size: 13px; }
    .large { height: 40px; width: 100%; margin-top: 4px; }
    .message-erreur { background: var(--critique-pale); color: var(--critique); border-radius: 9px; padding: 10px 12px; font-size: 13px; }
    .bascule { text-align: center; margin: 0; font-size: 13px; color: var(--texte-2); }
    .pied { color: #8b91bb; font-size: 12.5px; max-width: 400px; text-align: center; margin-top: 18px; }
    @media (max-width: 900px) {
      .ecran { grid-template-columns: 1fr; }
      .presentation { padding: 32px 24px 0; }
      .marque { font-size: 60px; }
      .apercu { display: none; }
    }
  `],
})
export class AuthComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly inscription = signal(false);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  private tentee = false;

  readonly form = this.fb.nonNullable.group({
    nom: [''],
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required]],
  });

  ngOnInit(): void {
    const ins = this.route.snapshot.data['mode'] === 'inscription';
    this.inscription.set(ins);
    if (ins) {
      this.form.controls.nom.setValidators([Validators.required, Validators.minLength(2), Validators.maxLength(80)]);
      this.form.controls.motDePasse.setValidators([
        Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
      ]);
    }
  }

  invalide(champ: 'nom' | 'email' | 'motDePasse'): boolean {
    const c = this.form.controls[champ];
    return c.invalid && (c.touched || this.tentee);
  }

  valider(): void {
    this.tentee = true;
    this.erreur.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { nom, email, motDePasse } = this.form.getRawValue();
    this.envoi.set(true);
    const appel = this.inscription() ? this.auth.inscription(nom.trim(), email.trim(), motDePasse) : this.auth.connexion(email.trim(), motDePasse);
    appel.subscribe({
      next: (r) => this.router.navigate([r.utilisateur.role === 'ADMIN' ? '/admin' : '/revues/nouvelle']),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
