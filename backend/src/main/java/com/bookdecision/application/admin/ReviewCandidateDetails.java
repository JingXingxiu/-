package com.bookdecision.application.admin;

import com.bookdecision.application.userdataset.UserDatasetBook;
import com.bookdecision.domain.PlatformOffer;

import java.util.List;

/** Normalized data presented to an administrator before publication. */
public record ReviewCandidateDetails(
        ReviewCandidate candidate,
        List<UserDatasetBook> books,
        List<PlatformOffer> offers
) {

    public ReviewCandidateDetails {
        books = List.copyOf(books);
        offers = List.copyOf(offers);
    }
}
