package com.revia.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.revia.domain.Revue;

/**
 * Toutes les requêtes filtrent sur l'auteur : un développeur ne voit que ses propres revues
 * (contrainte du diagramme de classes, appliquée côté serveur).
 */
public interface RevueRepository extends JpaRepository<Revue, Long>, JpaSpecificationExecutor<Revue> {

    @EntityGraph(attributePaths = {"problemes", "problemes.correction"})
    Optional<Revue> findByIdAndAuteurId(Long id, Long auteurId);

    long countByAuteurIdAndCreeLeGreaterThanEqual(Long auteurId, LocalDateTime depuis);

    long countByAuteurId(Long auteurId);

    /** Export RGPD : toutes les revues de l'utilisateur, problèmes et corrections compris. */
    @EntityGraph(attributePaths = {"problemes", "problemes.correction"})
    List<Revue> findByAuteurIdOrderByCreeLeAsc(Long auteurId);

    @EntityGraph(attributePaths = {"problemes"})
    List<Revue> findByAuteurIdAndCreeLeGreaterThanEqualOrderByCreeLeAsc(Long auteurId, LocalDateTime depuis);

    @Query("""
            select count(p) from Probleme p
            where p.revue.auteur.id = :auteurId and lower(p.titre) = lower(:titre)
              and p.fauxPositif = false and p.revue.id <> :revueId
            """)
    long compterMotif(@Param("auteurId") Long auteurId, @Param("titre") String titre, @Param("revueId") Long revueId);
}
