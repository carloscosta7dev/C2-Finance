package com.scanner.financeiro.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Espelha o JSON de resposta de GET /api/v3/ticker/price da Binance:
 * {"symbol":"BTCUSDT","price":"63521.44000000"}
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) é uma defesa simples: se a
 * Binance adicionar campos novos no futuro, a desserialização não quebra.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTickerDTO(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("price") String price
) {}