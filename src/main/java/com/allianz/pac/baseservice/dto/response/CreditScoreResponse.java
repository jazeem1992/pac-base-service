package com.allianz.pac.baseservice.dto.response;

import java.time.Instant;

/**
 * Credit score of a customer as returned by the downstream credit-score service.
 * Additional fields sent by the downstream service are ignored.
 *
 * @param customerId  customer id
 * @param score       credit score
 * @param rating      rating band (e.g. {@code A}, {@code B})
 * @param evaluatedAt time the score was calculated
 */
public record CreditScoreResponse(Long customerId, Integer score, String rating, Instant evaluatedAt) {
}
