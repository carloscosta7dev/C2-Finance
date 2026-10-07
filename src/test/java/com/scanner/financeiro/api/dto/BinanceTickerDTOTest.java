package com.scanner.financeiro.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceTickerDTOTest {

    @Test
    void desserializaCamposConhecidosEIgnoraCamposFuturos() throws Exception {
        BinanceTickerDTO dto = new ObjectMapper().readValue(
                "{\"symbol\":\"BTCUSDT\",\"price\":\"63521.44000000\",\"extra\":true}",
                BinanceTickerDTO.class);

        assertEquals("BTCUSDT", dto.symbol());
        assertEquals("63521.44000000", dto.price());
    }
}