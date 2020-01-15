package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class HierarchialFacetEntry extends FacetEntry {

	public HierarchialFacetEntry(String key, long docCount, String link) {
		super(key, docCount, link);
	}

	/**
	 * Child facet entries to that particular facet. The child facets again
	 * could be HierarchialFacetEntries.
	 */
	public List<FacetEntry> children = new ArrayList<>();

	public HierarchialFacetEntry addChild(final FacetEntry child) {
		children.add(child);
		return this;
	}
}
