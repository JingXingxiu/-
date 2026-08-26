package com.bookdecision.web.dto.response;

import com.bookdecision.application.OfferLookupResult;

import java.util.List;

public record OfferLookupBookResponse(
        String isbn,
        String title,
        OfferLookupResult.CatalogStatus catalogStatus,
        List<PlatformOfferResponse> offers
) {

    public static OfferLookupBookResponse from(OfferLookupResult.Book book) {
        return new OfferLookupBookResponse(
                book.isbn(),
                book.title(),
                book.catalogStatus(),
                book.offers().stream().map(PlatformOfferResponse::from).toList()
        );
    }
}
