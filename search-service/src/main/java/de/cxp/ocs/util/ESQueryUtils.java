package de.cxp.ocs.util;

import java.util.Collection;
import java.util.function.Function;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import de.cxp.ocs.elasticsearch.model.term.AssociatedTerm;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;

public class ESQueryUtils {

	/**
	 * @param termsUnique
	 *        a list of {@link QueryStringTerm}s
	 * @return
	 *         a string that can be used to label a ES query
	 */
	public static String getQueryLabel(Collection<QueryStringTerm> termsUnique) {
		StringBuilder queryLabel = new StringBuilder();
		for (QueryStringTerm qst : termsUnique) {
			queryLabel.append(' ');
			if (qst instanceof AssociatedTerm) {
				queryLabel.append(qst.toQueryString());
			}
			else {
				queryLabel.append(qst.getRawTerm());
			}
		}
		return queryLabel.toString().trim();
	}

	public static String getFuzzyTermLabel(AssociatedTerm correctedWord) {
		if (correctedWord.getRelatedTerms().size() == 0) return correctedWord.getRawTerm();
		StringBuilder fuzzyTermNotation = new StringBuilder("~")
				.append(correctedWord.getRawTerm())
				.append("=(");
		correctedWord.getRelatedTerms().keySet().forEach(rw -> fuzzyTermNotation.append(rw).append('/'));
		fuzzyTermNotation.setCharAt(fuzzyTermNotation.length() - 1, ')');
		return fuzzyTermNotation.toString();
	}

	/**
	 * If the inserted query is a BoolQueryBuilder, the queryBuilder is just returned. If not, it is wrapped in a
	 * Boolean Query with the query at the MUST clause.
	 * 
	 * @param query
	 * @return
	 */
	public static BoolQueryBuilder mapToBoolQueryBuilder(QueryBuilder query) {
		return mapToBoolQueryBuilder(query, q -> QueryBuilders.boolQuery().must(q));
	}

	/**
	 * If the inserted query is a BoolQueryBuilder, the queryBuilder is just returned. If not, the provided function is
	 * called to wrap the query into a BoolQueryBuilder.
	 * 
	 * @param query
	 * @param boolClauseFunction
	 * @return
	 */
	public static BoolQueryBuilder mapToBoolQueryBuilder(QueryBuilder query, Function<QueryBuilder, BoolQueryBuilder> boolClauseFunction) {
		if (query instanceof BoolQueryBuilder) {
			return (BoolQueryBuilder) query;
		}
		else {
			return boolClauseFunction.apply(query);
		}
	}

	/**
	 * Make sure both queries are combined as a boolean query with must-clauses.
	 * If one of them already is a boolean query with must clauses, the other
	 * one will be appended to it.
	 * 
	 * @param q1
	 *        first query
	 * @param q2
	 *        second query
	 * @return
	 *         q1 or q2 if one of them is null, otherwise a
	 *         {@link BoolQueryBuilder}
	 */
	public static QueryBuilder mergeQueries(QueryBuilder q1, QueryBuilder q2) {
		if (q1 == null) {
			return q2;
		}
		else if (q2 == null) {
			return q1;
		}
		else if (q1 instanceof BoolQueryBuilder && ((BoolQueryBuilder) q1).must().size() > 0) {
			((BoolQueryBuilder) q1).must(q2);
			return q1;
		}
		else if (q2 instanceof BoolQueryBuilder && ((BoolQueryBuilder) q2).must().size() > 0) {
			((BoolQueryBuilder) q2).must(q1);
			return q2;
		}
		else {
			return QueryBuilders.boolQuery()
					.must(q1)
					.must(q2);
		}
	}
}
