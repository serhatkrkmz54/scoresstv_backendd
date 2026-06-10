package com.scorestv.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PlayerDocRepository
        extends ElasticsearchRepository<PlayerDoc, Long> {}
