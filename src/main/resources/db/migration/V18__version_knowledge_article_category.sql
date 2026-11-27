ALTER TABLE knowledge_article_versions ADD COLUMN category VARCHAR(80);

UPDATE knowledge_article_versions version
SET category = article.category
FROM knowledge_articles article
WHERE article.id = version.article_id;

ALTER TABLE knowledge_article_versions ALTER COLUMN category SET NOT NULL;
