package com.revia.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.revia.config.ReviaProperties;
import com.revia.domain.Developpeur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.RevueRepository;

/**
 * Quota journalier de revues, réinitialisé à minuit, vérifié AVANT tout appel au LLM.
 * Quota effectif = surcharge de l'administrateur si elle est définie, sinon quota de l'offre
 * (GRATUIT / PRO / EQUIPE, {@code revia.offres.*}). Voir {@link Developpeur}.
 */
@Service
public class QuotaService {

    private final RevueRepository revues;
    private final ReviaProperties.Offres offres;

    public QuotaService(RevueRepository revues, ReviaProperties props) {
        this.revues = revues;
        this.offres = props.offres();
    }

    public ReviaProperties.Offres offres() {
        return offres;
    }

    public static LocalDateTime debutDuJour() {
        return LocalDate.now().atStartOfDay();
    }

    /** Renseigne le champ calculé revuesDuJour du développeur. */
    public Developpeur charger(Developpeur dev) {
        dev.setQuotaOffre(offres.quota(dev.getPlan()));
        dev.setRevuesDuJour((int) revues.countByAuteurIdAndCreeLeGreaterThanEqual(dev.getId(), debutDuJour()));
        return dev;
    }

    public void verifier(Developpeur dev) {
        charger(dev);
        if (dev.quotaAtteint()) {
            throw new ApiException(ErrorCode.QUOTA_UTILISATEUR,
                    "Vous avez atteint vos " + dev.getQuotaJournalier() + " revues du jour.");
        }
    }
}
