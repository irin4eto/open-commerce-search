package de.cxp.ocs.model.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class HierarchialFacetEntry extends FacetEntry {

	public final String _type = "HierarchialFacetEntry";

	@Getter
	private List<FacetEntry> children = new ArrayList<>();

	@Setter
	@Getter
	private String path;

	private final Map<String, FacetEntry> childIndex = new HashMap<>();

	public HierarchialFacetEntry(String key, long docCount, String link) {
		super(key, docCount, link);
	}

	public HierarchialFacetEntry setChildren(FacetEntry[] children) {
		for (FacetEntry c : children) {
			this.addChild(c);
		}
		return this;
	}

	public HierarchialFacetEntry addChild(final FacetEntry child) {
		children.add(child);
		childIndex.put(child.getKey(), child);
		return this;
	}

	public FacetEntry getChildByKey(String key) {
		return childIndex.get(key);
	}

}
