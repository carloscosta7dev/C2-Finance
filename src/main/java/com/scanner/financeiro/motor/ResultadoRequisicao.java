package com.scanner.financeiro.motor;

import java.net.http.HttpHeaders;

/**
 * Resultado de uma requisição individual: ou ela teve uma resposta HTTP (com
 * código de status, corpo e cabeçalhos), ou falhou antes disso (erro != null,
 * codigoStatus == -1) — equivalente ao bloco "except httpx.RequestError" do
 * script Python, que soma em stats["Falhas de Conexão"].
 */
public record ResultadoRequisicao(int codigoStatus, String corpo, HttpHeaders cabecalhos, Throwable erro) {

    public boolean sucesso() {
        return erro == null && codigoStatus >= 200 && codigoStatus < 300;
    }

    public boolean limiteTaxaExcedido() {
        return codigoStatus == 429;
    }

    public boolean falhaDeConexao() {
        return erro != null;
    }
}