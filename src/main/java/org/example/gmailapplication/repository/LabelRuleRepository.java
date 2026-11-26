package org.example.gmailapplication.repository;

import org.example.gmailapplication.entity.LabelRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabelRuleRepository extends JpaRepository<LabelRule, Long> {
    Optional<LabelRule> findByLabelNameIgnoreCase(String labelName);
    void deleteByLabelNameIgnoreCase(String labelName);
}
