package it.zuperman.support_trainer.common.response;

/**
 * Public validation detail. The rejected value and internal binding metadata
 * are intentionally not part of the HTTP contract.
 */
public record FieldErrorResponse(
        String field,
        String code,
        String message
) {
}
