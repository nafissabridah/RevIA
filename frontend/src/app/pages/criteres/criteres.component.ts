import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { messageErreur } from '../../core/http';
import { TPipe } from '../../core/i18n';
import { LangueService } from '../../core/langue.service';
import { CouvertureCriteres } from '../../core/models';
import { badgeCritere } from '../../shared/couverture';
import { IconeComponent } from '../../shared/icone.component';
import { ScoreRingComponent } from '../../shared/score-ring.component';

/** Limites de l'API (miroir de la validation côté serveur). */
const MAX_CRITERES = 20000;
const MAX_DIFF = 400000;
const MAX_LIGNES_DIFF = 4000;

/**
 * Exemple : 3 critères et un petit diff (code + un fichier de test) choisis pour que même le mode démo
 * rende un verdict contrasté — AC-1 implémenté et testé, AC-2 implémenté sans test, AC-3 absent.
 */
const EXEMPLE_CRITERES = `As a registered customer, I want to reset my password so that I can sign in again.

Acceptance criteria:
- A reset link is sent by email
- The reset link expires after 30 minutes
- After 5 failed requests in one hour, further requests are blocked`;

const EXEMPLE_DIFF = `diff --git a/src/main/java/com/shop/auth/PasswordResetService.java b/src/main/java/com/shop/auth/PasswordResetService.java
new file mode 100644
--- /dev/null
+++ b/src/main/java/com/shop/auth/PasswordResetService.java
@@ -0,0 +1,26 @@
+package com.shop.auth;
+
+public class PasswordResetService {
+    private static final Duration VALIDITY = Duration.ofMinutes(30);
+    private final TokenRepository tokens;
+    private final UserRepository users;
+    private final Mailer mailer;
+
+    public void sendResetLink(String email) {
+        Token token = Token.random(email, Instant.now().plus(VALIDITY));
+        tokens.save(token);
+        mailer.send(email, "Reset your password", "https://shop.example/reset?token=" + token.value());
+    }
+
+    public void resetPassword(String value, String newPassword) {
+        Token token = tokens.find(value).orElseThrow(InvalidTokenException::new);
+        if (token.expiresAt().isBefore(Instant.now())) {
+            throw new ExpiredLinkException();
+        }
+        users.updatePassword(token.email(), newPassword);
+        tokens.delete(token);
+    }
+}
diff --git a/src/test/java/com/shop/auth/PasswordResetServiceTest.java b/src/test/java/com/shop/auth/PasswordResetServiceTest.java
new file mode 100644
--- /dev/null
+++ b/src/test/java/com/shop/auth/PasswordResetServiceTest.java
@@ -0,0 +1,12 @@
+class PasswordResetServiceTest {
+    private final Mailer mailer = mock(Mailer.class);
+    private final PasswordResetService service = new PasswordResetService(new InMemoryTokens(), new InMemoryUsers(), mailer);
+
+    @Test
+    void sendsResetLinkByEmail() {
+        service.sendResetLink("ana@shop.example");
+        verify(mailer).send(eq("ana@shop.example"), anyString(), contains("/reset?token="));
+    }
+}
`;

