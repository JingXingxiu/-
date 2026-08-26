package com.bookdecision.web.dto.response;

import com.bookdecision.application.userdataset.UserDatasetBook;

public record UserDatasetBookResponse(String isbn, String title, int quantity) {

    public static UserDatasetBookResponse from(UserDatasetBook book) {
        return new UserDatasetBookResponse(book.isbn(), book.title(), book.quantity());
    }
}
