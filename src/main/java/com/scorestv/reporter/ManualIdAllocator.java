package com.scorestv.reporter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** Manuel varlık id tahsisi — 900M+ uzay, API id'leriyle çakışmaz. */
@Repository
public class ManualIdAllocator {

    @PersistenceContext
    private EntityManager em;

    public Long next() {
        return ((Number) em.createNativeQuery("SELECT nextval('manual_entity_seq')")
                .getSingleResult()).longValue();
    }
}
