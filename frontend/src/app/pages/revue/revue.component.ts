import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/http';
import { CATEGORIES, EXTENSIONS, GRAVITES_PLURIEL, LANGAGES, MODES, libelleLangage } from '../../core/libelles';
import { Categorie, Gravite, Probleme, RevueDetail } from '../../core/models';
import { CategorieBadgeComponent, GraviteBadgeComponent } from '../../shared/badges';
import { CodeEditorComponent } from '../../shared/code-editor.component';
import { IconeComponent } from '../../shared/icone.component';
import { ScoreRingComponent } from '../../shared/score-ring.component';

const CLE_BROUILLON = 'revia.brouillon';
const MAX_LIGNES = 2000;
const ETAPES = [
  'Préparation : langage détecté, lignes numérotées…',
  'Masquage des mots de passe, clés et e-mails…',
  "Appel au modèle d'IA (température 0,2)…",
  'Validation de la réponse JSON…',
  'Enregistrement de la revue…',
];

type Filtre = 'tous' | Categorie;

/** Écran de revue (maquette diapo 12) : 1. l'éditeur, 2. score & résumé, 3. les problèmes. */
@Component({
  selector: 'app-revue',
  standalone: true,
  imports: [FormsModule, RouterLink, DecimalPipe, CodeEditorComponent, ScoreRingComponent, GraviteBadgeComponent,
    CategorieBadgeComponent, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barre">
      <div class="fil">
        <a routerLink="/historique">Revues</a> /
        <b>{{ revue()?.titre || titre() || 'Nouvelle revue' }}</b>
      </div>
      <select class="input petit-select" [ngModel]="langage()" (ngModelChange)="langage.set($event)" aria-label="Langage">
        @for (l of langages; track l.valeur) { <option [value]="l.valeur">{{ l.icone }}  {{ l.libelle }}</option> }
      </select>
      <select class="input petit-select" [ngModel]="mode()" (ngModelChange)="mode.set($event)" aria-label="Mode d'analyse">
        @for (m of modes; track m.valeur) { <option [value]="m.valeur">Mode : {{ m.libelle }}</option> }
      </select>
      <button class="btn" type="button" (click)="fichier.click()"><app-icone nom="import" />Importer un fichier</button>
      <input #fichier type="file" hidden [accept]="acceptes" (change)="importer($event)" />
      <button class="btn primaire" type="button" (click)="lancer()" [disabled]="analyse()">
        @if (analyse()) { <span class="spinner"></span> Analyse… } @else { <app-icone nom="relancer" />{{ revue() ? 'Relancer la revue' : 'Lancer la revue' }} }
      </button>
    </header>

    <div class="page grille">
      <!-- 1. L'éditeur -->
      <section class="editeur-carte" aria-label="Éditeur de code">
        <div class="tete">
          <input class="nom-fichier" [ngModel]="titre()" (ngModelChange)="titre.set($event)" placeholder="NomDuFichier.java" maxlength="160"
                 aria-label="Titre de l'extrait" />
          <span class="meta">{{ nbLignes() }} lignes · UTF-8</span>
        </div>
        <div class="zone">
          <app-code-editor [code]="code()" (codeChange)="changerCode($event)" [langage]="langageEditeur()"
                           [problemes]="problemesVisibles()" [ligneActive]="ligneActive()" (curseur)="curseur.set($event)" />
        </div>
        <div class="pied">
          @if (revue(); as r) {
            @for (g of gravitesAffichees; track g) {
              @if (r.parGravite[g]) { <span class="cpt" [class]="'c-' + g"><i></i>{{ r.parGravite[g] }} {{ pluriel[g] }}</span> }
            }
          } @else {
            <span>Collez votre code puis lancez la revue</span>
          }
          <span class="pos">Ln {{ curseur().ligne }}, Col {{ curseur().colonne }}</span>
        </div>
      </section>

      <section class="droite">
        @if (erreur()) {
          <div class="carte erreur" role="alert">
            <b>{{ erreur() }}</b>
            <span>Votre code est conservé : vous pouvez relancer l'analyse.</span>
            <button class="btn petit" type="button" (click)="lancer()" [disabled]="analyse()">Réessayer</button>
          </div>
        }

        @if (analyse()) {
          <div class="carte attente">
            <span class="spinner sombre grand"></span>
            <div>
              <b>L'IA relit votre code…</b>
              <ol>
                @for (e of etapes; track e; let i = $index) {
                  <li [class.faite]="i < etape()" [class.encours]="i === etape()">{{ e }}</li>
                }
              </ol>
            </div>
          </div>
        } @else if (revue()) { @if (revue(); as r) {
          <!-- 2. Score & résumé -->
          <div class="carte resume">
            <app-score-ring [score]="r.score" [taille]="92" />
            <div class="texte">
              <h2>{{ verdict() }}</h2>
              <p>{{ r.resume }}</p>
              <div class="pastilles">
                @for (g of gravitesAffichees; track g) {
                  @if (r.parGravite[g]) { <span class="badge" [class]="'g-' + g">{{ r.parGravite[g] }} {{ pluriel[g] }}</span> }
                }
                @if (r.nbMasquages) { <span class="badge neutre" title="Valeurs remplacées par ***MASQUÉ*** avant l'envoi au modèle">🔒 {{ r.nbMasquages }} secret(s) masqué(s)</span> }
              </div>
            </div>
          </div>

          <!-- 3. Les problèmes -->
          <div class="filtres" role="tablist">
            <button type="button" role="tab" [class.actif]="filtre() === 'tous'" (click)="filtre.set('tous')">Tous · {{ actifs().length }}</button>
            @for (c of categories; track c) {
              @if (compte(c)) {
                <button type="button" role="tab" [class.actif]="filtre() === c" (click)="filtre.set(c)">{{ libellesCat[c] }} · {{ compte(c) }}</button>
              }
            }
            <span class="duree">Analyse en {{ r.dureeMs / 1000 | number: '1.1-1' }} s · {{ r.modele }}</span>
          </div>

          <div class="liste">
            @for (p of problemesFiltres(); track p.id; let i = $index) {
              <article class="carte probleme" [class.fp]="p.fauxPositif" [class.selection]="ligneActive() === p.ligne" (click)="ligneActive.set(p.ligne)">
                <div class="entete">
                  <app-gravite [valeur]="p.gravite" />
                  <app-categorie [valeur]="p.categorie" />
                  @if (p.corrige) { <span class="badge s-corrigee">✓ Corrigé</span> }
                  @if (p.fauxPositif) { <span class="badge neutre">Faux positif</span> }
                  <span class="ligne">Ligne {{ p.ligne }}</span>
                </div>
                <h3>{{ p.titre }}</h3>
                <p>{{ p.explication }}</p>
                <div class="actions">
                  @if (p.correction) {
                    <a [routerLink]="['/revues', r.id, 'problemes', indexDe(p)]" (click)="$event.stopPropagation()">Voir la correction →</a>
                  } @else {
                    <span></span>
                  }
                  <button type="button" class="btn lien discret" (click)="basculerFauxPositif(p); $event.stopPropagation()">
                    {{ p.fauxPositif ? 'Annuler le faux positif' : 'Faux positif ?' }}
                  </button>
                </div>
              </article>
            } @empty {
              <div class="carte vide">
                @if (actifs().length === 0 && filtre() === 'tous') {
                  <div class="grand">🎉</div><b>Aucun problème détecté.</b><br />Le code semble sain sur les points vérifiés.
                } @else {
                  Aucun problème dans cette catégorie.
                }
              </div>
            }
          </div>
          @if (r.problemesRejetes) {
            <p class="note">{{ r.problemesRejetes }} remarque(s) de l'IA écartée(s) à la validation (ligne inexistante).</p>
          }
        } } @else if (!erreur()) {
          <div class="carte accueil">
            <div class="sur-titre">Comment ça marche</div>
            <h2>Une IA qui relit votre code comme un développeur senior</h2>
            <ol class="etapes-accueil">
              <li><span>1</span><div><b>Coller le code</b> ou importer un fichier (1 à 2 000 lignes, 12 langages).</div></li>
              <li><span>2</span><div><b>L'IA analyse</b> : bugs, mauvaises pratiques, failles OWASP, classés par gravité.</div></li>
              <li><span>3</span><div><b>Corriger</b> : diff avant / après et le pourquoi du changement.</div></li>
            </ol>
            <p class="confiance"><app-icone nom="bouclier" /> Mots de passe, clés d'API et e-mails sont masqués avant tout envoi au modèle.</p>
            <button class="btn" type="button" (click)="exemple()">Essayer avec un exemple</button>
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    .petit-select { height: 34px; font-size: 13px; padding: 0 8px; max-width: 220px; }
    .grille { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr); gap: 18px; align-items: start; max-width: none; }
    .editeur-carte {
      background: #111427; border-radius: var(--rayon); overflow: hidden; border: 1px solid #1f2444;
      position: sticky; top: 76px; display: flex; flex-direction: column; height: calc(100vh - 110px); min-height: 460px;
    }
    .tete { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border-bottom: 1px solid #1f2444; gap: 10px; }
    .nom-fichier {
      background: #1b1f3d; border: 1px solid #262b52; color: #e5e7ff; border-radius: 7px; padding: 5px 10px; font: 12px var(--mono);
      min-width: 0; width: 260px; outline: none;
    }
    .nom-fichier:focus { border-color: var(--primaire); }
    .meta { color: #7d84ad; font-size: 11.5px; white-space: nowrap; }
    .zone { flex: 1; min-height: 0; }
    .pied { display: flex; gap: 14px; align-items: center; padding: 8px 12px; border-top: 1px solid #1f2444; color: #7d84ad; font-size: 11.5px; }
    .pos { margin-left: auto; }
    .cpt { display: inline-flex; align-items: center; gap: 5px; }
    .cpt i { width: 7px; height: 7px; border-radius: 50%; }
    .c-critique i { background: #ef4444; } .c-majeure i { background: #f97316; } .c-mineure i { background: #eab308; } .c-info i { background: #60a5fa; }
    .droite { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
    .resume { display: flex; gap: 18px; padding: 18px; align-items: center; }
    .resume h2 { font-size: 16px; margin-bottom: 6px; }
    .resume p { margin: 0 0 10px; color: var(--texte-2); font-size: 13px; line-height: 1.5; }
    .pastilles { display: flex; gap: 6px; flex-wrap: wrap; }
    .filtres { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
    .filtres button {
      height: 30px; padding: 0 12px; border-radius: 8px; border: 1px solid var(--bord); background: var(--surface);
      font: 600 12.5px var(--police); color: var(--texte-2); cursor: pointer;
    }
    .filtres button.actif { background: var(--texte); color: #fff; border-color: var(--texte); }
    .duree { margin-left: auto; font-size: 11.5px; color: var(--texte-3); }
    .liste { display: flex; flex-direction: column; gap: 10px; }
    .probleme { padding: 14px 16px; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
    .probleme:hover { border-color: #c9ccf8; }
    .probleme.selection { border-color: var(--primaire); box-shadow: 0 0 0 3px var(--primaire-pale); }
    .probleme.fp { opacity: .6; }
    .probleme.fp h3 { text-decoration: line-through; }
    .entete { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .ligne { margin-left: auto; font-size: 12px; color: var(--texte-3); }
    .probleme h3 { font-size: 14.5px; margin: 8px 0 4px; }
    .probleme p { margin: 0; color: var(--texte-2); font-size: 13px; line-height: 1.5; }
    .actions { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; font-size: 12.5px; font-weight: 600; }
    .discret { color: var(--texte-3); font-weight: 500; font-size: 12.5px; }
    .erreur { padding: 14px 16px; border-color: #f6c4c5; background: #fff7f7; display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
    .erreur b { color: var(--critique); }
    .erreur span { color: var(--texte-2); font-size: 13px; }
    .attente { display: flex; gap: 16px; padding: 22px; align-items: flex-start; }
    .spinner.grand { width: 26px; height: 26px; border-width: 3px; flex-shrink: 0; }
    .attente ol { margin: 10px 0 0; padding-left: 18px; color: var(--texte-3); font-size: 13px; line-height: 1.9; }
    .attente li.faite { color: var(--ok); }
    .attente li.encours { color: var(--texte); font-weight: 600; }
    .accueil { padding: 24px; }
    .accueil h2 { font-size: 19px; margin: 6px 0 16px; }
    .etapes-accueil { list-style: none; padding: 0; margin: 0 0 16px; display: flex; flex-direction: column; gap: 12px; }
    .etapes-accueil li { display: flex; gap: 12px; align-items: flex-start; font-size: 13.5px; color: var(--texte-2); }
    .etapes-accueil span {
      width: 26px; height: 26px; border-radius: 50%; background: var(--primaire); color: #fff; display: grid; place-items: center;
      font-weight: 700; flex-shrink: 0; font-size: 12px;
    }
    .etapes-accueil b { color: var(--texte); }
    .confiance { display: flex; gap: 8px; align-items: center; color: var(--texte-2); font-size: 12.5px; background: var(--primaire-pale); padding: 10px 12px; border-radius: 9px; }
    .note { font-size: 12px; color: var(--texte-3); margin: 0; }
    @media (max-width: 1100px) {
      .grille { grid-template-columns: 1fr; }
      .editeur-carte { position: static; height: 60vh; }
    }
  `],
})
export class RevueComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Paramètre de route /revues/:id (absent pour /revues/nouvelle). */
  readonly id = input<string | undefined>(undefined);

  readonly langages = LANGAGES;
  readonly modes = MODES;
  readonly categories: Categorie[] = ['securite', 'bug', 'bonne_pratique', 'performance'];
  readonly libellesCat = CATEGORIES;
  readonly pluriel = GRAVITES_PLURIEL;
  readonly gravitesAffichees: Gravite[] = ['critique', 'majeure', 'mineure', 'info'];
  readonly etapes = ETAPES;
  readonly acceptes = Object.keys(EXTENSIONS).map((e) => '.' + e).join(',') + ',.txt';

  readonly code = signal('');
  readonly titre = signal('');
  readonly langage = signal('auto');
  readonly mode = signal('complet');
  readonly revue = signal<RevueDetail | null>(null);
  readonly analyse = signal(false);
  readonly etape = signal(0);
  readonly erreur = signal<string | null>(null);
  readonly filtre = signal<Filtre>('tous');
  readonly ligneActive = signal<number | null>(null);
  readonly curseur = signal({ ligne: 1, colonne: 1 });
  private minuteur: ReturnType<typeof setInterval> | null = null;

  readonly nbLignes = computed(() => (this.code() ? this.code().split(/\r?\n/).length : 0));
  readonly langageEditeur = computed(() => (this.langage() === 'auto' ? this.revue()?.langage ?? 'plaintext' : this.langage()));
  readonly actifs = computed(() => (this.revue()?.problemes ?? []).filter((p) => !p.fauxPositif));
  readonly problemesVisibles = computed(() => (this.code() === this.revue()?.code ? this.revue()?.problemes ?? [] : []));
  readonly problemesFiltres = computed(() => {
    const f = this.filtre();
    const tous = this.revue()?.problemes ?? [];
    return f === 'tous' ? tous : tous.filter((p) => p.categorie === f);
  });
  readonly verdict = computed(() => {
    const r = this.revue();
    if (!r) return '';
    if (r.statut === 'corrigee' && this.actifs().length === 0) return 'Prêt pour la fusion';
    if (r.statut === 'corrigee') return 'Problèmes corrigés';
    return r.statut === 'a_corriger' ? 'À corriger avant fusion' : 'À revoir avant fusion';
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) {
        this.charger(Number(id));
      } else {
        this.revue.set(null);
        this.restaurerBrouillon();
      }
    }, { allowSignalWrites: true });
  }

  compte(c: Categorie): number {
    return this.actifs().filter((p) => p.categorie === c).length;
  }

  indexDe(p: Probleme): number {
    return (this.revue()?.problemes ?? []).indexOf(p) + 1;
  }

  changerCode(v: string): void {
    this.code.set(v);
    if (!this.id()) {
      this.sauverBrouillon();
    }
  }

  lancer(): void {
    const code = this.code();
    const lignes = code.split(/\r?\n/).length;
    if (!code.trim() || lignes > MAX_LIGNES) {
      this.erreur.set('Collez entre 1 et 2 000 lignes de code.');
      return;
    }
    const precedente = this.revue();
    let titre = this.titre().trim();
    if (precedente && code !== precedente.code && titre === precedente.titre && !/après correction/.test(titre)) {
      titre = `${titre} (après correction)`;
    }
    this.erreur.set(null);
    this.analyse.set(true);
    this.etape.set(0);
    this.minuteur = setInterval(() => this.etape.update((e) => Math.min(e + 1, ETAPES.length - 1)), 900);
    this.api.soumettre({ titre: titre || undefined, langage: this.langage(), code, mode: this.mode() }).subscribe({
      next: (r) => {
        this.arreter();
        try { localStorage.removeItem(CLE_BROUILLON); } catch { /* ignoré */ }
        this.revue.set(r);
        this.auth.rafraichir().subscribe();
        this.router.navigate(['/revues', r.id], { replaceUrl: !precedente });
      },
      error: (e) => {
        this.arreter();
        this.erreur.set(messageErreur(e));
      },
    });
  }

  importer(ev: Event): void {
    const champ = ev.target as HTMLInputElement;
    const f = champ.files?.[0];
    champ.value = '';
    if (!f) return;
    if (f.size > 500_000) {
      this.erreur.set('Fichier trop volumineux (500 Ko maximum).');
      return;
    }
    f.text().then((texte) => {
      const ext = f.name.split('.').pop()?.toLowerCase() ?? '';
      this.revue.set(null);
      this.code.set(texte);
      this.titre.set(f.name);
      this.langage.set(EXTENSIONS[ext] ?? 'auto');
      this.erreur.set(null);
      if (this.id()) {
        this.router.navigate(['/revues/nouvelle']);
      }
      this.sauverBrouillon();
    });
  }

  exemple(): void {
    this.titre.set('UserService.java');
    this.langage.set('java');
    this.code.set(`public class UserService {

    private static final String DB_PASSWORD = "admin123";

    public User findUser(String username) throws SQLException {
        Connection conn = DriverManager.getConnection(URL, "root", DB_PASSWORD);
        Statement stmt = conn.createStatement();
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        ResultSet rs = stmt.executeQuery(sql);
        if (rs.next()) {
            return new User(rs.getString("username"), rs.getString("email"));
        }
        return null;
    }

    public double moyenneAge(List<User> users) {
        int total = 0;
        for (int i = 0; i <= users.size(); i++) {
            total += users.get(i).getAge();
        }
        return total / users.size();
    }
}`);
    this.sauverBrouillon();
  }

  basculerFauxPositif(p: Probleme): void {
    this.api.fauxPositif(p.id, !p.fauxPositif).subscribe({
      next: () => this.charger(this.revue()!.id, false),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private charger(id: number, remplacerCode = true): void {
    this.api.revue(id).subscribe({
      next: (r) => {
        this.revue.set(r);
        if (remplacerCode) {
          this.code.set(r.code);
          this.titre.set(r.titre);
          this.langage.set(r.langage === 'autre' ? 'auto' : r.langage);
          this.mode.set(r.mode);
          this.filtre.set('tous');
          this.erreur.set(null);
        }
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private arreter(): void {
    this.analyse.set(false);
    if (this.minuteur) {
      clearInterval(this.minuteur);
      this.minuteur = null;
    }
  }

  private sauverBrouillon(): void {
    try {
      localStorage.setItem(CLE_BROUILLON, JSON.stringify({ code: this.code(), titre: this.titre(), langage: this.langage() }));
    } catch { /* stockage indisponible */ }
  }

  private restaurerBrouillon(): void {
    try {
      const b = JSON.parse(localStorage.getItem(CLE_BROUILLON) ?? 'null');
      if (b && typeof b.code === 'string') {
        this.code.set(b.code);
        this.titre.set(b.titre ?? '');
        this.langage.set(b.langage ?? 'auto');
        return;
      }
    } catch { /* brouillon illisible */ }
    this.code.set('');
    this.titre.set('');
  }

  ngOnDestroy(): void {
    this.arreter();
  }

  protected readonly libelleLangage = libelleLangage;
}
