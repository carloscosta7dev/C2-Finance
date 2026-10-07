package com.scanner.financeiro.analise;

import com.scanner.financeiro.modelo.Sinal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalisadorTecnicoTest {

    @Test
    void calculaSmaComJanelaDeslizanteEPreenchePeriodoInsuficienteComNulos() {
        List<BigDecimal> resultado = AnalisadorTecnico.calcularSMA(precos("1", "2", "3", "4"), 3);

        assertEquals(4, resultado.size());
        assertNull(resultado.get(0));
        assertNull(resultado.get(1));
        assertEquals(new BigDecimal("2.00000000"), resultado.get(2));
        assertEquals(new BigDecimal("3.00000000"), resultado.get(3));
    }

    @Test
    void calculaEmaSemeandoPelaSma() {
        List<BigDecimal> resultado = AnalisadorTecnico.calcularEMA(precos("2", "4", "6"), 2);

        assertNull(resultado.get(0));
        assertEquals(new BigDecimal("3.00000000"), resultado.get(1));
        assertEquals(new BigDecimal("5.00000001"), resultado.get(2));
    }

    @Test
    void retornaSomenteNulosQuandoAindaNaoHaDadosSuficientesParaEma() {
        List<BigDecimal> resultado = AnalisadorTecnico.calcularEMA(precos("2", "4"), 3);

        assertEquals(2, resultado.size());
        assertNull(resultado.get(0));
        assertNull(resultado.get(1));
    }

    @Test
    void detectaCompraVendaEAusenciaDeCruzamento() {
        assertEquals(Sinal.COMPRA, AnalisadorTecnico.detectarCruzamento(
                decimal("9"), decimal("10"), decimal("11"), decimal("10")));
        assertEquals(Sinal.VENDA, AnalisadorTecnico.detectarCruzamento(
                decimal("11"), decimal("10"), decimal("9"), decimal("10")));
        assertEquals(Sinal.MANTER, AnalisadorTecnico.detectarCruzamento(
                decimal("11"), decimal("10"), decimal("12"), decimal("10")));
        assertEquals(Sinal.MANTER, AnalisadorTecnico.detectarCruzamento(
                null, decimal("10"), decimal("11"), decimal("10")));
    }

    @Test
    void validaListaEPeriodo() {
        assertThrows(NullPointerException.class, () -> AnalisadorTecnico.calcularSMA(null, 2));
        assertThrows(IllegalArgumentException.class, () -> AnalisadorTecnico.calcularEMA(precos("1"), 0));
    }

    private static List<BigDecimal> precos(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    private static BigDecimal decimal(String valor) {
        return new BigDecimal(valor);
    }
}