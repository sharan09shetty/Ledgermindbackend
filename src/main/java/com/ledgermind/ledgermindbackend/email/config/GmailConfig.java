package com.ledgermind.ledgermindbackend.email.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;

@Configuration
public class GmailConfig {

    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Bean
    public GoogleClientSecrets googleClientSecrets() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("credentials.json");

        if (inputStream == null) {
            throw new RuntimeException("credentials.json not found");
        }

        return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));
    }

    @Bean
    public GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow(GoogleClientSecrets clientSecrets) throws Exception {
        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientSecrets,
                Collections.singleton(GmailScopes.GMAIL_READONLY)
        )
                .setAccessType("offline")
                .build();
    }
}