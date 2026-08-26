package com.bookdecision.application.userdataset;

import com.bookdecision.domain.PlatformOffer;

import java.util.List;

public record ParsedUserDataset(
        String schemaVersion,
        List<UserDatasetBook> books,
        List<PlatformOffer> offers,
        int rowCount
) {

    public ParsedUserDataset {
        books = List.copyOf(books);
        offers = List.copyOf(offers);
    }
}
