package com.bookdecision.application.userdataset;

import com.bookdecision.domain.PlatformOffer;

import java.util.List;

public record StoredUserDataset(
        UserDatasetUpload upload,
        List<UserDatasetBook> books,
        List<PlatformOffer> offers
) {

    public StoredUserDataset {
        books = List.copyOf(books);
        offers = List.copyOf(offers);
    }
}
