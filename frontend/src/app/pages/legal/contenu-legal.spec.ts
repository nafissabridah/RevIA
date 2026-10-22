import { tr } from '../../core/i18n';
import { langueCourante } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';
import { DOCS_LEGAUX, SOUS_TRAITANTS, documentLegal } from './contenu-legal';

describe('pages légales', () => {
  const initiale = langueCourante();
  afterEach(() => langueCourante.set(initiale));

  it('chaque document existe en français et en anglais', () => {
    for (const d of DOCS_LEGAUX) {
      for (const l of ['fr', 'en'] as const) {
        const doc = documentLegal(d, l);
        expect(doc.titre).toBeTruthy();
        expect(doc.sections.length).toBeGreaterThan(0);
      }
    }
    expect(documentLegal('cgu', 'fr').titre).not.toBe(documentLegal('cgu', 'en').titre);
  });

  it('reprend les informations de la société depuis marque.ts', () => {
    const texte = JSON.stringify(documentLegal('mentions-legales', 'fr'));
    expect(texte).toContain(MARQUE.legal.raisonSociale);
    expect(texte).toContain(MARQUE.legal.hebergeur);
  });

  it('décrit le fonctionnement réel : fournisseurs LLM, entraînement des offres gratuites, Paddle, Ollama', () => {
    const conf = JSON.stringify(documentLegal('confidentialite', 'fr'));
    expect(conf).toContain('Gemini');
    expect(conf).toContain('améliorer leurs modèles');
    expect(conf).toContain('Ollama');
    expect(JSON.stringify(documentLegal('cgu', 'en'))).toContain('merchant of record');
    const noms = SOUS_TRAITANTS.map((s) => s.nom).join(' ');
    for (const n of ['Google', 'Groq', 'OpenRouter', 'Mistral', 'Paddle']) {
      expect(noms).toContain(n);
    }
  });

  it('traduit les libellés de l’abonnement et du RGPD', () => {
    langueCourante.set('en');
    expect(tr('Passer à Pro')).toBe('Upgrade to Pro');
    expect(tr('Exporter mes données')).toBe('Export my data');
    expect(tr('Modèle à faire valider par un juriste')).toBe('Template to be reviewed by a lawyer');
  });
});
