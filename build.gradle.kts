import java.time.LocalDate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.legacy.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
}

val versionPropertiesFile = file("version.properties")
val releasesHistoryFile = file("RELEASES.md")
val changelogFile = file("CHANGELOG.md")

fun loadAppVersion(): Pair<Int, String> {
    val props =
        Properties().apply {
            require(versionPropertiesFile.exists()) { "Missing ${versionPropertiesFile.path}" }
            versionPropertiesFile.inputStream().use { load(it) }
        }
    val code =
        props.getProperty("versionCode")?.toIntOrNull()
            ?: error("version.properties is missing integer versionCode")
    val name =
        props.getProperty("versionName")?.trim().orEmpty().ifBlank {
            error("version.properties is missing versionName")
        }
    return code to name
}

fun bumpSemver(
    version: String,
    bump: String,
): String {
    val parts = version.split('.')
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
        "versionName must be MAJOR.MINOR.PATCH, was '$version'"
    }
    var major = parts[0].toInt()
    var minor = parts[1].toInt()
    var patch = parts[2].toInt()
    when (bump.lowercase()) {
        "major" -> {
            major += 1
            minor = 0
            patch = 0
        }
        "minor" -> {
            minor += 1
            patch = 0
        }
        "patch" -> patch += 1
        else -> error("Unknown bump '$bump'. Use patch, minor, or major.")
    }
    return "$major.$minor.$patch"
}

fun writeVersionProperties(
    versionCode: Int,
    versionName: String,
) {
    versionPropertiesFile.writeText(
        """
        |# SplitEase store version — single source of truth.
        |# Do not edit versionCode by hand. Create a release with:
        |#   ./gradlew newRelease
        |#   ./gradlew newRelease -Pbump=minor -Pnotes=Short summary
        |#   .\\scripts\\new-release.ps1 -Bump patch -Notes Short summary
        |versionCode=$versionCode
        |versionName=$versionName
        |
        """.trimMargin(),
    )
}

fun prependReleaseHistoryRow(
    versionCode: Int,
    versionName: String,
    date: String,
    notes: String,
) {
    require(releasesHistoryFile.exists()) { "Missing ${releasesHistoryFile.path}" }
    val text = releasesHistoryFile.readText()
    val marker = "| ---: | --- | --- | --- |"
    val idx = text.indexOf(marker)
    require(idx >= 0) { "RELEASES.md is missing the builds table header" }
    val insertAt = idx + marker.length
    val safeNotes = notes.replace("|", "/").ifBlank { "—" }
    val row = "\n| $versionCode | $versionName | $date | $safeNotes |"
    releasesHistoryFile.writeText(text.substring(0, insertAt) + row + text.substring(insertAt))
}

fun cutChangelogUnreleased(
    versionName: String,
    versionCode: Int,
    date: String,
) {
    require(changelogFile.exists()) { "Missing ${changelogFile.path}" }
    val text = changelogFile.readText()
    val marker = "## [Unreleased]"
    val start = text.indexOf(marker)
    require(start >= 0) { "CHANGELOG.md is missing '## [Unreleased]'" }
    val bodyStart = start + marker.length
    val nextHeading = Regex("\\n## \\[").find(text, bodyStart)
    val unreleasedBody =
        if (nextHeading != null) {
            text.substring(bodyStart, nextHeading.range.first).trim('\n')
        } else {
            text.substring(bodyStart).trim('\n')
        }
    val rest =
        if (nextHeading != null) {
            text.substring(nextHeading.range.first + 1)
        } else {
            ""
        }
    val header = text.substring(0, start)
    val freshUnreleased =
        """
        |## [Unreleased]
        |
        |### Added
        |
        |### Changed
        |
        |### Fixed
        |
        |## [${versionName}] - $date — build $versionCode
        |
        |${unreleasedBody}
        |
        """.trimMargin()
    changelogFile.writeText(header + freshUnreleased + rest)
}

tasks.register("printVersion") {
    group = "release"
    description = "Print the current versionName and versionCode from version.properties."
    doLast {
        val (code, name) = loadAppVersion()
        println("versionName=$name versionCode=$code")
    }
}

tasks.register("newRelease") {
    group = "release"
    description =
        "Increment versionCode, bump versionName (patch/minor/major), record RELEASES.md, cut CHANGELOG."
    doLast {
        val bump = (findProperty("bump") as? String)?.trim().orEmpty().ifBlank { "patch" }
        val notes = (findProperty("notes") as? String)?.trim().orEmpty()
        val (currentCode, currentName) = loadAppVersion()
        val nextCode = currentCode + 1
        val nextName = bumpSemver(currentName, bump)
        val date = LocalDate.now().toString()
        writeVersionProperties(nextCode, nextName)
        prependReleaseHistoryRow(nextCode, nextName, date, notes)
        cutChangelogUnreleased(nextName, nextCode, date)
        println("Release $nextName (build $nextCode) recorded. Previous was $currentName (build $currentCode).")
        println("Next: assemble/bundle the release, then commit version.properties, RELEASES.md, and CHANGELOG.md.")
    }
}
