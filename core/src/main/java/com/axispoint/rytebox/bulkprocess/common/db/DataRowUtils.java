package com.axispoint.rytebox.bulkprocess.common.db;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.vavr.Function2;
import io.vavr.Function3;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

//import com.axispoint.rytebox.bulkprocess.dqi.models.Asset;

@Slf4j
public class DataRowUtils {
    private static Map<Class, Map<String, Integer>> positionMaps = new HashMap<>();

    private final Class clazz;
    private final Function2<Function<String, Integer>, String, Integer> cachedColumnIndex;

    public static DataRowUtils of(Class clazz) {
        return new DataRowUtils(clazz);
    }

    private DataRowUtils(Class clazz) {
        this.clazz = clazz;
        cachedColumnIndex = Function3.of(this::getColumnIndex).apply(this.clazz);
    }

    public Integer indexOf(Row row, String colName) {
        return cachedColumnIndex.apply(row::getColumnIndex, colName);
    }

    @Synchronized
    public Integer getColumnIndex(Class clazz, Function<String, Integer> sourceColIndex, String name) {
        return
                positionMaps.computeIfAbsent(clazz, k -> new HashMap<>())
                            .computeIfAbsent(name, n -> sourceColIndex.apply(n));
    }

    public  <T> List<T> extractNestedJson(Row row, String colName, Function<JsonObject, T> constructor) {
        String jsonFragment = row.getString(indexOf(row, colName));

        Optional<List<T>> children = extractNestedJson(jsonFragment, constructor);

        return children.orElseGet(() -> List.of());
    }

    public <T> Optional<List<T>> extractNestedJson(String jsonFragment, Function<JsonObject, T> constructor) {
        return
                Optional.ofNullable(jsonFragment)
                .map(s -> "["+s+"]")    // unfortunately maria 10.3 only can concat json objects, but can't create a json array
                .map(JsonArray::new)
                .map(a -> a.stream()
                           .map(o -> (JsonObject) o)
                           .map(constructor)
                           .collect(toList())
                );
    }

    public static <T> Function<JsonObject, T> compose(Function<JsonObject, T> constructor, Consumer<T> setter) {
        return constructor.andThen(t -> {
            setter.accept(t);
            return t;
        });
    }
}
