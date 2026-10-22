package com.revia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.revia.domain.InstallationGithub;

public interface InstallationGithubRepository extends JpaRepository<InstallationGithub, Long> {

    List<InstallationGithub> findByUtilisateurIdOrderByLieLeDesc(Long utilisateurId);

    Optional<InstallationGithub> findByInstallationIdAndUtilisateurId(Long installationId, Long utilisateurId);
}
