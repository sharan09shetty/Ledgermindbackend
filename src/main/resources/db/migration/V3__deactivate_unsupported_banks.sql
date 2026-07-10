-- Only HDFC has a working transaction parser today. The /banks endpoint only
-- returns active banks, so deactivating the others hides them from the UI
-- until their parsers exist (rather than deleting the rows and losing the
-- sender-email config).
UPDATE bank SET active = false WHERE code IN ('ICICI', 'SBI');
