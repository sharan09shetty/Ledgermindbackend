package com.ledgermind.ledgermindbackend.email.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.ledgermind.ledgermindbackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailClientFactory {

    private static final String APPLICATION_NAME = "LedgerMind";

    private final GoogleClientSecrets clientSecrets;

    /**
     * Builds a fresh GoogleCredential with no tokens set yet, but with the
     * client id/secret populated from credentials.json. Used both for
     * refreshing existing users' tokens and for completing the OAuth callback.
     */
    public GoogleCredential newCredential() throws Exception {
        GoogleClientSecrets.Details details = clientSecrets.getWeb() != null
                ? clientSecrets.getWeb()
                : clientSecrets.getInstalled();

        return new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(details.getClientId(), details.getClientSecret())
                .build();
    }

    public Gmail buildGmail(GoogleCredential credential) throws Exception {
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Builds a Gmail client for a user using their stored refresh token.
     * Always refreshes the access token, since this runs as part of a
     * periodic scan and access tokens are short-lived anyway.
     */
    public Gmail buildClientFor(User user) throws Exception {
        if (user.getGmailRefreshToken() == null || user.getGmailRefreshToken().isBlank()) {
            throw new IllegalStateException("User " + user.getId() + " has not connected Gmail");
        }

        GoogleCredential credential = newCredential();
        credential.setRefreshToken(user.getGmailRefreshToken());

        if (!credential.refreshToken()) {
            throw new IllegalStateException("Failed to refresh Gmail token for user " + user.getId());
        }

        return buildGmail(credential);
    }
}