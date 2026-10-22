import { Langue } from '../../core/langue.service';
import { MARQUE } from '../../core/marque';

/**
 * Contenu des pages légales (FR / EN). MODÈLES à faire valider par un juriste : ils décrivent le fonctionnement
 * réel de RevIA (code envoyé à des fournisseurs LLM tiers après masquage des secrets, paiement par Paddle en tant
 * que marchand officiel, auto-hébergement possible avec Ollama). Les valeurs propres à la société viennent de
 * MARQUE.legal (marque.ts), marquées « À COMPLÉTER » tant qu'elles ne sont pas renseignées.
 */

export type DocLegal = 'cgu' | 'confidentialite' | 'mentions-legales' | 'dpa';

export const DOCS_LEGAUX: DocLegal[] = ['cgu', 'confidentialite', 'mentions-legales', 'dpa'];

export interface Section {
  titre: string;
  paragraphes: string[];
}

export interface Document {
  titre: string;
  intro: string;
  sections: Section[];
  /** Tableau des sous-traitants (politique de confidentialité et DPA). */
  sousTraitants?: boolean;
}

export interface SousTraitant {
  nom: string;
  role: { fr: string; en: string };
  donnees: { fr: string; en: string };
  lieu: { fr: string; en: string };
}

/** Sous-traitants ultérieurs (subprocessors). Les fournisseurs LLM ne sont appelés que s'ils sont configurés. */
export const SOUS_TRAITANTS: SousTraitant[] = [
  {
    nom: 'Google LLC (Gemini API)',
    role: { fr: 'Fournisseur LLM (analyse du code, assistant)', en: 'LLM provider (code analysis, assistant)' },
    donnees: { fr: 'Extraits de code et messages, après masquage des secrets', en: 'Code snippets and messages, after secret masking' },
    lieu: { fr: 'États-Unis / monde', en: 'United States / worldwide' },
  },
  {
    nom: 'Groq, Inc.',
    role: { fr: 'Fournisseur LLM de relais', en: 'Fallback LLM provider' },
    donnees: { fr: 'Extraits de code et messages, après masquage des secrets', en: 'Code snippets and messages, after secret masking' },
    lieu: { fr: 'États-Unis', en: 'United States' },
  },
  {
    nom: 'OpenRouter, Inc.',
    role: { fr: 'Routeur LLM de relais (transmet à des modèles tiers)', en: 'Fallback LLM router (forwards to third-party models)' },
    donnees: { fr: 'Extraits de code et messages, après masquage des secrets', en: 'Code snippets and messages, after secret masking' },
    lieu: { fr: 'États-Unis / fournisseur du modèle choisi', en: 'United States / provider of the selected model' },
  },
  {
    nom: 'Mistral AI SAS',
    role: { fr: 'Fournisseur LLM de relais (Codestral)', en: 'Fallback LLM provider (Codestral)' },
    donnees: { fr: 'Extraits de code et messages, après masquage des secrets', en: 'Code snippets and messages, after secret masking' },
    lieu: { fr: 'Union européenne', en: 'European Union' },
  },
  {
    nom: 'Paddle.com Market Ltd',
    role: { fr: 'Marchand officiel (merchant of record) : paiement, facturation, TVA', en: 'Merchant of record: payment, invoicing, VAT/sales tax' },
    donnees: { fr: 'E-mail, pays, données de paiement (collectées directement par Paddle), identifiant du compte', en: 'E-mail, country, payment data (collected directly by Paddle), account identifier' },
    lieu: { fr: 'Royaume-Uni / monde', en: 'United Kingdom / worldwide' },
  },
  {
    nom: '[À COMPLÉTER : hébergeur de l\'application et de la base de données]',
    role: { fr: 'Hébergement de l\'API, de l\'interface et de la base PostgreSQL', en: 'Hosting of the API, the web app and the PostgreSQL database' },
    donnees: { fr: 'Toutes les données du compte', en: 'All account data' },
    lieu: { fr: '[À COMPLÉTER]', en: '[TO BE COMPLETED]' },
  },
];

