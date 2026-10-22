import { Injectable, computed, signal } from '@angular/core';

export type Langue = 'fr' | 'en';

/** Langues dans lesquelles l'IA peut rédiger ses rapports et réponses (l'interface, elle, reste FR / EN). */
export type LangueIa = 'fr' | 'en' | 'es' | 'de' | 'pt' | 'it' | 'nl' | 'pl' | 'tr' | 'ar' | 'ru' | 'hi' | 'zh' | 'ja' | 'ko';

/** Noms natifs, pour que chacun reconnaisse sa langue sans traduction. */
export const LANGUES_IA: { code: LangueIa; nom: string }[] = [
  { code: 'fr', nom: 'Français' }, { code: 'en', nom: 'English' }, { code: 'es', nom: 'Español' },
  { code: 'de', nom: 'Deutsch' }, { code: 'pt', nom: 'Português' }, { code: 'it', nom: 'Italiano' },
  { code: 'nl', nom: 'Nederlands' }, { code: 'pl', nom: 'Polski' }, { code: 'tr', nom: 'Türkçe' },
  { code: 'ar', nom: 'العربية' }, { code: 'ru', nom: 'Русский' }, { code: 'hi', nom: 'हिन्दी' },
  { code: 'zh', nom: '中文' }, { code: 'ja', nom: '日本語' }, { code: 'ko', nom: '한국어' },
];

const CLE = 'revia.langue';
const CLE_IA = 'revia.langue-ia';

/** Langue envoyée à l'API : le choix explicite, ou la langue de l'interface en mode « auto ». */
export function langueReponses(ia: LangueIa | 'auto', ui: Langue): LangueIa {
  return ia === 'auto' ? ui : ia;
}

/**
 * Langue de l'interface (FR / EN) et, séparément, langue des réponses de l'IA (15 langues).
 * Toutes deux mémorisées dans le navigateur ; la seconde (ou, à défaut, la première) est
 * envoyée à l'API dans l'en-tête X-RevIA-Langue.
 */
@Injectable({ providedIn: 'root' })
export class LangueService {
  readonly langue = langueCourante;
  /** Langue des réponses de l'IA ('auto' = suivre la langue de l'interface). */
  readonly langueIa = signal<LangueIa | 'auto'>(LangueService.iaInitiale());
  readonly langueReponses = computed(() => langueReponses(this.langueIa(), this.langue()));

  static initiale(): Langue {
    try {
      const memo = localStorage.getItem(CLE);
      if (memo === 'fr' || memo === 'en') {
        return memo;
      }
    } catch {
      /* stockage indisponible */
    }
    return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
  }

  static iaInitiale(): LangueIa | 'auto' {
    try {
      const memo = localStorage.getItem(CLE_IA);
      if (memo && LANGUES_IA.some((l) => l.code === memo)) {
        return memo as LangueIa;
      }
    } catch {
      /* stockage indisponible */
    }
    return 'auto';
  }

  definirLangueIa(l: LangueIa | 'auto'): void {
    this.langueIa.set(l);
    try {
      if (l === 'auto') {
        localStorage.removeItem(CLE_IA);
      } else {
        localStorage.setItem(CLE_IA, l);
      }
    } catch {
      /* ignoré */
    }
  }

  definir(l: Langue): void {
    this.langue.set(l);
    try {
      localStorage.setItem(CLE, l);
    } catch {
      /* ignoré */
    }
    document.documentElement.lang = l;
  }

  basculer(): void {
    this.definir(this.langue() === 'fr' ? 'en' : 'fr');
  }

  /** Choisit le texte dans la langue courante. */
  t<T>(fr: T, en: T): T {
    return this.langue() === 'en' ? en : fr;
  }
}

/** Signal partagé (aussi lu par la fonction {@link tr} et le pipe « t », hors injection). */
export const langueCourante = signal<Langue>(LangueService.initiale());
