package com.revia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.revia.domain.JetonApi;

public interface JetonApiRepository extends JpaRepository<JetonApi, Long> {

    @Query("select j from JetonApi j join fetch j.utilisateur where j.empreinte = ?1")
    Optional<JetonApi> findByEmpreinte(String empreinte);

    List<JetonApi> findByUtilisateurIdOrderByCreeLeDesc(Long utilisateurId);

    Optional<JetonApi> findByIdAndUtilisateurId(Long id, Long utilisateurId);

    long countByUtilisateurId(Long utilisateurId);
}
