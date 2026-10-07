package com.scanner.financeiro.alerta;

/** Imprime o alerta no console com destaque visual (cores ANSI) — Fase 4.2. */
public class ConsoleAlertaChannel implements CanalAlerta {

    private static final String VERMELHO = "\u001B[31m";
    private static final String AMARELO = "\u001B[33m";
    private static final String NEGRITO = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    @Override
    public void enviar(String titulo, String mensagem) {
        String linha = "=".repeat(60);
        System.out.println(AMARELO + NEGRITO + linha + RESET);
        System.out.println(VERMELHO + NEGRITO + "ALERTA: " + titulo + RESET);
        System.out.println(mensagem);
        System.out.println(AMARELO + NEGRITO + linha + RESET);
    }
}