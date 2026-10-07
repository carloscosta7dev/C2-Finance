package com.scanner.financeiro.backtest;

import com.scanner.financeiro.modelo.PrecoHistorico;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Leitor de CSV de histórico de preços (Fase 4.3). Formato esperado:
 * "data,preco_fechamento", com data em yyyy-MM-dd. Cabeçalho é opcional —
 * é detectado automaticamente (ver pareceCabecalho). Linhas malformadas são
 * ignoradas com um aviso em vez de derrubar a leitura inteira: dados
 * históricos reais raramente são 100% limpos.
 */
public final class LeitorHistorico {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ISO_LOCAL_DATE;

    private LeitorHistorico() {}

    public static List<PrecoHistorico> lerCsv(Path caminhoArquivo) throws IOException {
        List<String> linhas = Files.readAllLines(caminhoArquivo);
        List<PrecoHistorico> precos = new ArrayList<>();
        if (linhas.isEmpty()) {
            return precos;
        }

        int indiceInicial = pareceCabecalho(linhas.get(0)) ? 1 : 0;
        int linhasIgnoradas = 0;

        for (int i = indiceInicial; i < linhas.size(); i++) {
            String linha = linhas.get(i).strip();
            if (linha.isEmpty()) continue;

            String[] campos = linha.split(",");
            if (campos.length < 2) {
                linhasIgnoradas++;
                continue;
            }

            try {
                LocalDate data = LocalDate.parse(campos[0].strip(), FORMATO_DATA);
                BigDecimal preco = new BigDecimal(campos[1].strip());
                precos.add(new PrecoHistorico(data, preco));
            } catch (DateTimeParseException | NumberFormatException e) {
                linhasIgnoradas++;
            }
        }

        precos.sort(Comparator.comparing(PrecoHistorico::data));
        if (linhasIgnoradas > 0) {
            System.err.printf("Aviso: %d linha(s) ignorada(s) por formato inválido em %s%n", linhasIgnoradas, caminhoArquivo);
        }
        return precos;
    }

    private static boolean pareceCabecalho(String primeiraLinha) {
        String[] campos = primeiraLinha.split(",");
        if (campos.length < 2) return false;
        try {
            new BigDecimal(campos[1].strip());
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}