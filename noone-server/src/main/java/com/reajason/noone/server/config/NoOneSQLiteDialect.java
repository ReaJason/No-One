package com.reajason.noone.server.config;

import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.community.dialect.identity.SQLiteIdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;

/**
 * Keeps the INTEGER type on SQLite identity columns when earlier columns also use INTEGER.
 * Hibernate 7.2 checks the identity token case-sensitively against a lower-cased partial
 * CREATE TABLE statement and can otherwise emit an untyped primary key.
 */
public final class NoOneSQLiteDialect extends SQLiteDialect {
    private static final IdentityColumnSupport IDENTITY_COLUMN_SUPPORT = new SQLiteIdentityColumnSupport() {
        @Override
        public String getIdentityColumnString(int type) {
            return "INTEGER";
        }
    };

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return IDENTITY_COLUMN_SUPPORT;
    }
}
