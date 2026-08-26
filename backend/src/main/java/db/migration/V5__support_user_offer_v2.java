package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** Allows both the current Chinese upload format and the legacy English format in persisted metadata. */
public final class V5__support_user_offer_v2 extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    alter table user_dataset_upload
                    drop constraint user_dataset_upload_schema_version_check
                    """);
            statement.execute("""
                    alter table user_dataset_upload
                    add constraint user_dataset_upload_schema_version_check
                    check (schema_version in ('user-offer-v1', '用户报价-v2'))
                    """);
        }
    }
}
