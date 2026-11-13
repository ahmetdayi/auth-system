package tr.gov.kurum.common.web;

import java.util.List;
import tr.gov.kurum.common.query.PageResult;

/** JSON shape of a page, the same for every list endpoint. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(PageResult<T> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(), result.totalElements(),
            result.totalPages());
    }
}