/** Couverture des critères d'acceptation : la PR fait-elle vraiment ce que la user story demande ? */
@Component({
  selector: 'app-criteres',
  standalone: true,
  imports: [TPipe, RouterLink, IconeComponent, ScoreRingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil"><a routerLink="/revues/nouvelle">RevIA</a> / <b>{{ 'Couverture des critères' | t }}</b></div>
      <button class="btn" type="button" (click)="exemple()"><app-icone nom="ampoule" />{{ 'Exemple' | t }}</button>
      <button class="btn primaire" type="button" (click)="verifier()" [disabled]="attente() || !valide()">
        @if (attente()) { <span class="spinner"></span> {{ 'Vérification…' | t }} } @else { <app-icone nom="criteres" />{{ 'Vérifier la couverture' | t }} }
      </button>
    </header>

    <div class="page">
      <h1 class="titre-page">{{ 'Couverture des critères d\\'acceptation' | t }}</h1>
      <p class="sous-titre">{{ 'La PR fait-elle vraiment ce que la user story demande ? Chaque critère est vérifié dans le code et les tests, avec preuves.' | t }}</p>

      <div class="grille">
        <section class="carte bloc" [attr.aria-label]="'Entrée' | t">
          <div class="champ">
            <label for="criteres">{{ 'User story / critères d\\'acceptation' | t }}
              <span class="meta">{{ criteres().length }} / {{ maxCriteres }}</span></label>
            <textarea id="criteres" class="input" rows="9" [attr.maxlength]="maxCriteres" [value]="criteres()"
                      (input)="criteres.set($any($event.target).value)"
                      [placeholder]="'Collez la user story et ses critères (puces ou scénarios Gherkin)' | t"></textarea>
          </div>
          <div class="champ">
            <label for="diff">{{ 'Diff de la pull request' | t }}
              <span class="meta" [class.trop]="nbLignesDiff() > maxLignes">{{ nbLignesDiff() }} / {{ maxLignes }} {{ 'lignes' | t }}</span></label>
            <textarea id="diff" class="input" rows="16" [attr.maxlength]="maxDiff" [value]="diff()" spellcheck="false"
                      (input)="diff.set($any($event.target).value)" [placeholder]="'Collez le diff de la PR' | t"></textarea>
            <span class="info">{{ 'Astuce :' | t }} <code>git diff main...HEAD</code> {{ 'donne le diff complet de votre branche.' | t }}</span>
          </div>
        </section>

        <section class="carte resultat" [attr.aria-label]="'Résultat' | t" aria-live="polite">
          @if (attente()) {
            <div class="vide"><span class="spinner sombre grand"></span><b>{{ 'L\\'IA vérifie chaque critère…' | t }}</b>
              <span>{{ 'Masquage des secrets, puis analyse du code et des tests.' | t }}</span></div>
          } @else if (erreur()) {
            <div class="erreur" role="alert"><b>{{ erreur()! | t }}</b>
              <button class="btn petit" type="button" (click)="verifier()">{{ 'Réessayer' | t }}</button></div>
          } @else if (resultat()) { @if (resultat(); as r) {
            <div class="synthese">
              <app-score-ring [score]="r.couverture" [taille]="110" />
              <div class="chiffres">
                <div class="compteurs">
                  <span class="badge s-corrigee">✅ {{ r.nbCouverts }} {{ 'couvert(s)' | t }}</span>
                  <span class="badge s-a_revoir">🟡 {{ r.nbPartiels }} {{ 'partiel(s)' | t }}</span>
                  <span class="badge s-a_corriger">❌ {{ r.nbNonCouverts }} {{ 'non couvert(s)' | t }}</span>
                </div>
                <p class="resume" dir="auto">{{ r.resume }}</p>
                <span class="infos">{{ r.fournisseur }} · {{ r.modele }} · {{ duree(r.dureeMs) }} s
                  @if (r.nbMasquages) { · 🔒 {{ r.nbMasquages }} {{ 'secret(s) masqué(s)' | t }} }</span>
              </div>
            </div>

            <ol class="liste">
              @for (c of r.criteres; track c.id) {
                <li class="critere" [class]="badge(c.statut).classe">
                  <div class="tete-critere">
                    <span class="id">{{ c.id }}</span>
                    <b dir="auto">{{ c.texte }}</b>
                    <span class="badge" [class]="badge(c.statut).classe">{{ badge(c.statut).icone }} {{ badge(c.statut).libelle | t }}</span>
                  </div>
                  @if (c.explication) { <p dir="auto">{{ c.explication }}</p> }
                  @if (c.preuves.length) {
                    <div class="sous">{{ 'Preuves' | t }}</div>
                    <ul class="preuves">@for (p of c.preuves; track $index) { <li dir="auto"><code>{{ p }}</code></li> }</ul>
                  }
                  @if (c.testManquant) {
                    <div class="manquant"><span class="sous">{{ 'Test manquant' | t }}</span><span dir="auto">{{ c.testManquant }}</span></div>
                  }
                </li>
              }
            </ol>
          } } @else {
            <div class="vide">
              <span class="grand">🎯</span>
              <b>{{ 'Verdict par critère, avec preuves' | t }}</b>
              <span>{{ 'Collez la user story et le diff de la PR (ou cliquez sur « Exemple »), puis « Vérifier la couverture ».' | t }}</span>
            </div>
          }
        </section>
      </div>
    </div>
  `,
  styles: [`
    .page { max-width: none; }
    .grille { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; align-items: start; }
    .bloc { padding: 18px; display: flex; flex-direction: column; gap: 14px; }
    .champ label { display: flex; justify-content: space-between; gap: 10px; }
    .meta { color: var(--texte-3); font-weight: 400; white-space: nowrap; }
    .meta.trop { color: var(--critique); font-weight: 600; }
    textarea.input { resize: vertical; width: 100%; }
    .info { font-size: 12px; color: var(--texte-3); }
    .info code { background: var(--primaire-pale); color: var(--primaire-fonce); border-radius: 5px; padding: 1px 5px; }
    .resultat { padding: 18px; min-height: 420px; max-height: calc(100vh - 150px); overflow-y: auto; }
    .vide { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; color: var(--texte-2); min-height: 380px; }
    .vide b { color: var(--texte); font-size: 15px; }
    .grand { font-size: 34px; }
    .erreur { background: var(--critique-pale); color: var(--critique); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
    .synthese { display: flex; gap: 18px; align-items: center; padding-bottom: 14px; border-bottom: 1px solid var(--bord); margin-bottom: 14px; }
    .chiffres { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
    .compteurs { display: flex; gap: 6px; flex-wrap: wrap; }
    .resume { margin: 0; font-size: 13.5px; line-height: 1.5; }
    .infos { font-size: 12px; color: var(--texte-3); }
    .liste { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    .critere { border: 1px solid var(--bord); border-left: 4px solid currentColor; border-radius: 10px; padding: 12px 14px; background: var(--surface) !important; }
    .critere p { margin: 8px 0 0; font-size: 13px; color: var(--texte-2); line-height: 1.5; }
    .tete-critere { display: flex; gap: 10px; align-items: flex-start; }
    .tete-critere b { flex: 1; font-size: 13.5px; color: var(--texte); line-height: 1.4; }
    .id { font: 600 11.5px var(--mono); color: var(--texte-3); padding-top: 2px; }
    .sous { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: var(--texte-3); font-weight: 700; margin: 10px 0 4px; }
    .preuves { margin: 0; padding-left: 18px; font-size: 12.5px; color: var(--texte); }
    .preuves code { font-size: 12px; overflow-wrap: anywhere; }
    .manquant { margin-top: 10px; background: var(--mineure-pale); color: var(--texte); border-radius: 8px; padding: 8px 10px; font-size: 13px; }
    .manquant .sous { margin-top: 0; color: #a47a00; }
    @media (max-width: 900px) {
      .grille { grid-template-columns: 1fr; }
      .resultat { max-height: none; }
      .synthese { flex-direction: column; align-items: flex-start; }
    }
  `],
})
export class CriteresComponent {
  private readonly api = inject(ApiService);
  private readonly l = inject(LangueService);

  readonly maxCriteres = MAX_CRITERES;
  readonly maxDiff = MAX_DIFF;
  readonly maxLignes = MAX_LIGNES_DIFF;
  readonly badge = badgeCritere;
  readonly criteres = signal('');
  readonly diff = signal('');
  readonly attente = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly resultat = signal<CouvertureCriteres | null>(null);
  readonly nbLignesDiff = computed(() => (this.diff() ? this.diff().split('\n').length : 0));
  readonly valide = computed(() => !!this.criteres().trim() && !!this.diff().trim() && this.nbLignesDiff() <= MAX_LIGNES_DIFF);

  exemple(): void {
    this.criteres.set(EXEMPLE_CRITERES);
    this.diff.set(EXEMPLE_DIFF);
    this.resultat.set(null);
    this.erreur.set(null);
  }

  verifier(): void {
    if (!this.valide() || this.attente()) {
      return;
    }
    this.attente.set(true);
    this.erreur.set(null);
    this.resultat.set(null);
    this.api.criteres({ criteres: this.criteres(), diff: this.diff() }).subscribe({
      next: (r) => {
        this.resultat.set(r);
        this.attente.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.attente.set(false);
      },
    });
  }

  duree(ms: number): string {
    const s = (ms / 1000).toFixed(1);
    return this.l.langue() === 'fr' ? s.replace('.', ',') : s;
  }
}
