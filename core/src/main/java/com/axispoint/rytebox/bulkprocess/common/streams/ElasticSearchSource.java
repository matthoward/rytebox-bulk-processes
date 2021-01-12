package com.axispoint.rytebox.bulkprocess.common.streams;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.Outlet;
import akka.stream.SourceShape;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.AsyncCallback;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.GraphStageLogicWithLogging;
import akka.stream.stage.GraphStageWithMaterializedValue;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import scala.Tuple2;

/**
 * A continuous stream from an ES search. Will fetch all matching docs using an efficient search_after API. Can be resumed by
 * capturing the last SearchHit getSortValues and passing it back in to startingSearchAfter.
 *
 * See https://www.elastic.co/guide/en/elasticsearch/reference/7.9/paginate-search-results.html#search-after
 *
 * Code basically stolen from the official Akka Alpakka Elastic Source:
 * https://github.com/akka/alpakka/blob/master/elasticsearch/src/main/scala/akka/stream/alpakka/elasticsearch/impl/ElasticsearchSourceStage.scala
 *
 * Key differences:
 *  - uses search_after instead of scroll ... this was the primary reason not to use the alpakka connector
 *      - the scroll API has some nice features and is a little more efficient than search_after for very large results
 *          however it isn't re-entrant so if we run this in a lambda with a timelimit it would lose data that had been
 *          pre-fetched but not consumed. In the future we may switch to the alpakka connector if we can find or contribute a
 *          workaround/fix for this.
 *  - uses the ES High Level rest client instead of the low-level (also the alpakka client requires an old version of the client 6.3.2)
 *  - doesn't handle any marshalling of the data... just gives the raw searchhit and doesn't fetch the source data so really only the IDs are returned
 *
 */
@Slf4j
public class ElasticSearchSource extends GraphStageWithMaterializedValue<SourceShape<List<SearchHit>>, CompletionStage<Object[]>> {
    private static final int MAX_PAGE_SIZE = 1000;
    private final List<Pair<String, Optional<SortOrder>>> DEFAULT_SORT = List.of(Pair.create("id", Optional.empty()));

      private final Outlet<List<SearchHit>> out = Outlet.create("ElasticSearchSource.out");
      private final SourceShape<List<SearchHit>> shape = SourceShape.of(out);


    private final RestHighLevelClient esClient;
    private final String indexName;
    private final ObjectNode query;
    private final Optional<Object[]> startingSearchAfter;
    private final int pageSize;
    private final List<Pair<String, Optional<SortOrder>>> sort;


    public ElasticSearchSource(RestHighLevelClient esClient,
                               String indexName,
                               ObjectNode query,
                               Optional<Object[]> searchAfter,
                               int pageSize,
                               Optional<List<Pair<String, Optional<SortOrder>>>> sort) {
        this.esClient = esClient;
        this.indexName = indexName;
        this.query = query;
        this.startingSearchAfter = searchAfter;
        this.pageSize = pageSize;
        this.sort = sort.orElse(DEFAULT_SORT);
    }


//
//    @Override
//    public Tuple2<GraphStageLogic, Optional<List<Object>>> createLogicAndMaterializedValue(Attributes inheritedAttributes,
//                                                                                    Materializer materializer) {
//        return new GraphStageWithMaterializedValue<>(inheritedAttributes, materializer) {
//
//            @Override
//            public SourceShape<List<SearchHit>> shape() {
//                return shape;
//            }
//
//            @Override
//            public Tuple2<GraphStageLogic, Object> createLogicAndMaterializedValue( Attributes inheritedAttributes)
//                    throws Exception, Exception {
//                return null;
//            }
//        };
//    }
//
//    @Override
//    public Tuple2<GraphStageLogic, Optional<List<Object>>> createLogicAndMaterializedValue(Attributes inheritedAttributes) throws Exception, Exception {
//        return null;
 //   }

