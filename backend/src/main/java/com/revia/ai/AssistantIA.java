package com.revia.ai;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.revia.config.ReviaProperties;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.metier.PacksMetier;

import reactor.core.publisher.Flux;

/**
 * Au-delà de la revue : assistant IA (chat) et outils qui accélèrent le travail du développeur
 * (tests, documentation, explication, message de commit, refactoring, conversion).
 * Même LLM, même masquage des secrets et même gestion des erreurs que la revue ;
 * la réponse est du texte libre en Markdown.
 */
@Service
public class AssistantIA {

    private static final Logger log = LoggerFactory.getLogger(AssistantIA.class);

    /** Au-delà, la réponse (tests, code documenté…) risque d'être tronquée par le modèle. */
    public static final int MAX_LIGNES = 500;
    public static final int MAX_MESSAGES = 20;

    static final String CONSIGNES_COMMUNES = """
            Tu es RevIA, un assistant de développement logiciel, d'analyse métier (BA) et d'assurance qualité (QA)
            pour les analystes, les développeurs et les testeurs.
            Rédige en français correct, en Markdown (le code et ses identifiants restent dans leur langue).
            Mets tout code dans un bloc ``` suivi du nom du langage.
            Les valeurs ***MASQUÉ*** sont des secrets masqués volontairement avant l'envoi : ne les devine jamais,
            garde ***MASQUÉ*** ou remplace-les par une variable d'environnement.
            Si la demande n'a aucun rapport avec un projet logiciel (exigences, code, tests), réponds poliment que tu ne traites
            que l'analyse, le développement et le test de logiciels.
            """;

