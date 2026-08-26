package com.bookdecision.application.dataset;

import java.util.Optional;

/** Port implemented by file-backed data today and replaceable by a database later. */
public interface DatasetProvider {

    Optional<DatasetSnapshot> findByVersion(String datasetVersion);
}
