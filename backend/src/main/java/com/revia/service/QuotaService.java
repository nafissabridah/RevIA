package com.revia.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.revia.domain.Developpeur;
import com.revia.error.ApiException;
import com.revia.error.ErrorCode;
import com.revia.repository.RevueRepository;

/** Quota journalier (50 revues par défaut), réinitialisé à minuit. Vérifié AVANT tout appel au LLM. */
@Service
public class QuotaService {

    private final RevueRepository revues;

    public QuotaService(RevueRepository revues) {
        this.revues = revues;
    }

    public static LocalDateTime debutDuJour() {
        return LocalDate.now().atStartOfDay();
    }

    /** Renseigne le champ calculé revuesDuJour du développeur. */
    public Developpeur charger(Developpeur dev) {
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
