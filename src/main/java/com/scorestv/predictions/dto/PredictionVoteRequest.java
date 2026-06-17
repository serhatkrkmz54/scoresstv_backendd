package com.scorestv.predictions.dto;

/** Oy isteği — anonim istemci kimliği + seçim (HOME/DRAW/AWAY). */
public record PredictionVoteRequest(String voterId, String choice) {}
