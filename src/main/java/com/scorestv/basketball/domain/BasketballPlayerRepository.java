package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Basketbol oyuncu master tablosu. */
public interface BasketballPlayerRepository extends JpaRepository<BasketballPlayer, Long> {
}
