import { CasDeTestFlowchart } from '../../core/models';
import { EXEMPLE_BPMN, EXEMPLE_MERMAID, formatFichier, lignesCasDeTest, PROBLEMES } from './flowchart-outils';

describe('flowchart-outils', () => {
  it('reconnaît le format d’un fichier par son extension, et un .xml par son contenu', () => {
    expect(formatFichier('photo.PNG')).toBe('image');
    expect(formatFichier('scan.jpeg')).toBe('image');
    expect(formatFichier('processus.bpmn')).toBe('bpmn');
    expect(formatFichier('schema.drawio')).toBe('drawio');
    expect(formatFichier('flux.mmd')).toBe('mermaid');
    expect(formatFichier('export.xml', '<mxfile host="x"><diagram/></mxfile>')).toBe('drawio');
    expect(formatFichier('export.xml', '<bpmn2:definitions xmlns:bpmn2="…">')).toBe('bpmn');
    expect(formatFichier('autre.xml', '<racine/>')).toBe('auto');
    expect(formatFichier('virus.exe')).toBeNull();
  });

  it('prépare le tableau CSV des cas de test (étapes numérotées, en-têtes traduits)', () => {
    const cas: CasDeTestFlowchart[] = [
      { id: 'TC-01', type: 'chemin', titre: 'Montant ? : Oui ⇒ Fin', etapes: ['Début', 'Montant ? [Oui]', 'Fin'], donnees: null, attendu: 'Fin', chemin: 'CH-01' },
      { id: 'TC-02', type: 'limite', titre: 'Valeur limite', etapes: ['Début'], donnees: 'Montant = 1000.01', attendu: 'Branche « Oui »', chemin: null },
    ];
    const fr = lignesCasDeTest(cas, false);
    expect(fr[0][3]).toBe('Étapes');
    expect(fr[1]).toEqual(['TC-01', 'chemin', 'Montant ? : Oui ⇒ Fin', '1. Début 2. Montant ? [Oui] 3. Fin', '', 'Fin', 'CH-01']);
    expect(fr[2][1]).toBe('valeur limite');
    expect(lignesCasDeTest(cas, true)[2][1]).toBe('boundary');
  });

  it('fournit deux exemples (Mermaid avec branche manquante, BPMN) et un libellé par type de problème', () => {
    expect(EXEMPLE_MERMAID.startsWith('flowchart TD')).toBeTrue();
    expect(EXEMPLE_MERMAID).toContain('G -->|Oui| F');
    expect(EXEMPLE_BPMN).toContain('<bpmn:parallelGateway');
    expect(EXEMPLE_BPMN).not.toContain('DOCTYPE');
    expect(PROBLEMES['branche_manquante'].en).toBe('Missing branch');
  });
});
