package com.scorestv.search.events;

import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.Team;

/**
 * Bir entity DB'ye save/update edildiginde yayinlanan event.
 *
 * <p>Upserter'lar (TeamUpserter, FixtureUpserter, ...) save sonrasi bu
 * event'i yayinlar. {@link SearchIndexEventListener} bunu yakalayip
 * <b>transaction commit sonrasi</b> + <b>async</b> ES'e yazar.
 *
 * <p><b>Tasarim:</b>
 * <ul>
 *   <li>Loose coupling — upserter'lar SearchIndexerService'i tanimaz</li>
 *   <li>@TransactionalEventListener(AFTER_COMMIT) — tx rollback olsa
 *       ES'e yanlis veri gitmez</li>
 *   <li>@Async — DB transaction'ini bekletmez</li>
 *   <li>scorestv.elasticsearch.enabled=false ise listener bean'i yoktur,
 *       event yayinlansa bile NOOP olur</li>
 * </ul>
 *
 * <p>5 tipte type-safe sealed interface — switch exhaustive kontrol saglar.
 */
public sealed interface EntityIndexedEvent {

    record TeamIndexed(Team team) implements EntityIndexedEvent {}
    record LeagueIndexed(League league) implements EntityIndexedEvent {}
    record PlayerIndexed(Player player) implements EntityIndexedEvent {}
    record CountryIndexed(Country country) implements EntityIndexedEvent {}
    record FixtureIndexed(Fixture fixture) implements EntityIndexedEvent {}
}
