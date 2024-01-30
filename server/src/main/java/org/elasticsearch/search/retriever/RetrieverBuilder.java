/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.retriever;

import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.SuggestingErrorOnUnknown;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.AbstractObjectParser;
import org.elasticsearch.xcontent.FilterXContentParserWrapper;
import org.elasticsearch.xcontent.NamedObjectNotFoundException;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentLocation;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class RetrieverBuilder<RB extends RetrieverBuilder<RB>> {

    public static final ParseField PRE_FILTER_FIELD = new ParseField("filter");

    protected static void declareBaseParserFields(
        String name,
        AbstractObjectParser<? extends RetrieverBuilder<?>, RetrieverParserContext> parser
    ) {
        parser.declareObjectArray(RetrieverBuilder::preFilterQueryBuilders, (p, c) -> {
            QueryBuilder preFilterQueryBuilder = AbstractQueryBuilder.parseTopLevelQuery(p, c::trackQueryUsage);
            c.trackSectionUsage(name + ":" + PRE_FILTER_FIELD.getPreferredName());
            return preFilterQueryBuilder;
        }, PRE_FILTER_FIELD);
    }

    public static RetrieverBuilder<?> parseTopLevelRetrieverBuilder(XContentParser parser, RetrieverParserContext context)
        throws IOException {
        parser = new FilterXContentParserWrapper(parser) {

            int nestedDepth = 0;

            @Override
            public <T> T namedObject(Class<T> categoryClass, String name, Object context) throws IOException {
                if (categoryClass.equals(QueryBuilder.class)) {
                    nestedDepth++;

                    if (nestedDepth > 2) {
                        throw new IllegalArgumentException(
                            "the nested depth of the [" + name + "] retriever exceeds the maximum nested depth [2] for retrievers"
                        );
                    }
                }

                T namedObject = getXContentRegistry().parseNamedObject(categoryClass, name, this, context);

                if (categoryClass.equals(RetrieverBuilder.class)) {
                    nestedDepth--;
                }

                return namedObject;
            }
        };

        return parseInnerRetrieverBuilder(parser, context);
    }

    protected static RetrieverBuilder<?> parseInnerRetrieverBuilder(XContentParser parser, RetrieverParserContext context)
        throws IOException {
        Objects.requireNonNull(context);

        if (parser.currentToken() != XContentParser.Token.START_OBJECT && parser.nextToken() != XContentParser.Token.START_OBJECT) {
            throw new ParsingException(
                parser.getTokenLocation(),
                "retriever malformed, must start with [" + XContentParser.Token.START_OBJECT + "]"
            );
        }

        if (parser.nextToken() == XContentParser.Token.END_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(), "retriever malformed, empty clause found");
        }

        if (parser.currentToken() != XContentParser.Token.FIELD_NAME) {
            throw new ParsingException(
                parser.getTokenLocation(),
                "retriever malformed, no field after [" + XContentParser.Token.START_OBJECT + "]"
            );
        }

        String retrieverName = parser.currentName();

        if (parser.nextToken() != XContentParser.Token.START_OBJECT) {
            throw new ParsingException(
                parser.getTokenLocation(),
                "[" + retrieverName + "] retriever malformed, no [" + XContentParser.Token.START_OBJECT + "] after retriever name"
            );
        }

        RetrieverBuilder<?> retrieverBuilder;

        try {
            retrieverBuilder = parser.namedObject(RetrieverBuilder.class, retrieverName, context);
            context.trackSectionUsage(retrieverName);
        } catch (NamedObjectNotFoundException nonfe) {
            String message = String.format(
                Locale.ROOT,
                "unknown retriever [%s]%s",
                retrieverName,
                SuggestingErrorOnUnknown.suggest(retrieverName, nonfe.getCandidates())
            );

            throw new ParsingException(new XContentLocation(nonfe.getLineNumber(), nonfe.getColumnNumber()), message, nonfe);
        }

        if (parser.currentToken() != XContentParser.Token.END_OBJECT) {
            throw new ParsingException(
                parser.getTokenLocation(),
                "["
                    + retrieverName
                    + "] malformed retriever, expected ["
                    + XContentParser.Token.END_OBJECT
                    + "] but found ["
                    + parser.currentToken()
                    + "]"
            );
        }

        if (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            throw new ParsingException(
                parser.getTokenLocation(),
                "["
                    + retrieverName
                    + "] malformed retriever, expected ["
                    + XContentParser.Token.END_OBJECT
                    + "] but found ["
                    + parser.currentToken()
                    + "]"
            );
        }

        return retrieverBuilder;
    }

    protected List<QueryBuilder> preFilterQueryBuilders = new ArrayList<>();

    public List<QueryBuilder> preFilterQueryBuilders() {
        return preFilterQueryBuilders;
    }

    @SuppressWarnings("unchecked")
    public RB preFilterQueryBuilders(List<QueryBuilder> preFilterQueryBuilders) {
        this.preFilterQueryBuilders = preFilterQueryBuilders;
        return (RB) this;
    }

    public final void extractToSearchSourceBuilder(SearchSourceBuilder searchSourceBuilder) {
        doExtractToSearchSourceBuilder(searchSourceBuilder);
    }

    public abstract void doExtractToSearchSourceBuilder(SearchSourceBuilder searchSourceBuilder);
}
