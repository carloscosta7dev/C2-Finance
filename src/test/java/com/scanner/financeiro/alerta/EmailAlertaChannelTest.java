package com.scanner.financeiro.alerta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAlertaChannelTest {

    @Test
    void exigeCamposDeConfiguracaoNaoNulos() {
        assertThrows(NullPointerException.class,
                () -> new EmailAlertaChannel(null, 587, "user", "pass", "to@example.com"));
        assertThrows(NullPointerException.class,
                () -> new EmailAlertaChannel("smtp.example.com", 587, null, "pass", "to@example.com"));
        assertThrows(NullPointerException.class,
                () -> new EmailAlertaChannel("smtp.example.com", 587, "user", null, "to@example.com"));
        assertThrows(NullPointerException.class,
                () -> new EmailAlertaChannel("smtp.example.com", 587, "user", "pass", null));
    }
}