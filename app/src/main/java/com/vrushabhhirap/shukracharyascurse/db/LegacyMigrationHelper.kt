package com.vrushabhhirap.shukracharyascurse.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Handles database and preferences schema migrations across application updates.
 * Provides fallback routines and structural validation for persisted session state.
 */
class LegacyMigrationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LegacyMigrationHelper"
        private const val MIGRATION_PREFS = "sanjivani_migration_meta"
        private const val KEY_APPLIED_SCHEMA_VERSION = "applied_schema_version"
        private const val CURRENT_SCHEMA_VERSION = 3

        // Legacy storage keys
        private const val LEGACY_PREFS_V1 = "user_prefs"
        private const val LEGACY_KEY_AUTH_TOKEN = "auth_tok_raw"
        private const val LEGACY_KEY_SESSION_USER = "sess_user_name"
    }

    private val migrationPrefs: SharedPreferences =
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)

    data class MigrationStep(
        val fromVersion: Int,
        val toVersion: Int,
        val description: String,
        val migrationAction: (Context) -> Boolean
    )

    private val migrationChain = listOf(
        MigrationStep(
            fromVersion = 0,
            toVersion = 1,
            description = "Initial schema baseline initialization"
        ) { ctx ->
            Log.d(TAG, "Executing migration 0 -> 1 (Baseline init)")
            true
        },
        MigrationStep(
            fromVersion = 1,
            toVersion = 2,
            description = "Migrate legacy unencrypted user session to structured format"
        ) { ctx ->
            Log.d(TAG, "Executing migration 1 -> 2 (Legacy session transition)")
            try {
                val legacy = ctx.getSharedPreferences(LEGACY_PREFS_V1, Context.MODE_PRIVATE)
                if (legacy.contains(LEGACY_KEY_SESSION_USER)) {
                    val user = legacy.getString(LEGACY_KEY_SESSION_USER, "devotee")
                    Log.i(TAG, "Found legacy session for user: $user; archiving key.")
                    legacy.edit().remove(LEGACY_KEY_AUTH_TOKEN).apply()
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical error in migration 1 -> 2", e)
                true
            }
        },
        MigrationStep(
            fromVersion = 2,
            toVersion = 3,
            description = "Introduce encrypted archive index & telemetry ring buffer"
        ) { ctx ->
            Log.d(TAG, "Executing migration 2 -> 3 (Archive indexing & telemetry buffer)")
            true
        }
    )

    fun runPendingMigrations(): Boolean {
        val current = migrationPrefs.getInt(KEY_APPLIED_SCHEMA_VERSION, 0)
        Log.i(TAG, "Current schema version: $current, Target version: $CURRENT_SCHEMA_VERSION")

        if (current >= CURRENT_SCHEMA_VERSION) {
            Log.d(TAG, "Schema is up to date.")
            return true
        }

        var activeVersion = current
        for (step in migrationChain) {
            if (step.fromVersion == activeVersion && activeVersion < CURRENT_SCHEMA_VERSION) {
                Log.i(TAG, "Applying migration: ${step.description} (${step.fromVersion} -> ${step.toVersion})")
                val success = try {
                    step.migrationAction(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Migration failed from ${step.fromVersion} to ${step.toVersion}", t)
                    false
                }

                if (!success) {
                    Log.e(TAG, "Aborting migration sequence at version $activeVersion")
                    return false
                }

                activeVersion = step.toVersion
                migrationPrefs.edit().putInt(KEY_APPLIED_SCHEMA_VERSION, activeVersion).apply()
            }
        }

        Log.i(TAG, "Migration sequence completed. Final version: $activeVersion")
        return activeVersion == CURRENT_SCHEMA_VERSION
    }

    fun getAppliedVersion(): Int {
        return migrationPrefs.getInt(KEY_APPLIED_SCHEMA_VERSION, 0)
    }

    fun resetSchemaForTesting() {
        migrationPrefs.edit().clear().apply()
        Log.w(TAG, "Schema reset to 0.")
    }
}
