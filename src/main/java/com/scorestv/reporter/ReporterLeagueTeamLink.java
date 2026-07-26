package com.scorestv.reporter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** reporter_league_teams junction — native erişim (entity'siz, iki kolon). */
@Repository
public class ReporterLeagueTeamLink {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void link(Long leagueId, Long teamId) {
        em.createNativeQuery(
                        "INSERT INTO reporter_league_teams(league_id, team_id) "
                                + "VALUES (:l, :t) ON CONFLICT DO NOTHING")
                .setParameter("l", leagueId)
                .setParameter("t", teamId)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Long> teamIds(Long leagueId) {
        return em.createNativeQuery(
                        "SELECT team_id FROM reporter_league_teams WHERE league_id = :l",
                        Long.class)
                .setParameter("l", leagueId)
                .getResultList();
    }
}
