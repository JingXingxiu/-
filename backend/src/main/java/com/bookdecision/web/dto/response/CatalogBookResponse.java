package com.bookdecision.web.dto.response;

public record CatalogBookResponse(String isbn, String title, int acceptedPlatformCount) {
}
