package com.scanner.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceKlineDTO(Instant encerramento, BigDecimal precoFechamento) {}