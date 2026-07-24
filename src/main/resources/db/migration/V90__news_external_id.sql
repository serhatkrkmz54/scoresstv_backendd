-- Haber içe aktarma (agregatör) — harici kaynak makale kimliği ile tekilleştirme.
-- MANUAL haberlerde external_id NULL kalır; yalnız içe aktarılan haberlerde dolar.
-- (source, external_id) çifti benzersiz → aynı haber iki kez DRAFT açılmaz.
ALTER TABLE news_articles ADD COLUMN external_id VARCHAR(255);

-- Kısmi benzersiz index: yalnız external_id dolu satırlar için (MANUAL haberler
-- external_id=NULL olduğundan kısıtlanmaz — Postgres NULL'ları benzersizlikte
-- ayrı sayar ama açıkça WHERE ile daraltıyoruz).
CREATE UNIQUE INDEX uq_news_source_external
    ON news_articles (source, external_id)
    WHERE external_id IS NOT NULL;
