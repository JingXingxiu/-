package com.bookdecision.application;

/**
 * Stable, transport-independent identifier for an application failure.
 */
public interface ErrorCode {

    String code();

    String defaultMessage();
}
