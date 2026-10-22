import { CasDeTestFlowchart } from '../../core/models';

/** Limites de l'API (miroir de la validation côté serveur). */
export const MAX_SOURCE = 200000;
export const MAX_IMAGE = 4 * 1024 * 1024;

export type FormatFichier = 'image' | 'mermaid' | 'bpmn' | 'drawio' | 'auto';

/**
 * Format d'un fichier choisi dans le sélecteur : par extension, et pour un « .xml » par son contenu
 * (draw.io : <mxfile> / <mxGraphModel> ; BPMN : <definitions>). {@code null} : fichier refusé.
 */
export function formatFichier(nom: string, contenu = ''): FormatFichier | null {
  const ext = (nom.split('.').pop() ?? '').toLowerCase();
  if (['png', 'jpg', 'jpeg', 'webp'].includes(ext)) return 'image';
  if (ext === 'bpmn') return 'bpmn';
  if (ext === 'drawio' || ext === 'dio') return 'drawio';
  if (['mmd', 'mermaid', 'md', 'txt'].includes(ext)) return 'mermaid';
  if (ext === 'xml') {
    if (/<(mxfile|mxGraphModel)\b/i.test(contenu)) return 'drawio';
    if (/<([\w-]+:)?definitions\b/i.test(contenu)) return 'bpmn';
    return 'auto';
  }
  return null;
}

/** Tableau des cas de test prêt pour l'export CSV (en-têtes dans la langue de l'interface). */
export function lignesCasDeTest(cas: CasDeTestFlowchart[], en: boolean): string[][] {
  const entete = en
    ? ['ID', 'Type', 'Title', 'Steps', 'Test data', 'Expected result', 'Path']
    : ['ID', 'Type', 'Titre', 'Étapes', 'Données de test', 'Résultat attendu', 'Chemin'];
  return [
    entete,
    ...cas.map((c) => [
      c.id,
      c.type === 'limite' ? (en ? 'boundary' : 'valeur limite') : (en ? 'path' : 'chemin'),
      c.titre,
      c.etapes.map((e, i) => `${i + 1}. ${e}`).join(' '),
      c.donnees ?? '',
      c.attendu,
      c.chemin ?? '',
    ]),
  ];
}

/** Libellé et icône d'un type de problème renvoyé par l'analyse. */
export const PROBLEMES: Record<string, { fr: string; en: string; icone: string }> = {
  branche_manquante: { fr: 'Branche manquante', en: 'Missing branch', icone: '🔀' },
  impasse: { fr: 'Impasse', en: 'Dead end', icone: '⛔' },
  inaccessible: { fr: 'Étape inaccessible', en: 'Unreachable step', icone: '🏝️' },
  boucle_sans_sortie: { fr: 'Boucle sans sortie', en: 'Loop without exit', icone: '♾️' },
  condition_absente: { fr: 'Condition non écrite', en: 'Unlabelled branch', icone: '❔' },
  parallele: { fr: 'Branches parallèles', en: 'Parallel branches', icone: '⏸️' },
  debut_absent: { fr: 'Début absent', en: 'No start', icone: '🚩' },
  fin_absente: { fr: 'Fin absente', en: 'No end', icone: '🏁' },
  chemins_tronques: { fr: 'Chemins tronqués', en: 'Paths truncated', icone: '✂️' },
};

/** Exemple 1 : déclaration de sinistre (assurance), la décision « Dommage couvert ? » n'a pas de branche « Non ». */
export const EXEMPLE_MERMAID = `flowchart TD
    A([Déclaration du sinistre]) --> B{Contrat en vigueur à la date du sinistre ?}
    B -->|Non| R([Refus notifié à l'assuré])
    B -->|Oui| C[Instruction du dossier]
    C --> D{Montant > 1000 ?}
    D -->|Oui| E[Expertise sur place]
    D -->|Non| F[Règlement direct]
    E --> G{Dommage couvert ?}
    G -->|Oui| F
    F --> H([Sinistre clos])`;

/** Exemple 2 : paiement (BPMN 2.0), authentification forte au-delà de 30 €, débit et notification en parallèle. */
export const EXEMPLE_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="paiement-carte" targetNamespace="http://revia.local/bpmn">
  <bpmn:process id="paiement" name="Paiement par carte" isExecutable="false">
    <bpmn:startEvent id="debut" name="Ordre de paiement reçu"/>
    <bpmn:serviceTask id="controle" name="Contrôler le solde et le plafond"/>
    <bpmn:exclusiveGateway id="g_solde" name="Solde suffisant ?" default="f_refus"/>
    <bpmn:exclusiveGateway id="g_sca" name="Authentification forte requise ?"/>
    <bpmn:userTask id="sca" name="Authentification forte (SCA)"/>
    <bpmn:exclusiveGateway id="g_sca_ok" name="SCA réussie ?"/>
    <bpmn:parallelGateway id="p_debut"/>
    <bpmn:serviceTask id="debiter" name="Débiter le compte"/>
    <bpmn:serviceTask id="notifier" name="Notifier le client"/>
    <bpmn:parallelGateway id="p_fin"/>
    <bpmn:endEvent id="fin_ok" name="Paiement exécuté"/>
    <bpmn:endEvent id="fin_refus" name="Paiement refusé"/>
    <bpmn:sequenceFlow id="f1" sourceRef="debut" targetRef="controle"/>
    <bpmn:sequenceFlow id="f2" sourceRef="controle" targetRef="g_solde"/>
    <bpmn:sequenceFlow id="f_oui" name="Oui" sourceRef="g_solde" targetRef="g_sca"/>
    <bpmn:sequenceFlow id="f_refus" sourceRef="g_solde" targetRef="fin_refus"/>
    <bpmn:sequenceFlow id="f3" sourceRef="g_sca" targetRef="sca">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">\${montant &gt; 30}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="f4" name="montant &lt;= 30" sourceRef="g_sca" targetRef="p_debut"/>
    <bpmn:sequenceFlow id="f5" sourceRef="sca" targetRef="g_sca_ok"/>
    <bpmn:sequenceFlow id="f6" name="Oui" sourceRef="g_sca_ok" targetRef="p_debut"/>
    <bpmn:sequenceFlow id="f7" sourceRef="p_debut" targetRef="debiter"/>
    <bpmn:sequenceFlow id="f8" sourceRef="p_debut" targetRef="notifier"/>
    <bpmn:sequenceFlow id="f9" sourceRef="debiter" targetRef="p_fin"/>
    <bpmn:sequenceFlow id="f10" sourceRef="notifier" targetRef="p_fin"/>
    <bpmn:sequenceFlow id="f11" sourceRef="p_fin" targetRef="fin_ok"/>
  </bpmn:process>
</bpmn:definitions>`;
