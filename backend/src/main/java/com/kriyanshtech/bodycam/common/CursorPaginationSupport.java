package com.kriyanshtech.bodycam.common;

import java.util.List;
import java.util.function.Function;

public final class CursorPaginationSupport {
    private CursorPaginationSupport() {
    }

    public static <T, R> CursorPageResponse<R> buildPage(
            List<T> fetchedItems,
            int size,
            Function<T, R> itemMapper,
            Function<T, String> nextCursorMapper) {
        boolean hasNext = fetchedItems.size() > size;
        List<T> pageItems = hasNext ? fetchedItems.subList(0, size) : fetchedItems;
        List<R> responseItems = pageItems.stream().map(itemMapper).toList();

        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            nextCursor = nextCursorMapper.apply(pageItems.get(pageItems.size() - 1));
        }

        return new CursorPageResponse<>(responseItems, nextCursor, hasNext);
    }
}
