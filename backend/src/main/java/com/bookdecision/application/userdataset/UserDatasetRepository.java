package com.bookdecision.application.userdataset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDatasetRepository {

    void save(UserDatasetUpload upload, ParsedUserDataset dataset);

    Optional<StoredUserDataset> findById(UUID uploadId);

    List<UserDatasetUpload> findExpired(Instant now, int limit);

    boolean deleteById(UUID uploadId);
}
