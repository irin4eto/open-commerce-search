package de.cxp.ocs.elasticsearch.prodset;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaticProductSetResolver implements ProductSetResolver {

	@Override
	public StaticProductSet resolve(final ProductSet productSet, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
		StaticProductSet staticSet = (StaticProductSet) productSet;
		
		IdsQueryBuilder requestedIds;
		if (excludedIds != null && excludedIds.size() > 0) {
			Set<String> filteredIds = new HashSet<String>(staticSet.getIds().length);
			for (String id : staticSet.getIds()) {
				if (!excludedIds.contains(id)) {
					filteredIds.add(id);
				}
			}
			requestedIds = QueryBuilders.idsQuery().addIds(filteredIds.toArray(new String[filteredIds.size()]));
		} else {
			requestedIds = QueryBuilders.idsQuery().addIds(staticSet.getIds());
		}
		
		try {
			SearchResponse searchResponse = searcher.executeSearchRequest(SearchSourceBuilder.searchSource()
					.query(requestedIds)
					.fetchSource(false)
					.size(requestedIds.ids().size()));
			if (searchResponse.getHits().getTotalHits().value == 0) {
				staticSet.setIds(new String[0]);
			}
			else if (searchResponse.getHits().getTotalHits().value < requestedIds.ids().size()
					&& searchResponse.getHits().getTotalHits().relation.equals(TotalHits.Relation.EQUAL_TO)) {
				staticSet.setIds(StreamSupport.stream(searchResponse.getHits().spliterator(), false)
						.map(hit -> hit.getId())
						.collect(Collectors.toList())
						.toArray(new String[0]));
					}
			else if (requestedIds.ids().size() < staticSet.getSize()) {
				staticSet.setIds(requestedIds.ids().toArray(new String[0]));
			}
		}
		catch (Exception e) {
			log.error("{} while verifying productSet ids. Won't verify.", e.getMessage());
		}
		return staticSet;
	}

	@Override
	public boolean runAsync() {
		return true;
	}

}
