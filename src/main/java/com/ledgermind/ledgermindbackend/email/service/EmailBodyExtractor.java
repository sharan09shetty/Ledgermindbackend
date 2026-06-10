package com.ledgermind.ledgermindbackend.email.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EmailBodyExtractor {

    public static String extractBody(Message message) {

        return extractPart(message.getPayload());
    }

    private static String extractPart(MessagePart part) {

        if ("text/html".equalsIgnoreCase(part.getMimeType())
                && part.getBody() != null
                && part.getBody().getData() != null) {

            String html = new String(
                    Base64.getUrlDecoder()
                            .decode(part.getBody().getData()),
                    StandardCharsets.UTF_8
            );

            return Jsoup.parse(html).text();
        }

        if (part.getParts() != null) {

            for (MessagePart child : part.getParts()) {

                String result = extractPart(child);

                if (!result.isBlank()) {
                    return result;
                }
            }
        }

        return "";
    }
}