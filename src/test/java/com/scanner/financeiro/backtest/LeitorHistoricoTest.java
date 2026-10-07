package com.scanner.financeiro.backtest;

import com.scanner.financeiro.modelo.PrecoHistorico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorHistoricoTest {

    @TempDir
    Path diretorioTemporario;

    @Test
    void leCsvComCabecalhoIgnoraLinhaInvalidaEOrdenaPorData() throws Exception {
        Path arquivo = diretorioTemporario.resolve("historico.csv");
        Files.writeString(arquivo,
                "data,preco_fechamento\n2025-01-02,11.50\nlinha invalida\n2025-01-01,10.00\n");
        PrintStream erroOriginal = System.err;
        ByteArrayOutputStream capturaErro = new ByteArrayOutputStream();
        List<PrecoHistorico> precos;
        try {
            System.setErr(new PrintStream(capturaErro, true, StandardCharsets.UTF_8));
            precos = LeitorHistorico.lerCsv(arquivo);
        } finally {
            System.setErr(erroOriginal);
        }

        assertEquals(2, precos.size());
        assertEquals(LocalDate.parse("2025-01-01"), precos.get(0).data());
        assertEquals(new BigDecimal("10.00"), precos.get(0).precoFechamento());
        assertEquals(LocalDate.parse("2025-01-02"), precos.get(1).data());
        assertTrue(capturaErro.toString(StandardCharsets.UTF_8).contains("1 linha(s) ignorada(s)"));
    }

    @Test
    void aceitaCsvSemCabecalho() throws Exception {
        Path arquivo = diretorioTemporario.resolve("sem-cabecalho.csv");
        Files.writeString(arquivo, "2025-01-01,10\n");

        List<PrecoHistorico> precos = LeitorHistorico.lerCsv(arquivo);

        assertEquals(1, precos.size());
        assertEquals(new BigDecimal("10"), precos.get(0).precoFechamento());
    }
}