    /** Les outils de la boîte à outils IA, avec leur consigne propre. */
    public enum Outil {
        TESTS("tests", "Génération de tests", """
                Outil : génération de tests.
                Écris des tests unitaires prêts à copier pour le code fourni, avec le framework standard du langage
                (JUnit 5 + AssertJ pour Java, pytest pour Python, Jest pour JavaScript/TypeScript, PHPUnit pour PHP,
                xUnit pour C#, le paquet testing pour Go, sinon le plus courant).
                Couvre le cas nominal, les cas limites et les cas d'erreur, avec des assertions précises sur les valeurs
                (pas seulement « non nul ») : un test doit échouer si un opérateur ou une borne du code change.
                Si un « Contexte d'exécution » est fourni (fichier testé, fichier de test, commande), le fichier doit
                s'exécuter tel quel à cet emplacement : imports exacts, aucun espace réservé « ... », aucune dépendance
                en dehors du langage et du framework indiqués.
                Si le code te semble faux, n'écris pas un test qui valide le bug : signale-le dans « Bugs probables ».
                Compare chaque borne du code à sa documentation (docstring, commentaire, nom) : « à partir de 50 »,
                « au moins », « at least », « minimum » veulent dire >= ; « jusqu'à » / « up to » veut dire <=. Si le code
                ne respecte pas la documentation (ex. « à partir de 50 » mais `> 50`), c'est un bug probable : ne teste
                pas cette valeur limite, signale-la dans « Bugs probables » avec la ligne et le correctif.
                Structure : une phrase d'introduction, un seul bloc de code complet, puis une liste « Ce qui est testé ».
                """),
        DOCUMENTATION("documentation", "Documentation automatique", """
                Outil : documentation automatique.
                Renvoie le code complet avec des commentaires de documentation au format standard du langage
                (Javadoc, docstrings Python, JSDoc/TSDoc, PHPDoc, commentaires XML C#…) sur chaque classe,
                fonction et méthode, sans modifier la logique.
                Ajoute ensuite une section « README » courte : rôle du code, utilisation, exemple d'appel.
                """),
        EXPLICATION("explication", "Explication de code", """
                Outil : explication de code.
                Explique le code à un développeur débutant, avec des phrases courtes :
                1. « En bref » : ce que fait le code, en deux phrases ;
                2. « Pas à pas » : les étapes, en citant les numéros de ligne (le code est numéroté) ;
                3. « Points d'attention » : bugs ou risques éventuels (aucun s'il n'y en a pas).
                """),
        COMMIT("commit", "Message de commit et PR", """
                Outil : message de commit et description de pull request.
                Le texte fourni est un diff (ou le code modifié).
                Rédige :
                1. un message de commit au format Conventional Commits (type(portée): résumé, 72 caractères maximum),
                   suivi d'un corps en puces, dans un bloc ```text ;
                2. une description de pull request avec les titres ## Résumé, ## Changements, ## Comment tester, ## Risques.
                """),
        REFACTORING("refactoring", "Refactoring", """
                Outil : refactoring.
                Propose une version plus lisible, plus simple et plus sûre du code, sans changer son comportement.
                Donne le code complet refactoré dans un seul bloc, puis une liste « Ce qui a changé et pourquoi ».
                """),
        CONVERSION("conversion", "Conversion de langage", """
                Outil : conversion de langage.
                Convertis le code dans le langage cible indiqué, de façon idiomatique (conventions et bibliothèque
                standard du langage cible), en conservant le comportement.
                Donne le code converti dans un seul bloc, puis une liste « Points d'attention »
                (différences de comportement, dépendances à installer).
                """),
        // ----- Outils QA : de l'exigence au test automatisé, et du bug au correctif -----
        CAS_DE_TEST("cas-de-test", "Plan et cas de test (QA)", "Exigence, user story ou code", """
                Outil : plan et cas de test.
                Le texte fourni est une exigence, une user story (avec ses critères d'acceptation) ou du code.
                Rédige, comme un ingénieur QA senior (ISTQB) :
                1. « Périmètre et risques » : ce qui est testé, ce qui ne l'est pas, les risques principaux ;
                2. un tableau Markdown des cas de test : ID (TC-01…), Titre, Type (fonctionnel, limite, négatif, sécurité,
                   accessibilité, performance), Priorité (P1 à P3), Préconditions, Étapes, Résultat attendu ;
                   applique partitions d'équivalence et valeurs limites, et couvre chaque critère d'acceptation ;
                3. les scénarios principaux en Gherkin (Feature / Scenario / Scenario Outline + Examples) dans un bloc ```gherkin ;
                4. « Matrice de traçabilité » : critère d'acceptation → cas de test.
                Si le langage cible indique un outil (ex. Xray, TestRail, Zephyr), adapte les colonnes à son import CSV.
                """),
        E2E("e2e", "Tests end-to-end (UI)", "Parcours, HTML ou composant", """
                Outil : tests end-to-end d'interface.
                Le texte fourni décrit un parcours utilisateur, ou contient du HTML / un composant d'interface.
                Écris des tests E2E prêts à lancer avec l'outil indiqué comme langage cible
                (par défaut Playwright en TypeScript ; sinon Cypress, Selenium Java, Selenium Python, WebdriverIO…).
                Règles : modèle Page Object, sélecteurs robustes (rôle ARIA, libellé, data-testid ; jamais de XPath fragile),
                attentes explicites (aucun sleep fixe), un test indépendant par scénario, cas nominal + erreurs de saisie.
                Structure : une phrase d'introduction, les blocs de code (page object puis tests),
                puis « Comment lancer » (commandes) et « Ce qui est testé ».
                """),
        TESTS_API("tests-api", "Tests d'API (REST)", "Contrat OpenAPI, contrôleur ou endpoints", """
                Outil : tests d'API.
                Le texte fourni est un contrat OpenAPI / Swagger, un contrôleur, une route ou une liste d'endpoints.
                Écris des tests d'API avec l'outil du langage cible (par défaut : REST Assured + JUnit 5 pour Java,
                pytest + requests pour Python, Jest + supertest pour JavaScript/TypeScript ; ou Postman, Karate, k6 si demandé).
                Couvre : codes HTTP attendus, schéma et contenu de la réponse, validation des entrées (400), authentification
                et autorisations (401 / 403), ressource absente (404), idempotence, valeurs limites, injection.
                Ajoute à la fin un court script k6 de test de charge « smoke » (10 utilisateurs, 30 s, seuils p95 et taux d'erreur).
                """),
        RAPPORT_BUG("rapport-bug", "Rapport de bug", "Description, logs ou capture du bug", """
                Outil : rapport de bug.
                Le texte fourni est une description brute d'un bug (souvent incomplète), éventuellement avec des logs.
                Rédige un rapport prêt à coller dans Jira / GitHub Issues / Azure DevOps :
                **Titre** (court, précis), **Environnement**, **Étapes pour reproduire** (numérotées), **Résultat attendu**,
                **Résultat obtenu**, **Gravité** (bloquante, majeure, mineure, cosmétique) et **Priorité** justifiées,
                **Cause probable** (hypothèses classées), **Informations manquantes à demander**,
                puis un **test de non-régression** à automatiser (dans un bloc de code).
                N'invente aucun fait : marque « à confirmer » ce qui n'est pas dans le texte.
                """),
        ANALYSE_LOGS("analyse-logs", "Analyse de logs / stack trace", "Logs, stack trace ou message d'erreur", """
                Outil : analyse de logs et de stack trace.
                Le texte fourni contient des logs, une stack trace ou un message d'erreur (le code peut être joint).
                Réponds avec :
                1. « Diagnostic » : la cause racine probable, en deux phrases ;
                2. « Où » : le fichier, la classe ou la ligne en cause (cite la ligne de la trace) ;
                3. « Pourquoi » : l'enchaînement qui mène à l'erreur ;
                4. « Correction » : le correctif dans un bloc de code ;
                5. « Prévenir » : test, log ou alerte à ajouter pour que cela ne revienne pas.
                Si plusieurs causes sont possibles, classe-les de la plus probable à la moins probable.
                """),
        DONNEES_TEST("donnees-test", "Jeux de données de test", "Schéma, modèle, table SQL ou exemple", """
                Outil : jeux de données de test.
                Le texte fourni est un schéma (classe, interface, table SQL, JSON Schema) ou un exemple d'objet.
                Génère un jeu de données au format du langage cible (par défaut JSON ; sinon CSV, SQL INSERT, YAML, fixtures)
                contenant : des cas nominaux réalistes, des valeurs limites (vide, maximum, Unicode, espaces), des valeurs
                invalides pour les tests négatifs, et des cas d'injection (SQL, XSS) pour les tests de sécurité.
                Toutes les données sont fictives : aucune vraie personne, aucun vrai e-mail (utilise example.com), aucune vraie carte.
                Ajoute une liste « Rôle de chaque enregistrement ».
                """),
        REVUE_TESTS("revue-tests", "Revue de tests", "Tests existants (et code testé)", """
                Outil : revue de tests.
                Le texte fourni contient des tests automatisés (et parfois le code testé).
                Évalue-les comme un ingénieur QA senior :
                1. « Note » sur 10 et verdict en une phrase ;
                2. « Cas manquants » : chemins, limites et erreurs non couverts ;
                3. « Faiblesses » : assertions faibles ou absentes, tests dépendants entre eux, sleeps, données en dur,
                   mocks excessifs, risques de tests instables (flaky), noms peu clairs ;
                4. « Mutants survivants probables » : modifications du code que ces tests ne détecteraient pas ;
                5. « Tests à ajouter » : le code des tests manquants dans un bloc.
                """),
        // ----- Tests prouvés (CI) : les tests générés sont exécutés chez le client, puis réparés ici -----
        REPARER_TESTS("reparer-tests", "Réparation de tests", "Code testé, tests et sortie d'exécution", """
                Outil : réparation de tests.
                Le texte fourni contient le code testé, un fichier de tests généré et la sortie réelle de son exécution
                (erreurs, échecs) ou la liste des mutants survivants (modifications du code que les tests n'ont pas vues).
                Renvoie le fichier de tests COMPLET corrigé dans un seul bloc de code, puis la liste « Corrections ».
                Règles :
                - corrige les imports, la syntaxe, les fixtures, les mocks et les valeurs attendues erronées ;
                - ne modifie jamais le code testé et ne supprime pas un test valide ;
                - si un test échoue parce que le CODE TESTÉ a un vrai bug (le résultat contredit son nom, sa documentation
                  ou son intention évidente), retire ce test et décris le bug dans « Bugs probables détectés »
                  (ligne, entrée, résultat attendu, résultat obtenu) : ne masque jamais un bug en changeant l'assertion ;
                - pour chaque mutant survivant, ajoute une assertion précise qui le détecterait.
                """),
        // ----- Analyse métier (BA) : de la note brute à l'exigence testable, puis sa revue -----
        REDACTION_EXIGENCES("redaction-exigences", "Rédaction d'exigences (BA)", "Notes, e-mail ou compte rendu", """
                Outil : rédaction d'exigences.
                Le texte fourni est brut : notes, e-mail, compte rendu ou transcription de réunion.
                Rédige, comme un analyste métier (BA) senior, un dossier d'exigences prêt à relire, avec EXACTEMENT
                ces titres Markdown, dans cet ordre :
                ## Contexte et objectif — 2 à 4 phrases ;
                ## Règles de gestion — des lignes « - RG-01 : … », une règle vérifiable par ligne (qui, quoi, quand,
                   quelle valeur ou quel seuil) ;
                ## User stories — pour chacune « ### US-01 : <titre court> », puis « En tant que <rôle>, je veux <action>
                   afin de <bénéfice>. » et les règles concernées (RG-xx) ;
                ## Critères d'acceptation — UNIQUEMENT des lignes « - AC-01 (US-01) : … », numérotées en continu sur tout
                   le document (AC-01, AC-02, AC-03… sans repartir à 01 pour chaque story), sans sous-titre entre elles ;
                   chaque critère est testable (valeurs, messages, états) et, quand c'est utile, écrit sur une seule ligne
                   « Étant donné …, quand …, alors … » ; couvre le cas nominal, les erreurs, les limites et les droits ;
                ## Scénarios Gherkin — les principaux critères dans un bloc ```gherkin (Feature, puis un Scenario par
                   critère important, dont le titre commence par son AC-xx) ;
                ## Questions ouvertes — ce que le texte ne dit pas (chiffres, rôles, erreurs, volumes, délais),
                   des lignes « - Q-01 : … » ;
                ## Hors périmètre — ce qui n'est explicitement pas couvert, ou « À confirmer ».
                Les identifiants RG-xx, US-xx et AC-xx sont stables : ne les renumérote pas et réutilise-les pour la
                traçabilité (la section des critères est relue telle quelle par la couverture des critères de RevIA).
                N'invente aucun fait : une valeur absente du texte devient une question ouverte ou « à confirmer ».
                """),
        REVUE_EXIGENCES("revue-exigences", "Revue d'exigences (BA)", "User story, exigence et critères", """
                Outil : revue d'exigences.
                Le texte fourni est une user story, une exigence ou une liste de critères d'acceptation écrite par un
                analyste métier. Relis-le comme un BA senior et un testeur exigeants, avec ces titres :
                ## Note de qualité — « Note : NN/100 » sur la première ligne, puis le verdict en une phrase ;
                ## Grille — tableau Markdown Critère | Statut (✅ / ⚠️ / ❌) | Constat : les 6 critères INVEST de la story
                   (Indépendante, Négociable, Valeur, Estimable, Petite, Testable), puis les qualités ISO/IEC/IEEE 29148
                   (non ambiguë, complète, cohérente, vérifiable, faisable, traçable) ;
                ## Mots ambigus — chaque terme vague CITÉ entre guillemets tel qu'il apparaît dans le texte (« rapidement »,
                   « si nécessaire », « etc. », « user-friendly »…), avec la question qui le rend mesurable ;
                ## Cas manquants — erreurs et saisies invalides, limites et volumes, rôles et droits, états vides,
                   concurrence et double soumission, reprise après panne, accessibilité ;
                ## Contradictions — entre règles, entre critères ou avec le domaine (ou « Aucune détectée ») ;
                ## Critères non testables réécrits — tableau Avant | Après (testable, avec valeurs) ;
                ## Écarts avec les règles du domaine — si un DOMAINE MÉTIER est fourni plus bas, chaque règle ou piège du
                   domaine que l'exigence ignore (cite son identifiant) ; sinon les règles transverses (dates, montants,
                   fuseaux horaires, données personnelles) ;
                ## Questions aux parties prenantes — numérotées, de la plus bloquante à la moins bloquante ;
                ## Version améliorée — la story réécrite (« En tant que …, je veux … afin de … »), puis ses critères en
                   lignes « - AC-01 : … » numérotées en continu, testables et réutilisables telles quelles.
                Ne note pas que la forme : une story bien écrite mais muette sur les erreurs ou les limites ne dépasse
                pas 70/100. Cite le texte plutôt que de le paraphraser.
                """);

