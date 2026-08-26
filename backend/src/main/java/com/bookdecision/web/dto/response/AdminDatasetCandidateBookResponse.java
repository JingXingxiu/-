package com.bookdecision.web.dto.response;

import com.bookdecision.application.userdataset.UserDatasetBook;
import com.bookdecision.domain.PlatformOffer;

import java.util.List;

public record AdminDatasetCandidateBookResponse(
        String isbn,
        String title,
        int quantity,
        List<AdminDatasetCandidateOfferResponse> offers
) {

    public static AdminDatasetCandidateBookResponse from(
            UserDatasetBook book,
            List<PlatformOffer> offers
    ) {
        return new AdminDatasetCandidateBookResponse(
                book.isbn(),
                book.title(),
                book.quantity(),
                offers.stream().map(AdminDatasetCandidateOfferResponse::from).toList()
        );
    }
}