export function documentLegal(doc: DocLegal, langue: Langue): Document {
  const m = MARQUE.legal;
  const editeur = `${m.raisonSociale}, ${m.formeJuridique}, ${m.adresse}, ${m.immatriculation}`;
  const prix = `${MARQUE.prix.pro} ${MARQUE.prix.devise}`;
  const prixEq = `${MARQUE.prix.equipe} ${MARQUE.prix.devise}`;
  const fr = langue === 'fr';

  switch (doc) {
    case 'cgu':
      return fr ? {
        titre: 'Conditions générales d\'utilisation et de vente',
        intro: `Les présentes conditions régissent l'utilisation du service RevIA (revue de code assistée par intelligence artificielle) édité par ${editeur} (« nous »). En créant un compte, vous les acceptez.`,
        sections: [
          { titre: '1. Le service', paragraphes: [
            'RevIA analyse le code que vous soumettez (revue, sécurité OWASP, qualité), propose des corrections, un assistant et des outils de test, et s\'intègre à la CI au moyen de jetons d\'API personnels.',
            'Les résultats sont produits par des modèles d\'IA et des règles automatiques : ils peuvent être incomplets ou erronés. Ils ne remplacent ni une relecture humaine, ni un audit de sécurité. Vous restez seul responsable du code que vous déployez.',
          ] },
          { titre: '2. Compte', paragraphes: [
            'Vous devez fournir une adresse e-mail valide et garder votre mot de passe et vos jetons d\'API confidentiels. Toute action effectuée avec vos identifiants ou vos jetons est réputée faite par vous.',
            'Vous pouvez exporter vos données et supprimer votre compte à tout moment depuis Paramètres.',
          ] },
          { titre: '3. Offres, quotas et prix', paragraphes: [
            `Offre Gratuite : quota journalier de revues limité. Offres payantes Pro (${prix} / mois) et Équipe (${prixEq} / mois) : quotas plus élevés. Les quotas en vigueur sont affichés dans l'application ; nous pouvons les ajuster, notamment pour prévenir les abus.`,
            'Les prix affichés s\'entendent hors taxes applicables ; le montant final, taxes comprises, est indiqué avant le paiement.',
          ] },
          { titre: '4. Paiement par Paddle (marchand officiel)', paragraphes: [
            'Les abonnements sont vendus par Paddle.com Market Ltd, qui agit en tant que revendeur et marchand officiel (merchant of record). Paddle encaisse le paiement, émet la facture, collecte et reverse la TVA ou la taxe de vente applicable, et gère les remboursements et contestations. Les conditions d\'achat de Paddle (paddle.com/legal/checkout-buyer-terms) s\'appliquent à la transaction.',
            'L\'abonnement est renouvelé automatiquement à chaque période jusqu\'à résiliation. Vous pouvez résilier depuis le lien présent dans votre reçu Paddle, depuis le portail client (Paramètres › Abonnement) ou en nous contactant ; la résiliation prend effet à la fin de la période payée. En cas d\'échec de paiement, l\'offre payante est maintenue pendant les relances de Paddle, puis le compte repasse à l\'offre Gratuite.',
            'Droit de rétractation (consommateurs de l\'UE / du Royaume-Uni) : il s\'applique selon les conditions de Paddle ; en demandant l\'accès immédiat au service, vous reconnaissez que ce droit peut prendre fin dès le début de l\'exécution.',
          ] },
          { titre: '5. Votre code et vos contenus', paragraphes: [
            'Vous conservez tous vos droits sur le code et les textes que vous soumettez. Vous nous accordez uniquement le droit de les traiter pour fournir le service.',
            'Avant tout envoi à un modèle d\'IA, RevIA masque automatiquement les secrets détectés (mots de passe, clés et jetons d\'API, chaînes de connexion, e-mails). Ce masquage est une protection supplémentaire, pas une garantie : ne soumettez pas de code dont la divulgation à un fournisseur LLM tiers vous est interdite. Pour ce cas, utilisez l\'auto-hébergement avec un modèle local (Ollama).',
            'Selon la configuration, le code est traité par des fournisseurs LLM tiers (Google Gemini, Groq, OpenRouter, Mistral). Les offres gratuites de certains fournisseurs peuvent utiliser les données reçues pour améliorer leurs modèles (voir la politique de confidentialité).',
          ] },
          { titre: '6. Utilisation acceptable', paragraphes: [
            'Il est interdit : de soumettre du code ou des données que vous n\'avez pas le droit de partager ; de contourner les quotas, la limitation de débit ou la sécurité ; de revendre l\'accès sans accord écrit ; d\'utiliser le service pour développer des logiciels malveillants ou attaquer des systèmes tiers.',
            'Nous pouvons suspendre ou bloquer un compte en cas de manquement, après notification lorsque c\'est possible.',
          ] },
          { titre: '7. Disponibilité et responsabilité', paragraphes: [
            'Le service est fourni « en l\'état », avec une obligation de moyens. Il dépend de fournisseurs tiers (hébergement, LLM) dont les quotas ou interruptions peuvent le dégrader temporairement.',
            'Dans les limites permises par la loi, notre responsabilité totale est limitée aux sommes payées par vous au cours des 12 derniers mois ; nous ne répondons pas des dommages indirects (perte de données, de chiffre d\'affaires, failles non détectées). Rien dans ces conditions ne limite les droits impératifs des consommateurs.',
          ] },
          { titre: '8. Modifications, résiliation, droit applicable', paragraphes: [
            'Nous pouvons modifier ces conditions ; les changements importants vous seront notifiés au moins 30 jours à l\'avance. Vous pouvez cesser d\'utiliser le service et supprimer votre compte à tout moment.',
            `Droit applicable : ${m.droitApplicable}. Litiges : ${m.tribunaux}, sous réserve des règles protectrices des consommateurs. Contact : ${m.emailContact}.`,
          ] },
        ],
      } : {
        titre: 'Terms of Service and Sale',
        intro: `These terms govern the use of RevIA (AI-assisted code review), published by ${editeur} ("we"). By creating an account, you accept them.`,
        sections: [
          { titre: '1. The service', paragraphes: [
            'RevIA analyses the code you submit (review, OWASP security, quality), suggests fixes, offers an assistant and testing tools, and plugs into CI through personal API tokens.',
            'Results are produced by AI models and automated rules: they may be incomplete or wrong. They do not replace human review or a security audit. You remain solely responsible for the code you ship.',
          ] },
          { titre: '2. Account', paragraphes: [
            'You must provide a valid e-mail address and keep your password and API tokens confidential. Any action performed with your credentials or tokens is deemed to be yours.',
            'You can export your data and delete your account at any time from Settings.',
          ] },
          { titre: '3. Plans, quotas and prices', paragraphes: [
            `Free plan: limited daily review quota. Paid plans Pro (${prix} / month) and Team (${prixEq} / month): higher quotas. Current quotas are shown in the app; we may adjust them, in particular to prevent abuse.`,
            'Displayed prices exclude applicable taxes; the final amount including taxes is shown before payment.',
          ] },
          { titre: '4. Payment through Paddle (merchant of record)', paragraphes: [
            'Subscriptions are sold by Paddle.com Market Ltd, acting as reseller and merchant of record. Paddle collects the payment, issues the invoice, collects and remits VAT or sales tax, and handles refunds and disputes. Paddle\'s buyer terms (paddle.com/legal/checkout-buyer-terms) apply to the transaction.',
            'Subscriptions renew automatically each period until cancelled. You can cancel from the link in your Paddle receipt, from the customer portal (Settings › Subscription) or by contacting us; cancellation takes effect at the end of the paid period. If a payment fails, the paid plan is kept while Paddle retries, then the account returns to the Free plan.',
            'Right of withdrawal (EU / UK consumers): it applies under Paddle\'s terms; by requesting immediate access to the service you acknowledge that this right may end once performance has started.',
          ] },
          { titre: '5. Your code and content', paragraphes: [
            'You keep all rights to the code and text you submit. You only grant us the right to process them to provide the service.',
            'Before anything is sent to an AI model, RevIA automatically masks detected secrets (passwords, API keys and tokens, connection strings, e-mail addresses). Masking is an extra safeguard, not a guarantee: do not submit code you are not allowed to disclose to a third-party LLM provider. For that case, self-host RevIA with a local model (Ollama).',
            'Depending on configuration, code is processed by third-party LLM providers (Google Gemini, Groq, OpenRouter, Mistral). Free tiers of some providers may use the data they receive to improve their models (see the privacy policy).',
          ] },
          { titre: '6. Acceptable use', paragraphes: [
            'You must not: submit code or data you have no right to share; bypass quotas, rate limits or security; resell access without written consent; use the service to build malware or attack third-party systems.',
            'We may suspend or block an account in case of breach, with prior notice where possible.',
          ] },
          { titre: '7. Availability and liability', paragraphes: [
            'The service is provided "as is", on a best-effort basis. It relies on third parties (hosting, LLMs) whose quotas or outages may temporarily degrade it.',
            'To the extent permitted by law, our total liability is limited to the amounts you paid in the previous 12 months; we are not liable for indirect damages (data loss, lost revenue, undetected vulnerabilities). Nothing in these terms limits mandatory consumer rights.',
          ] },
          { titre: '8. Changes, termination, governing law', paragraphes: [
            'We may update these terms; material changes will be notified at least 30 days in advance. You may stop using the service and delete your account at any time.',
            `Governing law: ${m.droitApplicable}. Disputes: ${m.tribunaux}, subject to consumer protection rules. Contact: ${m.emailContact}.`,
          ] },
        ],
      };

    case 'confidentialite':
      return fr ? {
        titre: 'Politique de confidentialité',
        intro: `Responsable du traitement : ${editeur}. Contact pour vos données personnelles : ${m.emailDpo}.`,
        sousTraitants: true,
        sections: [
          { titre: '1. Données traitées', paragraphes: [
            'Compte : nom, adresse e-mail, mot de passe (stocké uniquement sous forme de hash BCrypt), date de création, offre et quota.',
            'Historique : le code soumis, son titre et son langage, le rapport de revue (score, problèmes, corrections), vos retours (faux positif, corrigé, utile).',
            'Jetons d\'API : nom, préfixe, dates de création et de dernier usage ; seule une empreinte SHA-256 est conservée, jamais le jeton en clair.',
            'Abonnement : identifiants client et abonnement Paddle, statut et date de fin de période. Nous ne recevons jamais vos données de carte : elles sont collectées par Paddle.',
            'Données techniques : adresse IP (limitation de débit, sécurité) et journaux du serveur.',
            'Essai sans compte (page d\'accueil) : le code est analysé puis n\'est pas enregistré. Les conversations de l\'assistant et des outils IA ne sont pas enregistrées dans votre historique.',
          ] },
          { titre: '2. Finalités et bases légales', paragraphes: [
            'Fournir le service et votre historique (exécution du contrat) ; facturer via Paddle (exécution du contrat, obligations légales) ; sécuriser le service et prévenir les abus (intérêt légitime) ; répondre à vos demandes (intérêt légitime ou contrat).',
            'Nous ne vendons pas vos données et n\'utilisons pas votre code pour entraîner nos propres modèles.',
          ] },
          { titre: '3. Envoi du code à des fournisseurs d\'IA', paragraphes: [
            'Pour analyser votre code, RevIA l\'envoie à un fournisseur LLM : Google Gemini en principal, puis, en cas de quota atteint ou d\'indisponibilité, Groq, OpenRouter (qui transmet au modèle choisi) ou Mistral. Avant l\'envoi, les secrets détectés (mots de passe, clés et jetons, chaînes de connexion, e-mails) sont remplacés par ***MASQUÉ***.',
            'Important : les offres gratuites de certains fournisseurs (notamment Google AI Studio / Gemini API gratuit, et certains modèles gratuits accessibles via OpenRouter) peuvent conserver les requêtes et les utiliser pour améliorer leurs modèles, et faire relire des échantillons par des humains. Ne soumettez pas de code confidentiel si cela ne vous convient pas.',
            'Alternative : RevIA peut être auto-hébergé avec un modèle local (Ollama) — le code ne quitte alors pas votre réseau — ou configuré avec votre propre fournisseur et votre propre contrat.',
          ] },
          { titre: '4. Durées de conservation', paragraphes: [
            'Compte, historique et jetons : jusqu\'à la suppression de votre compte (ou d\'une revue, que vous pouvez supprimer individuellement).',
            `Comptes inactifs : nous pouvons supprimer un compte inactif depuis plus de ${m.conservationInactifMois} mois, après vous avoir prévenu par e-mail.`,
            `Journaux techniques : ${m.conservationJournauxJours} jours environ. Données de facturation : conservées par Paddle selon ses obligations légales.`,
          ] },
          { titre: '5. Vos droits', paragraphes: [
            'Vous disposez des droits d\'accès, de rectification, d\'effacement, de limitation, d\'opposition et de portabilité. Depuis Paramètres, vous pouvez exporter toutes vos données (fichier JSON) et supprimer votre compte vous-même ; vous pouvez aussi modifier votre nom.',
            `Pour toute autre demande : ${m.emailDpo}. Vous pouvez introduire une réclamation auprès de l'autorité de contrôle : ${m.autoriteControle}.`,
          ] },
          { titre: '6. Transferts hors UE et sécurité', paragraphes: [
            'Certains sous-traitants sont situés hors de l\'Union européenne (notamment aux États-Unis). Les transferts reposent sur les garanties prévues par le RGPD (décision d\'adéquation comme le Data Privacy Framework, ou clauses contractuelles types).',
            'Mesures de sécurité : HTTPS, mots de passe hachés (BCrypt), jetons stockés sous forme d\'empreinte, clés d\'API des fournisseurs conservées uniquement côté serveur, masquage des secrets, cloisonnement des données par utilisateur, limitation de débit.',
            'Cookies : RevIA n\'utilise pas de cookie publicitaire ni de mesure d\'audience. La session et vos préférences (langue) sont stockées dans le stockage local de votre navigateur. Le module de paiement Paddle, chargé uniquement lorsque vous ouvrez le paiement, peut déposer ses propres cookies nécessaires à la transaction.',
          ] },
          { titre: '7. Sous-traitants', paragraphes: ['Les sous-traitants ci-dessous peuvent traiter des données pour notre compte :'] },
        ],
      } : {
        titre: 'Privacy Policy',
        intro: `Data controller: ${editeur}. Contact for personal data matters: ${m.emailDpo}.`,
        sousTraitants: true,
        sections: [
          { titre: '1. Data we process', paragraphes: [
            'Account: name, e-mail address, password (stored only as a BCrypt hash), creation date, plan and quota.',
            'History: submitted code, its title and language, the review report (score, issues, fixes), your feedback (false positive, fixed, useful).',
            'API tokens: name, prefix, creation and last-use dates; only a SHA-256 fingerprint is kept, never the clear token.',
            'Subscription: Paddle customer and subscription identifiers, status and period end date. We never receive your card details: they are collected by Paddle.',
            'Technical data: IP address (rate limiting, security) and server logs.',
            'No-account trial (home page): the code is analysed and not stored. Assistant and AI tool conversations are not stored in your history.',
          ] },
          { titre: '2. Purposes and legal bases', paragraphes: [
            'Providing the service and your history (contract); billing through Paddle (contract, legal obligations); securing the service and preventing abuse (legitimate interest); answering your requests (legitimate interest or contract).',
            'We do not sell your data and do not use your code to train our own models.',
          ] },
          { titre: '3. Sending code to AI providers', paragraphes: [
            'To analyse your code, RevIA sends it to an LLM provider: Google Gemini first, then, when a quota is reached or a provider is unavailable, Groq, OpenRouter (which forwards to the selected model) or Mistral. Before sending, detected secrets (passwords, keys and tokens, connection strings, e-mail addresses) are replaced with ***MASQUÉ***.',
            'Important: free tiers of some providers (notably the free Google AI Studio / Gemini API, and some free models available through OpenRouter) may retain prompts, use them to improve their models and have samples reviewed by humans. Do not submit confidential code if that is not acceptable to you.',
            'Alternative: RevIA can be self-hosted with a local model (Ollama) — code then never leaves your network — or configured with your own provider and contract.',
          ] },
          { titre: '4. Retention', paragraphes: [
            'Account, history and tokens: until you delete your account (or a single review, which you can delete individually).',
            `Inactive accounts: we may delete an account inactive for more than ${m.conservationInactifMois} months, after notifying you by e-mail.`,
            `Technical logs: about ${m.conservationJournauxJours} days. Billing data: kept by Paddle as required by law.`,
          ] },
          { titre: '5. Your rights', paragraphes: [
            'You have the rights of access, rectification, erasure, restriction, objection and portability. From Settings you can export all your data (JSON file) and delete your account yourself; you can also change your name.',
            `For any other request: ${m.emailDpo}. You may lodge a complaint with the supervisory authority: ${m.autoriteControle}.`,
          ] },
          { titre: '6. International transfers and security', paragraphes: [
            'Some subprocessors are located outside the European Union (notably in the United States). Transfers rely on GDPR safeguards (adequacy decision such as the Data Privacy Framework, or standard contractual clauses).',
            'Security measures: HTTPS, hashed passwords (BCrypt), tokens stored as fingerprints, provider API keys kept server-side only, secret masking, per-user data isolation, rate limiting.',
            'Cookies: RevIA uses no advertising or analytics cookies. Your session and preferences (language) are kept in your browser\'s local storage. The Paddle payment module, loaded only when you open checkout, may set its own cookies required for the transaction.',
          ] },
          { titre: '7. Subprocessors', paragraphes: ['The subprocessors below may process data on our behalf:'] },
        ],
      };

    case 'mentions-legales':
      return fr ? {
        titre: 'Mentions légales',
        intro: 'Informations sur l\'éditeur et l\'hébergeur du site.',
        sections: [
          { titre: 'Éditeur', paragraphes: [m.raisonSociale, m.formeJuridique, m.adresse, m.immatriculation, m.tva, `Contact : ${m.emailContact}`] },
          { titre: 'Directeur de la publication', paragraphes: [m.directeurPublication] },
          { titre: 'Hébergement', paragraphes: [m.hebergeur] },
          { titre: 'Paiement', paragraphes: ['Les abonnements sont vendus par Paddle.com Market Ltd (Judd House, 18-29 Mora Street, Londres EC1V 8BT, Royaume-Uni), revendeur et marchand officiel, qui émet les factures et collecte les taxes.'] },
          { titre: 'Propriété intellectuelle', paragraphes: ['La marque RevIA, l\'interface et les textes du site sont protégés. Le code que vous soumettez reste votre propriété.'] },
          { titre: 'Données personnelles', paragraphes: ['Voir la politique de confidentialité et, pour les clients professionnels, l\'accord de traitement des données (DPA).'] },
        ],
      } : {
        titre: 'Legal notice',
        intro: 'Information about the publisher and host of this website.',
        sections: [
          { titre: 'Publisher', paragraphes: [m.raisonSociale, m.formeJuridique, m.adresse, m.immatriculation, m.tva, `Contact: ${m.emailContact}`] },
          { titre: 'Publication director', paragraphes: [m.directeurPublication] },
          { titre: 'Hosting', paragraphes: [m.hebergeur] },
          { titre: 'Payment', paragraphes: ['Subscriptions are sold by Paddle.com Market Ltd (Judd House, 18-29 Mora Street, London EC1V 8BT, United Kingdom), reseller and merchant of record, which issues invoices and collects taxes.'] },
          { titre: 'Intellectual property', paragraphes: ['The RevIA brand, interface and website texts are protected. The code you submit remains yours.'] },
          { titre: 'Personal data', paragraphes: ['See the privacy policy and, for business customers, the data processing agreement (DPA).'] },
        ],
      };

    case 'dpa':
      return fr ? {
        titre: 'Accord de traitement des données (DPA)',
        intro: `Le présent accord (article 28 du RGPD) complète les CGU lorsque le client (« Responsable du traitement ») utilise RevIA pour traiter des données personnelles contenues dans son code ou ses textes. ${editeur} agit en qualité de « Sous-traitant ».`,
        sousTraitants: true,
        sections: [
          { titre: '1. Objet et durée', paragraphes: [
            'Objet : revue de code, assistant et outils de test fournis par RevIA. Durée : celle de l\'abonnement ou de l\'utilisation du service.',
            'Nature des données : données personnelles que le client choisit d\'inclure dans le code, les messages ou les critères soumis (ex. noms, e-mails, identifiants dans des jeux de test) et données des utilisateurs du compte. Catégories de personnes : utilisateurs du client et personnes figurant dans ces contenus. Le client s\'engage à ne pas soumettre de catégories particulières de données (art. 9 RGPD).',
          ] },
          { titre: '2. Obligations du sous-traitant', paragraphes: [
            'Traiter les données uniquement sur instruction documentée du client (l\'utilisation du service vaut instruction) ; garantir la confidentialité des personnes autorisées ; mettre en œuvre les mesures de sécurité de l\'annexe ; aider le client à répondre aux demandes d\'exercice des droits (export et suppression en libre-service) ; notifier toute violation de données dans les meilleurs délais et au plus tard sous 72 heures après en avoir pris connaissance ; mettre à disposition les informations nécessaires pour démontrer la conformité.',
            'En fin de contrat : suppression des données à la suppression du compte (immédiate dans la base de production ; sauvegardes écrasées selon leur cycle de rotation), sauf obligation légale de conservation.',
          ] },
          { titre: '3. Sous-traitants ultérieurs', paragraphes: [
            'Le client autorise le recours aux sous-traitants listés ci-dessous. Nous informerons le client de tout ajout ou remplacement au moins 30 jours à l\'avance, ce qui lui permet de s\'y opposer en résiliant.',
            'Les fournisseurs LLM ne reçoivent le code qu\'après masquage automatique des secrets. Certaines offres gratuites de ces fournisseurs peuvent utiliser les données pour améliorer leurs modèles : le client qui ne l\'accepte pas doit utiliser l\'auto-hébergement (modèle local Ollama) ou un fournisseur sous contrat professionnel configuré par ses soins.',
          ] },
          { titre: '4. Transferts internationaux', paragraphes: ['Les transferts hors EEE sont encadrés par une décision d\'adéquation (dont le Data Privacy Framework UE–États-Unis) ou par les clauses contractuelles types de la Commission européenne.'] },
          { titre: '5. Annexe : mesures de sécurité', paragraphes: [
            'Chiffrement en transit (HTTPS/TLS) ; mots de passe hachés BCrypt ; jetons d\'API stockés sous forme d\'empreinte SHA-256 ; clés des fournisseurs conservées uniquement côté serveur ; masquage des secrets avant envoi au LLM ; cloisonnement des données par utilisateur au niveau des requêtes ; limitation de débit et quotas ; accès d\'administration restreint.',
          ] },
          { titre: '6. Divers', paragraphes: [`Droit applicable et juridiction : ceux des CGU (${m.droitApplicable}). Contact : ${m.emailDpo}.`] },
          { titre: '7. Liste des sous-traitants ultérieurs', paragraphes: [] },
        ],
      } : {
        titre: 'Data Processing Agreement (DPA)',
        intro: `This agreement (GDPR Article 28) supplements the Terms when the customer ("Controller") uses RevIA to process personal data contained in its code or text. ${editeur} acts as "Processor".`,
        sousTraitants: true,
        sections: [
          { titre: '1. Subject matter and duration', paragraphes: [
            'Subject matter: code review, assistant and testing tools provided by RevIA. Duration: the term of the subscription or use of the service.',
            'Nature of data: personal data the customer chooses to include in submitted code, messages or criteria (e.g. names, e-mails, identifiers in test data) and data of the account users. Data subjects: the customer\'s users and persons appearing in that content. The customer agrees not to submit special categories of data (GDPR Art. 9).',
          ] },
          { titre: '2. Processor obligations', paragraphes: [
            'Process data only on the customer\'s documented instructions (use of the service constitutes instructions); ensure confidentiality of authorised personnel; implement the security measures in the annex; assist the customer with data subject requests (self-service export and deletion); notify any personal data breach without undue delay and no later than 72 hours after becoming aware of it; make available the information needed to demonstrate compliance.',
            'At the end of the contract: data is deleted when the account is deleted (immediately in the production database; backups overwritten according to their rotation cycle), unless retention is required by law.',
          ] },
          { titre: '3. Sub-processors', paragraphes: [
            'The customer authorises the sub-processors listed below. We will inform the customer of any addition or replacement at least 30 days in advance, allowing it to object by terminating.',
            'LLM providers only receive code after automatic secret masking. Some free tiers of these providers may use data to improve their models: customers who do not accept this must self-host (local Ollama model) or configure a provider under their own business contract.',
          ] },
          { titre: '4. International transfers', paragraphes: ['Transfers outside the EEA rely on an adequacy decision (including the EU–US Data Privacy Framework) or on the European Commission\'s standard contractual clauses.'] },
          { titre: '5. Annex: security measures', paragraphes: [
            'Encryption in transit (HTTPS/TLS); BCrypt-hashed passwords; API tokens stored as SHA-256 fingerprints; provider keys kept server-side only; secret masking before sending to the LLM; per-user data isolation enforced in queries; rate limiting and quotas; restricted administrative access.',
          ] },
          { titre: '6. Miscellaneous', paragraphes: [`Governing law and jurisdiction: as in the Terms (${m.droitApplicable}). Contact: ${m.emailDpo}.`] },
          { titre: '7. List of sub-processors', paragraphes: [] },
        ],
      };
  }
}
