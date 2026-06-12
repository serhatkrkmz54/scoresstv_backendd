package com.scorestv.football.web.dto;

import java.io.Serializable;

/** Sol ray "Ülkeler" (milli takım) listesi öğesi. ID config'te (popular-team-ids). */
public record PopularTeamView(
        Long id,
        String name,
        String slug,
        String logo,
        String country
) implements Serializable {
}
