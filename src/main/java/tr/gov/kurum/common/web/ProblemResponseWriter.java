package tr.gov.kurum.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tr.gov.kurum.common.domain.ErrorKey;

/**
 * Writes a problem response from a servlet filter or Spring Security handler, where Spring MVC's message
 * converters are not available. Produces the same JSON shape as {@link GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final ProblemFactory problemFactory;
    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorKey errorKey) throws IOException {
        ProblemDetail problem = problemFactory.create(errorKey, NO_ARGUMENTS, request.getRequestURI());
        Map<String, Object> body = new LinkedHashMap<>();
        if (problem.getType() != null) {   // Spring 7 leaves it unset (= "about:blank"); omit it like MVC does
            body.put("type", problem.getType().toString());
        }
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", request.getRequestURI());
        body.put("code", errorKey.code());

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
