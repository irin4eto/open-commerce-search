package de.cxp.ocs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import de.cxp.ocs.api.indexer.ImportSession;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;
import de.cxp.ocs.model.params.SearchParams;
import de.cxp.ocs.model.params.SortOrder;
import de.cxp.ocs.model.params.Sorting;
import de.cxp.ocs.model.query.Query;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.model.result.FacetEntry;
import de.cxp.ocs.model.result.HierarchialFacetEntry;
import de.cxp.ocs.model.result.ResultHit;
import de.cxp.ocs.model.result.SearchResult;
import de.cxp.ocs.model.result.SearchResultSlice;

public class SerializationTest {

	// we don't have control over serialization, so we just can assume standard
	// serialization
	final ObjectMapper serializer = new ObjectMapper();

	final ObjectMapper deserializer = new ObjectMapper();

	/**
	 * special configuration for jackson object mapper to work with the
	 * defined models.
	 */
	@BeforeEach
	public void configureDeserialization() {
		deserializer.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		deserializer.registerModule(new ParameterNamesModule(Mode.PROPERTIES));
		deserializer.addMixIn(Facet.class, FacetMixin.class);
		deserializer.addMixIn(Attribute.class, WithTypeInfo.class);
		deserializer.addMixIn(Query.class, SingleStringArgsCreator.class);

		deserializer.addMixIn(FacetEntry.class, WithTypeInfo.class);
		deserializer.registerSubtypes(HierarchialFacetEntry.class);
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
	public static abstract class WithTypeInfo {}

	public static abstract class SingleStringArgsCreator {

		@JsonCreator
		SingleStringArgsCreator(String name) {}
	}

	public static abstract class DoubleStringArgsCreator {

		@JsonCreator
		DoubleStringArgsCreator(String name, String id) {}
	}
	
	public static abstract class FacetMixin {

		@JsonCreator
		FacetMixin(String name) {}

		@JsonIgnore
		abstract String getLabel();

		@JsonIgnore
		abstract String getType();
	}

	@ParameterizedTest
	@MethodSource("getSerializableObjects")
	public void testRoundTrip(Object serializable) throws IOException {
		String serialized = serializer.writeValueAsString(serializable);
		System.out.println(serialized);
		Object deserialized = deserializer.readValue(serialized, serializable.getClass());
		assertEquals(serializable, deserialized);
	}

	public static Stream<Object> getSerializableObjects() {
		return Stream.of(

				new Product("1").set("title", "master test"),

				new Product("2").set("title", "master 2")
						.set("string", "foo bar")
						.set("number", 12.5)
						.set("color", Attribute.of("blue"), Attribute.of("red"))
						.set("category", Attribute.of("Men", "_cat1"), Attribute.of("Shoes", "_cat1_1")),

				new Product("3")
						.set("title", "master category test")
						.set("category", Attribute.of("a"), Attribute.of("b")),

				new Product("4")
						.set("title", "master category test ")
						.set("category", Attribute.of("Fruit"), Attribute.of("Apple")),

				masterWithVariants(
						(Product) new Product("3").set("title", "master 2"),
						new Document("31"),
						new Document("32").set("price", 99.9).set("price.discount", 78.9),
						new Document("33").set("price", 45.6).set("type", "var1")),

				new ImportSession("foo-bar", "foo-bar-20191203"),

				new Sorting("title", SortOrder.ASC),

				new SearchParams()
						.setLimit(8)
						.setOffset(42)
						.withSorting(new Sorting("margin", SortOrder.DESC)),

							
				// color={red,black}/10<price<99&category={id1, id2}
				// param1=value1&color={red,black}&category...
				// param1=value&fh_location=//catalog/locale/$s=dress&
						
				// color=red,black/category=cat1/price=10.1,
						
				/*
				 Query - param=value&_s=shoes&_brand=adidas
				 Links - param=value&_s=shoes&_brand=adidas&_price=10,20
				 State - _category=cat1&_brand=adidas
				 
				 Query - param=value&q=/$s=shoes/brand=adidas
				 Links - param=value&q=/$s=shoes/brand=adidas/price=10,20
				 State - /category=cat1/brand=adidas


				 Query - param=value&q=shoes:brand
				 Links - param=value&q=/$s=shoes/brand=adidas/price=10,20
				 State - /category=cat1/brand=adidas
				 $s=dress/brand=adidas&fh_sort_by=-price,size
				 */
						
				new Facet("brand").addEntry("nike", 13, "brand=nike"),
				new Facet("categories")
						.addEntry(new HierarchialFacetEntry("a", 50, "categories=a").addChild(new FacetEntry("aa", 23, "categories=aa"))),

				new ResultHit()
						.setDocument(new Document("12").setData(Collections.singletonMap("title", "nice stuff")))
						.setIndex("de-de")
						.setMatchedQueries(new String[] { "nice" }),

				new SearchResultSlice()
						.setMatchCount(42)
						.setNextOffset(8),

				new SearchResult(),

				new SearchResult()
						.setSearchQuery("the answer")
						.setTookInMillis(400000000000L)
		);
	}

	private static Product masterWithVariants(Product masterProduct, Document... variantProducts) {
		masterProduct.setVariants(variantProducts);
		return masterProduct;
	}

}
