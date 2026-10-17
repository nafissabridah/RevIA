package com.revia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.revia.domain.PromptSysteme;

public interface PromptSystemeRepository extends JpaRepository<PromptSysteme, Long> {

    Optional<PromptSysteme> findFirstByActifTrue();

    boolean existsByVersion(String version);

    List<PromptSysteme> findAllByOrderByCreeLeDesc();
}
