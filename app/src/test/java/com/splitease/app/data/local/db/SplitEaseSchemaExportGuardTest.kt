package com.splitease.app.data.local.db

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Fails CI when [SplitEaseDatabase] is bumped without committing the matching
 * Room schema JSON. Versions below the lowest file currently in `schemas/`
 * (historically 1–4) are out of scope — see `app/schemas/README.md`.
 */
class SplitEaseSchemaExportGuardTest {
    @Test
    fun schemaJsonExistsForEveryVersionFromLowestPresentThroughLive() {
        val schemaDir = resolveSchemaDir()
        assertTrue(schemaDir.isDirectory, "Missing schema directory: ${schemaDir.path}")
        val present =
            schemaDir
                .listFiles { _, name -> name.endsWith(".json") }
                .orEmpty()
                .mapNotNull { file -> file.name.removeSuffix(".json").toIntOrNull() }
                .sorted()
        assertTrue(present.isNotEmpty(), "No schema JSON files in ${schemaDir.path}")
        val lowest = present.first()
        val live = liveDatabaseVersion()
        for (version in lowest..live) {
            val file = File(schemaDir, "$version.json")
            assertTrue(
                file.isFile,
                "Missing Room schema JSON for version $version (${file.path}). " +
                    "Compile the app so Room exports it, then commit the file.",
            )
        }
    }

    private fun liveDatabaseVersion(): Int {
        val source = resolveDatabaseSource()
        assertTrue(source.isFile, "Could not read SplitEaseDatabase.kt at ${source.path}")
        val match = Regex("""version\s*=\s*(\d+)""").find(source.readText())
        check(match != null) { "Could not parse Room version from ${source.path}" }
        return match.groupValues[1].toInt()
    }

    private fun resolveSchemaDir(): File {
        val relative = "schemas/com.splitease.app.data.local.db.SplitEaseDatabase"
        return candidateRoots()
            .map { File(it, relative) }
            .firstOrNull { it.isDirectory }
            ?: File(relative)
    }

    private fun resolveDatabaseSource(): File {
        val relative =
            "src/main/java/com/splitease/app/data/local/db/SplitEaseDatabase.kt"
        return candidateRoots()
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?: File(relative)
    }

    private fun candidateRoots(): List<File> {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return listOf(
            userDir,
            File(userDir, "app"),
            userDir.parentFile,
            userDir.parentFile?.let { File(it, "app") },
        ).filterNotNull().distinct()
    }
}
