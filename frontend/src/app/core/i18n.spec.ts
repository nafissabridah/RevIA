import { TPipe, tr } from './i18n';
import { langueCourante } from './langue.service';

describe('i18n', () => {
  const initiale = langueCourante();
  afterEach(() => langueCourante.set(initiale));

  it('laisse le français tel quel', () => {
    langueCourante.set('fr');
    expect(tr('Nouvelle revue')).toBe('Nouvelle revue');
  });

  it('traduit en anglais, y compris les messages de l’API', () => {
    langueCourante.set('en');
    expect(tr('Nouvelle revue')).toBe('New review');
    expect(tr("Le service d'analyse est très sollicité. Réessayez dans une minute.")).toBe('The analysis service is very busy. Try again in a minute.');
  });

  it('traduit les messages variables (quota) et garde un texte inconnu', () => {
    langueCourante.set('en');
    expect(tr('Vous avez atteint vos 12 revues du jour.')).toBe('You have reached your 12 reviews for today.');
    expect(tr('Texte sans traduction')).toBe('Texte sans traduction');
    expect(tr(null)).toBe('');
  });

  it('traduit la page de couverture des critères et le choix de la langue de l’IA', () => {
    langueCourante.set('en');
    expect(tr('Vérifier la couverture')).toBe('Check coverage');
    expect(tr('Non couvert')).toBe('Not covered');
    expect(tr("Langue des réponses de l'IA")).toBe('AI answer language');
  });

  it('traduit les messages de l’API du flowchart et des packs métier, y compris les messages variables', () => {
    langueCourante.set('en');
    expect(tr("Ce fournisseur d'IA ne sait pas lire les images : collez plutôt le diagramme en Mermaid, BPMN ou draw.io."))
      .toBe('This AI provider cannot read images: paste the diagram as Mermaid, BPMN or draw.io instead.');
    expect(tr('Métier inconnu : astrologie.')).toBe('Unknown industry: astrologie.');
    expect(tr('Mermaid illisible ligne 2 : lien attendu.')).toBe('Unreadable Mermaid on line 2: lien attendu.');
  });

  it('le pipe « t » suit la langue courante', () => {
    const pipe = new TPipe();
    langueCourante.set('en');
    expect(pipe.transform('Historique')).toBe('History');
    langueCourante.set('fr');
    expect(pipe.transform('Historique')).toBe('Historique');
  });
});
