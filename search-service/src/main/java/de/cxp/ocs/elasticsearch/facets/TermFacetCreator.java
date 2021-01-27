package de.cxp.ocs.elasticsearch.facets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

import de.cxp.ocs.config.FacetConfiguration;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.elasticsearch.query.FiltersBuilder;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class TermFacetCreator implements NestedFacetCreator {

	private static String	GENERAL_TERM_FACET_AGG	= "_term_facet";
	private static String	FACET_NAMES_AGG			= "_names";
	private static String	FACET_VALUES_AGG		= "_values";

	private final Map<String, FacetConfig> facetsBySourceField = new HashMap<>();

	public TermFacetCreator(FacetConfiguration facetConf) {
		facetConf.getFacets().forEach(fc -> facetsBySourceField.put(fc.getSourceField(), fc));
	}

	@Setter
	private int maxFacets = 5;

	@Setter
	private int maxFacetValues = 100;

	@Setter
	private NestedFacetCountCorrector nestedFacetCorrector = null;

	@Override
	public AbstractAggregationBuilder<?> buildAggregation(FiltersBuilder filters) {
		// TODO: for multi-select facets, filter facets accordingly

		String nestedPathPrefix = "";
		if (nestedFacetCorrector != null) nestedPathPrefix = nestedFacetCorrector.getNestedPathPrefix();
		nestedPathPrefix += FieldConstants.TERM_FACET_DATA;

		TermsAggregationBuilder valueAggBuilder = AggregationBuilders.terms(FACET_VALUES_AGG)
				.field(nestedPathPrefix + ".value")
				.size(maxFacetValues);
		if (nestedFacetCorrector != null) nestedFacetCorrector.correctValueAggBuilder(valueAggBuilder);

		List<KeyedFilter> facetFilters = NestedFacetCreator.getAggregationFilters(filters, nestedPathPrefix + ".name");

		return AggregationBuilders.nested(GENERAL_TERM_FACET_AGG, nestedPathPrefix)
				.subAggregation(
						AggregationBuilders.filters(FILTERED_AGG, facetFilters.toArray(new KeyedFilter[0]))
								.subAggregation(
										AggregationBuilders.terms(FACET_NAMES_AGG)
												.field(nestedPathPrefix + ".name")
												.size(maxFacets)
												.subAggregation(valueAggBuilder)));
	}

	@Override
	public Collection<Facet> createFacets(List<InternalResultFilter> filters, Aggregations aggResult, SearchQueryBuilder linkBuilder) {
		// TODO: optimize SearchParams object to avoid such index creation!
		Map<String, InternalResultFilter> filtersByName = new HashMap<>();
		filters.forEach(p -> filtersByName.put(p.getField(), p));

		ParsedFilters filtersAgg = ((Nested) aggResult.get(GENERAL_TERM_FACET_AGG)).getAggregations().get(FILTERED_AGG);
		List<Facet> extractedFacets = new ArrayList<>();
		for (org.elasticsearch.search.aggregations.bucket.filter.Filters.Bucket filterBucket : filtersAgg.getBuckets()) {
			Terms facetNames = filterBucket.getAggregations().get(FACET_NAMES_AGG);
			extractedFacets.addAll(extractTermFacets(facetNames, filtersByName, linkBuilder));
		}

		return extractedFacets;
	}

	private List<Facet> extractTermFacets(Terms facetNames, Map<String, InternalResultFilter> filtersByName, SearchQueryBuilder linkBuilder) {

		List<Facet> termFacets = new ArrayList<>();
		for (Bucket facetNameBucket : facetNames.getBuckets()) {
			String facetName = facetNameBucket.getKeyAsString();

			FacetConfig facetConfig = facetsBySourceField.get(facetName);
			if (facetConfig == null) facetConfig = new FacetConfig(facetName, facetName);

			Facet facet = FacetFactory.create(facetConfig, "text");

			// TODO: this code chunk could be abstracted together with
			// NumberFacetCreator
			InternalResultFilter facetFilter = filtersByName.get(facetName);
			if (facetFilter != null && facetFilter instanceof TermResultFilter) {
				facet.setFiltered(true);
				// FIXME: create deselect links for selected facet entry
				if (facetConfig.isMultiSelect() || facetConfig.isShowUnselectedOptions()) {
					fillFacet(facet, facetNameBucket, facetConfig, linkBuilder);
				}
				else {
					fillSingleSelectFacet(facetNameBucket, facet, (TermResultFilter) facetFilter, facetConfig, linkBuilder);
				}
			}
			else {
				// unfiltered facet
				fillFacet(facet, facetNameBucket, facetConfig, linkBuilder);
			}

			termFacets.add(facet);
		}

		return termFacets;
	}

	private void fillSingleSelectFacet(Bucket facetNameBucket, Facet facet, TermResultFilter facetFilter, FacetConfig facetConfig,
			SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		long absDocCount = 0;
		for (String filterValue : facetFilter.getValues()) {
			Bucket elementBucket = facetValues.getBucketByKey(filterValue);
			if (elementBucket != null) {
				long docCount = getDocumentCount(elementBucket);
				facet.addEntry(buildFacetEntry(facetConfig, filterValue, docCount, linkBuilder));
				absDocCount += docCount;
			}
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private void fillFacet(Facet facet, Bucket facetNameBucket, FacetConfig facetConfig, SearchQueryBuilder linkBuilder) {
		Terms facetValues = ((Terms) facetNameBucket.getAggregations().get(FACET_VALUES_AGG));
		long absDocCount = 0;
		for (Bucket valueBucket : facetValues.getBuckets()) {
			long docCount = getDocumentCount(valueBucket);
			facet.addEntry(buildFacetEntry(facetConfig, valueBucket.getKeyAsString(), docCount, linkBuilder));
			absDocCount += docCount;
		}
		facet.setAbsoluteFacetCoverage(absDocCount);
	}

	private FacetEntry buildFacetEntry(FacetConfig facetConfig, String filterValue, long docCount, SearchQueryBuilder linkBuilder) {
		boolean isSelected = linkBuilder.isFilterSelected(facetConfig, filterValue);
		return new FacetEntry(
				filterValue,
				null, // TODO: fetch IDS
				docCount,
				isSelected ? linkBuilder.withoutFilterAsLink(facetConfig, filterValue) : linkBuilder.withFilterAsLink(facetConfig, filterValue),
				isSelected);
	}

	private long getDocumentCount(Bucket valueBucket) {
		long docCount = nestedFacetCorrector != null
				? nestedFacetCorrector.getCorrectedDocumentCount(valueBucket)
				: valueBucket.getDocCount();
		return docCount;
	}

}
