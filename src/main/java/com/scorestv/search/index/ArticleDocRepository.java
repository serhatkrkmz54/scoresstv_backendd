package com.scorestv.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleDocRepository
        extends ElasticsearchRepository<ArticleDoc, Long> {}
