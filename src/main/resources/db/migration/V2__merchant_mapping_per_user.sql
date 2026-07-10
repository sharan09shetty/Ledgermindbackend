-- Merchant→category mappings were global: any user's correction changed
-- categorization for every user. Re-key them per user. Existing global
-- mappings are copied to every existing user, which preserves the effective
-- categorization behavior each user saw before this migration.

CREATE TABLE merchant_category_mapping_new (
    user_id UUID NOT NULL REFERENCES users(id),
    merchant VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, merchant)
);

INSERT INTO merchant_category_mapping_new (user_id, merchant, category)
SELECT u.id, m.merchant, m.category
FROM users u
CROSS JOIN merchant_category_mapping m;

DROP TABLE merchant_category_mapping;

ALTER TABLE merchant_category_mapping_new RENAME TO merchant_category_mapping;
