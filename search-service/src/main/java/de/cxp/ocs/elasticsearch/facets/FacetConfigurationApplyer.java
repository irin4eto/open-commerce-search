package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetFactory.getLabel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.search.aggregations.Aggregations;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConstants;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.query.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.util.SearchQueryBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FacetConfigurationApplyer {

	private final Map<String, FacetConfig>	facetsBySourceField	= new HashMap<>();
	private int								maxFacets;

	public FacetConfigurationApplyer(SearchConfiguration config) {
		maxFacets = config.getFacetConfiguration().getMaxFacets();
		for (FacetConfig facetConfig : config.getFacetConfiguration().getFacets()) {
			// TODO: null check: validate config at early stage!
			Field sourceField = config.getFieldConfiguration().getFields().get(facetConfig.getSourceField());

			// TODO: special handling for hard coded name of categories
			// facet in
			// index. Should be solved in a better way.
			String facetName;
			if (sourceField != null && FieldType.category.equals(sourceField.getType())) {
				facetName = FieldConstants.CATEGORY_FACET_DATA;
			}
			else {
				facetName = facetConfig.getSourceField();
			}

			if (facetsBySourceField.put(facetName,
					facetConfig) != null) {
				log.warn("multiple facets based on same source field are not supported!"
						+ " Overwriting facet config for source field {}",
						facetConfig.getSourceField());
			}
			// if (sourceField == null) {
			// log.warn("No source field with name {} for facet {}",
			// facetConfig.getSourceField(), facetConfig
			// .getLabel());
			// }
		}
	}

	public List<Facet> getFacets(Aggregations aggregations, List<FacetCreator> facetCreators, long matchCount,
			List<InternalResultFilter> filters, SearchQueryBuilder linkBuilder) {
		List<Facet> facets = new ArrayList<>();
		int actualMaxFacets = maxFacets;
		Set<String> duplicateFacets = new HashSet<>();

		// get filtered facets
		Set<String> appliedFilters = new HashSet<>();
		filters.forEach(rf -> appliedFilters.add(rf.getField()));

		for (FacetCreator fc : facetCreators) {
			Collection<Facet> createdFacets = fc.createFacets(filters, aggregations, linkBuilder);
			for (Facet f : createdFacets) {
				// skip facets with the identical name
				if (!duplicateFacets.add(getLabel(f))) {
					log.warn("duplicate facet with label " + getLabel(f));
					continue;
				}
				if (appliedFilters.contains(f.getFieldName())) {
					f.setFiltered(true);
				}
				else if (f.getEntries().size() == 1
						&& f.getAbsoluteFacetCoverage() == matchCount) {
							log.debug("removed facet with label {} because its only element covered the whole result", getLabel(f));
							continue;
						}

				FacetConfig facetConfig = facetsBySourceField.get(f.getFieldName());
				if (facetConfig != null) {
					if (facetConfig.isExcludeFromFacetLimit()) actualMaxFacets++;
				}
				facets.add(f);
			}
		}

		// sort by order and facet coverage
		facets.sort(new Comparator<Facet>() {

			@Override
			public int compare(Facet o1, Facet o2) {
				// prio 1: configured order
				int compare = Byte.compare(FacetFactory.getOrder(o1), FacetFactory.getOrder(o2));
				// prio 2: prefer facets with filtered value
				if (compare == 0) {
					compare = Boolean.compare(o2.isFiltered(), o1.isFiltered());
				}
				// prio 3: prefer higher facet coverage (reverse natural order)
				if (compare == 0) {
					compare = Long.compare(o2.getAbsoluteFacetCoverage(), o1.getAbsoluteFacetCoverage());
				}
				return compare;
			}

		});

		return facets.size() > actualMaxFacets ? facets.subList(0, actualMaxFacets) : facets;
	}
}
