package com.axispoint.rytebox.bulkprocess.common.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

@Data
public class DqiInput implements Reentrant<List<Object>> {
    public static final String DB_CONFIG = "db";
    // TODO: probably need to add ES config as well, but could possibly just be picked up via AWS param store

    private String outputBucket = "rbx-log-export";
    private String outputPath = "dqi";
    private String indexName;
    private ObjectNode esQuery;

    private int iteration = 0;
    private boolean isDone = false;
    private List<Object> continuation;
    private String exceptionMessage;
    private Map<String, JsonNode> config;

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public Reentrant<List<Object>> completeIteration(boolean isDone, List<Object> continuation, String exceptionMessage) {
        this.isDone = isDone;
        this.iteration = getIteration() + 1;
        this.continuation = continuation;
        this.exceptionMessage = exceptionMessage;
        return this;
    }

    @JsonIgnore
    public JsonNode getDbConfig() {
        return getConfig().get(DB_CONFIG);
    }
}
