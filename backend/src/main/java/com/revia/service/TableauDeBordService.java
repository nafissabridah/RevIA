package com.revia.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.revia.domain.TableauDeBord;
import com.revia.repository.RevueRepository;

/** Suivre son tableau de bord : statistiques calculées sur ses propres revues uniquement. */
@Service
public class TableauDeBordService {

    private final RevueRepository revues;

    public TableauDeBordService(RevueRepository revues) {
        this.revues = revues;
    }

    @Transactional(readOnly = true)
    public TableauDeBord calculer(Long userId, int jours) {
        int j = Math.min(Math.max(jours, 1), 365);
        LocalDate fin = LocalDate.now();
        LocalDate debut = fin.minusDays(j - 1L);
        TableauDeBord t = new TableauDeBord(
                revues.findByAuteurIdAndCreeLeGreaterThanEqualOrderByCreeLeAsc(userId, debut.atStartOfDay()), debut, fin);
        t.calculer();
        return t;
    }
}
