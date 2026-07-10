package com.ledgermind.ledgermindbackend.email.entity;

import com.ledgermind.ledgermindbackend.email.enums.Category;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * Learned merchant → category mapping, scoped per user: one user's category
 * correction must never re-categorize another user's transactions.
 */
@Entity
@Table(name = "merchant_category_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(MerchantCategoryMapping.Key.class)
public class MerchantCategoryMapping {

    @Id
    private UUID userId;

    @Id
    private String merchant;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID userId;
        private String merchant;
    }
}
