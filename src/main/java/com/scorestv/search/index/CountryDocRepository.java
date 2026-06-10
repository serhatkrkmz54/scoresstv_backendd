package com.scorestv.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CountryDocRepository
        extends ElasticsearchRepository<CountryDoc, Long> {}
