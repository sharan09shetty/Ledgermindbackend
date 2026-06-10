package com.ledgermind.ledgermindbackend.email.entity;

import com.ledgermind.ledgermindbackend.email.enums.Category;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_category_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCategoryMapping {

    @Id
    private String merchant;

    @Enumerated(EnumType.STRING)
    private Category category;
}
