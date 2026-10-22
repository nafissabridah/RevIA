-- RevIA - GitHub App « installer RevIA en un clic ».
-- Une installation GitHub (compte ou organisation) est reliée à UN compte RevIA : les revues des pull requests
-- de cette installation sont comptées sur le quota (et l'offre) de ce compte. La suppression du compte supprime
-- la liaison (cascade) ; la désinstallation de l'application sur GitHub aussi (webhook « installation deleted »).
CREATE TABLE github_installation (
    installation_id  BIGINT        PRIMARY KEY,
    utilisateur_id   BIGINT        NOT NULL,
    compte           VARCHAR(100)  NOT NULL,
    type_compte      VARCHAR(20),
    lie_le           TIMESTAMP     NOT NULL,
    CONSTRAINT fk_github_installation_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE
);
CREATE INDEX ix_github_installation_utilisateur ON github_installation (utilisateur_id);

-- Idempotence des webhooks : chaque livraison (en-tête X-GitHub-Delivery) n'est traitée qu'une fois.
CREATE TABLE github_livraison (
    delivery_id  VARCHAR(64)  PRIMARY KEY,
    evenement    VARCHAR(40)  NOT NULL,
    recu_le      TIMESTAMP    NOT NULL
);
