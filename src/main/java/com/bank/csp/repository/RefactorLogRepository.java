package com.bank.csp.repository;

import com.bank.csp.domain.RefactorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for RefactorLog entities.
 * We don't need to add any methods here.
 * JpaRepository gives us save(), findById(), findAll(), etc. automatically.
 */
@Repository
public interface RefactorLogRepository extends JpaRepository<RefactorLog, Long> {

    // We use findAll(Sort.by(...)) in the controller, so this is not needed.
    // This fixes the "cannot find symbol" compile error.

}

