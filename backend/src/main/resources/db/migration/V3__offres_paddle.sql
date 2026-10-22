-- RevIA - offres commerciales (GRATUIT / PRO / EQUIPE) et abonnements Paddle Billing.
--
-- Règle des quotas à partir de cette version :
--   * quota_journalier devient une SURCHARGE définie par l'administrateur (NULL = quota de l'offre) ;
--   * quand elle est renseignée, la surcharge l'emporte toujours sur le quota de l'offre.
-- Les comptes existants avaient tous la valeur par défaut historique (50) : elle est effacée pour qu'ils
-- suivent désormais le quota de leur offre ; les quotas réellement modifiés par un admin sont conservés.

ALTER TABLE utilisateur ALTER COLUMN quota_journalier DROP DEFAULT;
ALTER TABLE utilisateur ALTER COLUMN quota_journalier DROP NOT NULL;
UPDATE utilisateur SET quota_journalier = NULL WHERE quota_journalier = 50 OR type = 'ADMINISTRATEUR';

ALTER TABLE utilisateur ADD COLUMN plan VARCHAR(20) DEFAULT 'GRATUIT' NOT NULL;
ALTER TABLE utilisateur ADD COLUMN paddle_customer_id VARCHAR(64);
ALTER TABLE utilisateur ADD COLUMN paddle_subscription_id VARCHAR(64);
ALTER TABLE utilisateur ADD COLUMN abonnement_statut VARCHAR(20);
ALTER TABLE utilisateur ADD COLUMN abonnement_fin_periode TIMESTAMP;
-- Date (occurred_at) du dernier événement Paddle appliqué : les événements plus anciens reçus en retard sont ignorés.
ALTER TABLE utilisateur ADD COLUMN abonnement_maj_le TIMESTAMP;
ALTER TABLE utilisateur ADD CONSTRAINT ck_utilisateur_plan CHECK (plan IN ('GRATUIT', 'PRO', 'EQUIPE'));
CREATE INDEX ix_utilisateur_paddle_customer ON utilisateur (paddle_customer_id);
CREATE INDEX ix_utilisateur_paddle_subscription ON utilisateur (paddle_subscription_id);

-- Idempotence des webhooks : chaque event_id Paddle n'est traité qu'une fois (Paddle peut renvoyer un événement).
CREATE TABLE paddle_evenement (
    event_id  VARCHAR(64) PRIMARY KEY,
    type      VARCHAR(80) NOT NULL,
    recu_le   TIMESTAMP   NOT NULL
);