        private final String id;
        private final String libelle;
        private final String entree;
        private final String consigne;

        Outil(String id, String libelle, String consigne) {
            this(id, libelle, "Code", consigne);
        }

        Outil(String id, String libelle, String entree, String consigne) {
            this.id = id;
            this.libelle = libelle;
            this.entree = entree;
            this.consigne = consigne;
        }

        /** Nature du texte attendu en entrée (code, exigence, logs…). */
        public String entree() {
            return entree;
        }

        /** Outil QA ou BA : l'entrée n'est pas forcément du code. */
        public boolean qa() {
            return ordinal() >= CAS_DE_TEST.ordinal();
        }

        /** Outil d'analyse métier (BA) : l'entrée est du texte, jamais du code. */
        public boolean ba() {
            return this == REDACTION_EXIGENCES || this == REVUE_EXIGENCES;
        }

        public String id() {
            return id;
        }

        public String libelle() {
            return libelle;
        }

        public static Outil depuis(String id) {
            String v = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(o -> o.id.equals(v)).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.INTROUVABLE, "Outil IA inconnu : " + id));
        }
    }

    static final String CONSIGNE_CHAT = """
            Outil : assistant (chat).
            Réponds à la dernière question de l'utilisateur en t'appuyant sur la conversation et, s'il est fourni,
            sur le code joint. Sois concis et concret, avec un court exemple de code quand c'est utile.
            """;

    /** Un message de la conversation : rôle « utilisateur » ou « assistant ». */
    public record Message(String role, String contenu) {
    }

    /** Réponse de l'assistant ou d'un outil. */
    public record Reponse(String contenu, String langage, int nbMasquages, long dureeMs, String fournisseur, String modele) {
    }

    private final LlmClient llm;
    private final MasqueurDonnees masqueur;
    private final PreparateurCode preparateur;
    private final ExecutorService executor;
    private final ReviaProperties.Ai config;
    private final PacksMetier packs;

    public AssistantIA(LlmClient llm, MasqueurDonnees masqueur, PreparateurCode preparateur,
                       @Qualifier("analyseExecutor") ExecutorService executor, ReviaProperties props, PacksMetier packs) {
        this.packs = packs;
        this.llm = llm;
        this.masqueur = masqueur;
        this.preparateur = preparateur;
        this.executor = executor;
        this.config = props.ai();
    }

    /** Demande prête à envoyer au modèle : consignes, message (code masqué) et informations pour la réponse. */
    public record Demande(String systeme, String message, String langage, int nbMasquages, long debut, boolean approfondi) {

        /** Demande de chat : réponse rapide. */
        public Demande(String systeme, String message, String langage, int nbMasquages, long debut) {
            this(systeme, message, langage, nbMasquages, debut, false);
        }
    }

    /** Exécute un outil de la boîte à outils sur un extrait de code. */
    public Reponse outil(Outil outil, String code, String langage, String cible) {
        return repondre(preparerOutil(outil, code, langage, cible));
    }

    /** Assistant IA : répond au dernier message en tenant compte de la conversation et du code joint. */
    public Reponse discuter(List<Message> messages, String code, String langage) {
        return repondre(preparerChat(messages, code, langage));
    }

    /** Réponse complète en un seul bloc. */
    public Reponse repondre(Demande d) {
        String contenu = d.approfondi()
                ? AnalyseurIA.executer(() -> llm.approfondir(d.systeme(), d.message()), executor, config.timeoutOutilsSec())
                : AnalyseurIA.executer(() -> llm.rediger(d.systeme(), d.message()), executor, config.timeoutSec());
        return new Reponse(contenu.trim(), d.langage(), d.nbMasquages(), System.currentTimeMillis() - d.debut(),
                llm.fournisseur(), llm.modele());
    }

    /**
     * Réponse diffusée morceau par morceau (streaming) : le texte s'affiche dès que le modèle écrit.
     * Délai maximal entre deux morceaux = délai d'analyse ; les erreurs sont traduites en messages clairs.
     */
    public Flux<String> diffuser(Demande d) {
        Flux<String> flux = d.approfondi() ? llm.approfondirEnFlux(d.systeme(), d.message())
                : llm.redigerEnFlux(d.systeme(), d.message());
        // Délai entre deux morceaux : un modèle qui réfléchit avant d'écrire a besoin du délai des outils
        return flux.timeout(Duration.ofSeconds(d.approfondi() ? config.timeoutOutilsSec() : config.timeoutSec()))
                .onErrorMap(e -> e instanceof TimeoutException
                        ? new ApiException(ErrorCode.LLM_TIMEOUT, ErrorCode.LLM_TIMEOUT.message(), e)
                        : AnalyseurIA.traduire(e));
    }

    public String fournisseur() {
        return llm.fournisseur();
    }

    public String modele() {
        return llm.modele();
    }

    public Demande preparerOutil(Outil outil, String code, String langage, String cible) {
        return preparerOutil(outil, code, langage, cible, null);
    }

    /**
     * Prépare l'appel d'un outil. {@code metier} (facultatif, ex. « assurance ») : les règles, pièges et
     * réglementations du pack métier sont ajoutés au prompt système, avec une taille bornée.
     */
    public Demande preparerOutil(Outil outil, String code, String langage, String cible, String metier) {
        verifierDisponible();
        String contexteMetier = packs.contexte(metier);
        verifierTaille(code);
        if (outil == Outil.CONVERSION && (cible == null || cible.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION, "Choisissez le langage cible de la conversion.");
        }
        long debut = System.currentTimeMillis();
        String lang = outil.ba() ? "autre" : preparateur.detecterLangage(langage, null, code);
        MasqueurDonnees.Resultat masque = masqueur.masquer(code);
        String extrait = outil == Outil.EXPLICATION
                ? PreparateurCode.numeroter(PreparateurCode.lignes(masque.code()), 1)
                : masque.code();
        StringBuilder message = new StringBuilder("Langage : ").append(lang).append('\n');
        if (cible != null && !cible.isBlank() && (outil == Outil.CONVERSION || outil.qa())) {
            message.append("Langage cible : ").append(cible.trim()).append('\n');
        } else if (cible != null && !cible.isBlank() && outil == Outil.TESTS) {
            message.append("Contexte d'exécution : ").append(cible.trim()).append('\n');
        }
        String entree = outil == Outil.COMMIT ? "Diff" : outil.qa() ? outil.entree() : "Code";
        // Réparation de tests : l'entrée contient déjà ses blocs (code testé, tests, sortie) : pas de bloc englobant
        message.append(entree).append(" :\n").append(outil == Outil.REPARER_TESTS ? extrait.stripTrailing() + "\n" : bloc(lang, extrait));
        log.info("Outil IA {} : {} ligne(s), {} masquage(s)", outil.id, PreparateurCode.lignes(code).size(), masque.nbMasquages());
        return new Demande(CONSIGNES_COMMUNES + "\n" + outil.consigne + contexteMetier + Langue.consigneTexte(),
                message.toString(), lang,
                masque.nbMasquages(), debut, true);
    }

    public Demande preparerChat(List<Message> messages, String code, String langage) {
        verifierDisponible();
        if (messages == null || messages.isEmpty() || !"utilisateur".equals(messages.get(messages.size() - 1).role())) {
            throw new ApiException(ErrorCode.VALIDATION, "Écrivez votre question.");
        }
        long debut = System.currentTimeMillis();
        int masquages = 0;
        StringBuilder message = new StringBuilder();
        String lang = null;
        if (code != null && !code.isBlank()) {
            verifierTaille(code);
            lang = preparateur.detecterLangage(langage, null, code);
            MasqueurDonnees.Resultat m = masqueur.masquer(code);
            masquages += m.nbMasquages();
            message.append("Code joint (langage : ").append(lang).append(") :\n").append(bloc(lang, m.code())).append('\n');
        }
        message.append("Conversation :\n");
        List<Message> recents = messages.subList(Math.max(0, messages.size() - MAX_MESSAGES), messages.size());
        for (Message msg : recents) {
            MasqueurDonnees.Resultat m = masqueur.masquer(msg.contenu());
            masquages += m.nbMasquages();
            message.append("assistant".equals(msg.role()) ? "Assistant : " : "Utilisateur : ").append(m.code().trim()).append("\n\n");
        }
        message.append("Réponds au dernier message de l'utilisateur.");
        return new Demande(CONSIGNES_COMMUNES + "\n" + CONSIGNE_CHAT + Langue.consigneTexte(),message.toString(), lang, masquages, debut);
    }

    private void verifierDisponible() {
        if (!llm.disponible()) {
            log.error("Assistant IA refusé : clé d'API absente pour le fournisseur {}", llm.fournisseur());
            throw new ApiException(ErrorCode.LLM_CLE_ABSENTE);
        }
    }

    private static void verifierTaille(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "Collez votre code.");
        }
        if (PreparateurCode.lignes(code).size() > MAX_LIGNES) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "Les outils IA acceptent " + MAX_LIGNES + " lignes au maximum : collez une partie du code.");
        }
    }

    private static String bloc(String langage, String code) {
        return "```" + ("autre".equals(langage) ? "" : langage) + "\n" + code.stripTrailing() + "\n```\n";
    }
}
