package com.scanner.financeiro.alerta;

/**
 * Abstração de um canal de envio de alertas. Permite trocar console por
 * e-mail (ou qualquer outro canal — Telegram, Slack, etc.) sem tocar no
 * resto do sistema: o MonitorPrecos só conhece esta interface.
 */
public interface CanalAlerta {
    void enviar(String titulo, String mensagem);
}
