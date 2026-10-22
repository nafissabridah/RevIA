/**
 * Informations commerciales et légales affichées sur la page d'accueil et dans les pages /legal/*.
 * Les valeurs de `legal` apparaissent telles quelles dans les CGU, la politique de confidentialité, les mentions
 * légales et le DPA : les tenir à jour (adresse postale, création d'une société, changement d'hébergeur…).
 */
export const MARQUE = {
  contact: 'saleh@masartr.com',
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
    // Projet personnel édité par une personne physique (pas de société) : pas de capital, de RCS ni de TVA.
    // Si une société est créée plus tard, remplacer ces valeurs par celles de la société.
    raisonSociale: 'Saleh — RevIA (projet personnel)',
    formeJuridique: 'personne physique / individual',
    adresse: 'Tunisie / Tunisia',
    immatriculation: 'non immatriculé (projet personnel) / not registered (personal project)',
    tva: 'TVA : non applicable (les paiements sont encaissés par Paddle, revendeur officiel) / VAT: collected by Paddle as merchant of record',
    directeurPublication: 'Saleh',
    emailContact: 'saleh@masartr.com',
    emailDpo: 'saleh@masartr.com',
    hebergeur: 'Render Services, Inc., 525 Brannan St, Ste 300, San Francisco, CA 94107, USA (render.com) — '
      + 'Vercel Inc., 440 N Barranca Ave #4133, Covina, CA 91723, USA (vercel.com)',
    droitApplicable: 'droit tunisien / Tunisian law',
    tribunaux: 'tribunaux de Tunis / courts of Tunis',
    autoriteControle: 'Instance Nationale de Protection des Données Personnelles (INPDP), Tunis — www.inpdp.nat.tn '
      + '(loi organique n° 2004-63 du 27 juillet 2004) ; pour les utilisateurs de l\'Union européenne : l\'autorité '
      + 'de protection des données de leur pays (RGPD) / EU users: their national data protection authority (GDPR)',
    /** Durées de conservation annoncées dans la politique de confidentialité. */
    conservationInactifMois: 24,
    conservationJournauxJours: 30,
  },
};
