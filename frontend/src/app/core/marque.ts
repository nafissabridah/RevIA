/**
 * Informations commerciales et légales affichées sur la page d'accueil et dans les pages /legal/*.
 * ⚠️ À personnaliser avant la mise en ligne : adresse de contact commerciale, prix et TOUTES les valeurs
 * marquées « À COMPLÉTER » ci-dessous (elles apparaissent telles quelles dans les CGU, la politique de
 * confidentialité, les mentions légales et le DPA).
 */
export const MARQUE = {
  contact: 'contact@revia.dev',
  depot: 'https://github.com/nafissabridah/RevIA',
  /** Prix affichés (HT/TTC selon le pays : Paddle calcule et affiche la TVA au moment du paiement). */
  prix: { pro: 9, equipe: 19, devise: '€' },

  legal: {
    /**
     * Passer à true UNIQUEMENT après relecture des pages légales par un juriste : le bandeau
     * « Modèle à faire valider par un juriste » disparaît alors de toutes les pages /legal/*.
     */
    valideParJuriste: false,
    /** Date de dernière mise à jour affichée en tête des documents (AAAA-MM-JJ). */
    miseAJour: '2026-10-22',
    raisonSociale: '[À COMPLÉTER : raison sociale]',
    formeJuridique: '[À COMPLÉTER : forme juridique et capital social]',
    adresse: '[À COMPLÉTER : adresse du siège social]',
    immatriculation: '[À COMPLÉTER : n° d\'immatriculation (RCS / SIREN ou équivalent)]',
    tva: '[À COMPLÉTER : n° de TVA intracommunautaire]',
    directeurPublication: '[À COMPLÉTER : nom du directeur de la publication]',
    emailContact: '[À COMPLÉTER : e-mail de contact]',
    emailDpo: '[À COMPLÉTER : e-mail pour les demandes RGPD / DPO]',
    hebergeur: '[À COMPLÉTER : hébergeur (nom, adresse, téléphone) — ex. Render Services Inc. / Vercel Inc.]',
    droitApplicable: '[À COMPLÉTER : droit applicable — ex. droit français]',
    tribunaux: '[À COMPLÉTER : tribunaux compétents — ex. tribunaux de Paris]',
    autoriteControle: '[À COMPLÉTER : autorité de contrôle — ex. CNIL, 3 place de Fontenoy, 75007 Paris]',
    /** Durées de conservation annoncées dans la politique de confidentialité. */
    conservationInactifMois: 24,
    conservationJournauxJours: 30,
  },
};
