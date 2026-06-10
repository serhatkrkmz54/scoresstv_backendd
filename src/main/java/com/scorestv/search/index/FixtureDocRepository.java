package com.scorestv.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FixtureDocRepository
        extends ElasticsearchRepository<FixtureDoc, Long> {}
