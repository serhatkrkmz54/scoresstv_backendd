package com.scorestv.volleyball.web.dto;

/** Web sol ray populer voleybol takimi satiri — basketbol esi. */
public record VolleyballPopularTeamView(
        Long id,
        String name,
        String slug,
        String logo) {}
