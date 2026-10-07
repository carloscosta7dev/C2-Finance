package com.scanner.financeiro.alerta;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Objects;
import java.util.Properties;

/**
 * Envia alertas por e-mail via SMTP (Jakarta Mail) — Fase 4.2. Requer as
 * dependências jakarta.mail-api + angus-mail declaradas no pom.xml.
 *
 * NUNCA coloque usuário/senha diretamente no código-fonte. Use
 * {@link #apartirDeVariaveisAmbiente()}, que lê as credenciais do ambiente.
 */
public class EmailAlertaChannel implements CanalAlerta {

    private final String host;
    private final int porta;
    private final String usuario;
    private final String senha;
    private final String destinatario;

    public EmailAlertaChannel(String host, int porta, String usuario, String senha, String destinatario) {
        this.host = Objects.requireNonNull(host);
        this.porta = porta;
        this.usuario = Objects.requireNonNull(usuario);
        this.senha = Objects.requireNonNull(senha);
        this.destinatario = Objects.requireNonNull(destinatario);
    }

    /**
     * Lê SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS e ALERTA_DESTINATARIO do
     * ambiente. Lança IllegalStateException se alguma variável obrigatória faltar.
     */
    public static EmailAlertaChannel apartirDeVariaveisAmbiente() {
        return new EmailAlertaChannel(
                exigirVariavel("SMTP_HOST"),
                Integer.parseInt(exigirVariavel("SMTP_PORT")),
                exigirVariavel("SMTP_USER"),
                exigirVariavel("SMTP_PASS"),
                exigirVariavel("ALERTA_DESTINATARIO")
        );
    }

    private static String exigirVariavel(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + nome);
        }
        return valor;
    }

    @Override
    public void enviar(String titulo, String mensagem) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(porta));

        Session sessao = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(usuario, senha);
            }
        });

        try {
            Message email = new MimeMessage(sessao);
            email.setFrom(new InternetAddress(usuario));
            email.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            email.setSubject("[ALERTA] " + titulo);
            email.setText(mensagem);
            Transport.send(email);
        } catch (MessagingException e) {
            throw new RuntimeException("Falha ao enviar e-mail de alerta", e);
        }
    }
}