package net.spritzlive.spritzstore

import kotlinx.serialization.json.Json
import net.spritzlive.spritzstore.github.StoreManifest
import net.spritzlive.spritzstore.model.DEFAULT_TRACKED_REPOS
import net.spritzlive.spritzstore.model.STORE_APP_NAMES
import net.spritzlive.spritzstore.model.TrackedRepo
import net.spritzlive.spritzstore.model.migrateToStore
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreRepoTest {

    @Test
    fun migrationRepointsMovedAppsAndKeepsTheRest() {
        val saved = listOf(
            TrackedRepo("cook1991", "weatherapp", "com.spritzlive.weather", lastNotifiedVersionCode = 3),
            TrackedRepo("cook1991", "vmapp", "net.spritzlive.voicemail"),
        )
        val migrated = migrateToStore(saved)

        assertEquals("cook1991/cook1991.github.io#skycast", migrated[0].slug)
        assertEquals(3L, migrated[0].lastNotifiedVersionCode)
        assertEquals(saved[1], migrated[1])
        assertEquals(migrated, migrateToStore(migrated))
    }

    @Test
    fun migrationDoesNotAddAppsThePersonRemoved() {
        val saved = listOf(TrackedRepo("cook1991", "securemessenger", "net.spritzlive.securemessenger"))
        assertEquals(listOf("securemessenger"), migrateToStore(saved).map { it.storeApp })
    }

    @Test
    fun defaultListSlugsAreUnique() {
        assertEquals(DEFAULT_TRACKED_REPOS.size, DEFAULT_TRACKED_REPOS.map { it.slug }.toSet().size)
    }

    @Test
    fun everyDefaultAppHasAName() {
        assertEquals(emptyList<String?>(), DEFAULT_TRACKED_REPOS.map { it.storeApp }.filterNot { it in STORE_APP_NAMES })
        assertEquals("Identify Song", DEFAULT_TRACKED_REPOS.first { it.storeApp == "identifysong" }.displayName)
        assertEquals("vmapp", TrackedRepo("cook1991", "vmapp", "net.spritzlive.voicemail").displayName)
    }

    @Test
    fun manifestParsesAndToleratesNewFields() {
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<StoreManifest>(
            """{"apps":{"drb":{"versionCode":5,"versionName":"1.4","url":"https://x/a.apk",
            "size":10,"sha256":"ab","added_later":true}}}"""
        )
        assertEquals(5L, manifest.apps.getValue("drb").versionCode)
    }
}
