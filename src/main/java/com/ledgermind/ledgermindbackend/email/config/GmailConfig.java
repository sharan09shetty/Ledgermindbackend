package com.ledgermind.ledgermindbackend.email.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class GmailConfig {

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Bean
    public GoogleClientSecrets googleClientSecrets() {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);

        GoogleClientSecrets secrets = new GoogleClientSecrets();
        secrets.setWeb(details);
        return secrets;
    }

    /**
     * Plain sign-in flow: identifies the user (email + name) only. No Gmail
     * access is requested here, so no refresh token is needed/stored — this
     * is just "online" access used once to read the profile.
     */
    @Bean
    @Qualifier("login")
    public GoogleAuthorizationCodeFlow loginAuthorizationCodeFlow(
            GoogleClientSecrets clientSecrets) throws Exception {

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                Set.of(
                        "openid",
                        "https://www.googleapis.com/auth/userinfo.email",
                        "https://www.googleapis.com/auth/userinfo.profile"
                )
        ).build();
    }

    /**
     * Gmail-linking flow: requested separately, after the user is already
     * logged in, from a "Connect Gmail" action in the UI. Requires offline
     * access + forced consent so we reliably get back a refresh token.
     */
    @Bean
    @Qualifier("gmail")
    public GoogleAuthorizationCodeFlow gmailAuthorizationCodeFlow(
            GoogleClientSecrets clientSecrets) throws Exception {

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                Set.of(GmailScopes.GMAIL_READONLY)
        )
                .setAccessType("offline")
                .build();
    }
}