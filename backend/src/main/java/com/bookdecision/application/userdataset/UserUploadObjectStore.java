package com.bookdecision.application.userdataset;

public interface UserUploadObjectStore {

    void put(String objectKey, byte[] content);

    byte[] get(String objectKey);

    void delete(String objectKey);
}
