package it.zuperman.support_trainer.common.response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class ErrorResponseWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorResponseWriter.class);

    private final ObjectMapper objectMapper;
    private final ErrorResponseFactory errorResponseFactory;

    public ErrorResponseWriter(ObjectMapper objectMapper, ErrorResponseFactory errorResponseFactory) {
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatusCode status,
            String code,
            String message
    ) {
        write(request, response, status.value(), code, message);
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try {
            objectMapper.writeValue(
                    response.getOutputStream(),
                    errorResponseFactory.create(request, status, code, message)
            );
            response.getOutputStream().flush();
        } catch (IOException ex) {
            LOGGER.error(
                    "Unable to serialize HTTP error response for path={}",
                    errorResponseFactory.requestPath(request),
                    ex
            );
        }
    }
}
