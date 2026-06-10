package com.scorestv.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TeamDocRepository
        extends ElasticsearchRepository<TeamDoc, Long> {}