    //@Override
    public Tuple2<GraphStageLogic, CompletionStage<Object[]>> createLogicAndMaterializedValue(Attributes inheritedAttributes) {

        if (pageSize <=0 || pageSize >= MAX_PAGE_SIZE) throw new IllegalArgumentException("pageSize ("+pageSize+") must be between 0 and " + MAX_PAGE_SIZE);
        if (StringUtils.isBlank(indexName)) throw new IllegalArgumentException("indexName is required");

        CompletableFuture<Object[]> mat = new CompletableFuture<>();

        GraphStageLogic logic = new GraphStageLogicWithLogging(shape()) {

            // stateful stream properties... mutable state should only exist within the GraphStageLogic anonymous class
            private Object[] searchAfter = startingSearchAfter.orElse(null);
            private final String queryText = parseQuery(query);
            private Optional<SearchResponse> dataReady = Optional.empty();
            private boolean waitingForEsResponse = false;
            private boolean pullIsWaitingForData = false;
            private Long processedCt = 0L;

            private AsyncCallback<SearchResponse> searchCallback;
            private AsyncCallback<Exception> searchErrorCallback;
            private ActionListener<SearchResponse> responseListener = new ActionListener<>() {
                // !! This is an ES action listener, which is called by ES outside the context of the akka stream
                //      the only code in the ActionListener should delegate back to akka via an AsyncCallback which
                //      is guaranteed to run in a thread-safe manner and have proper context to the stream lifecycle
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    waitingForEsResponse = false;
                    searchCallback.invoke(searchResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    waitingForEsResponse = false;
                    searchErrorCallback.invoke(e);
                }
            };

            {
                setHandler(out, new AbstractOutHandler() {
                    @Override
                    public void onPull() throws Exception {
                        doPull();
                    }
                });
            }
            private void handleException(Exception e) {
                    //mat.complete(searchAfter);
                    failStage(e);
            }

            private void handleResponse(SearchResponse response) {
                log().info("Elastic response took {} for {} total hits", response.getTook(), response.getHits().getTotalHits().value);
                waitingForEsResponse = false;
                if (response.getHits() == null || response.getHits().getHits().length == 0) {
                    log.debug("completing stage");
                     // mat.complete(searchAfter);
                    completeStage();
                    return;
                }

                if (pullIsWaitingForData) {
                    log.debug("Received data from elastic. Downstream has already called pull and is waiting for data");
                    if (emitData(response)) {
                        // fetch the next page to have it ready by the time the downstream requests more data
                        requestNextPage();
                    }
                } else {
                  log.debug("Received data from elastic. Downstream have not yet asked for it");
                  // This is a prefetch of data which we received before downstream has asked for it
                    log().info("stashing response with {} items", response.getHits().getHits().length);
                  dataReady = Optional.of(response);
                }
            }

            private boolean emitData(SearchResponse response) {
                log().info("Completed {} of {}{} total hits", processedCt, response.getHits().getTotalHits().value,
                        response.getHits().getTotalHits().relation.equals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO) ?
                        "+":"");
                if (response.getHits() == null || response.getHits().getHits().length == 0) {

                    completeStage();
                    return false;
                }

                List<SearchHit> hits = Arrays.asList(response.getHits().getHits());
                log().info("emitting {}", hits.size());
                pullIsWaitingForData = false;
                push(out, hits);
                processedCt = processedCt + hits.size();

                searchAfter = hits.get(hits.size()-1).getSortValues();
                log.debug("last item pushed({}): {}", hits.size(), Arrays.asList(searchAfter));

                if (hits.size() < pageSize) {
                    return false;
                }
                return true;
            }

            @Override
            public void preStart() {
                searchCallback = createAsyncCallback(this::handleResponse);
                searchErrorCallback = createAsyncCallback(this::handleException);
            }

            @Override
            public void postStop() {
                mat.complete(searchAfter);
            }

            private void doPull() throws Exception {
                log.debug("Pull Requested from downstream");
                if (dataReady.isPresent()) {
                    log.debug("Downstream is pulling data and we already have data ready");
                    if (emitData(dataReady.get())) {
                        if (false == waitingForEsResponse) {
                            requestNextPage();
                        }
                    }
                    dataReady = Optional.empty();
                } else {
                    if (pullIsWaitingForData) throw new Exception("This should not happen: Downstream is pulling more than once");
                    pullIsWaitingForData = true;

                    if (!waitingForEsResponse) {
                      log.debug("Downstream is pulling data. We must go and get it");
                      requestNextPage();
                    } else {
                      log.debug("Downstream is pulling data. Already waiting for data");
                    }
                }
            }

            private void requestNextPage() {
                waitingForEsResponse = true;
                esClient.searchAsync(buildSearchRequest(), RequestOptions.DEFAULT, responseListener);
            }

            private SearchRequest buildSearchRequest() {
                SearchRequest searchRequest = new SearchRequest(indexName);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                        .fetchSource(false)
                        .size(pageSize)
                        .query(QueryBuilders.wrapperQuery(queryText));

                for (var sortPair : sort) {
                    String sortCol = sortPair.first();
                    Optional<SortOrder> sortDir = sortPair.second();

                    if (sortDir.isPresent()) {
                        searchSourceBuilder = searchSourceBuilder.sort(sortCol, sortDir.get());
                    } else {
                        searchSourceBuilder = searchSourceBuilder.sort(sortCol);
                    }
                }

                if (searchAfter != null) {
                    searchSourceBuilder = searchSourceBuilder.searchAfter(searchAfter);
                }

                searchRequest.source(searchSourceBuilder);
                log.debug("try call {}",  searchSourceBuilder);

                return searchRequest;
            }
        };

        return Tuple2.apply(logic, mat);
    }



    /**
     * if we've been passed a top-level request including a query and other search params (sorting, pagination, highlighting)
     * then just extract the root query and discard the rest
     * @param query
     * @return
     */
    private String parseQuery(ObjectNode query) {
        return Optional.ofNullable(query.get("query"))
                       .orElse(query)
                       .toString();
    }

    @Override
    public SourceShape<List<SearchHit>> shape() {
        return shape;
    }
}
