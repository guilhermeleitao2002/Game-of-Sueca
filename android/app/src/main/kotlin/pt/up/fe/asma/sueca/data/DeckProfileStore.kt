package pt.up.fe.asma.sueca.data

import android.content.Context
import pt.up.fe.asma.sueca.vision.DeckProfile
import java.io.File

/** Enough of a profile to list it without loading every exemplar. */
data class DeckProfileSummary(
    val id: String,
    val name: String,
    val pips: Int,
    val usable: Boolean,
)

/**
 * Deck profiles on disk, one small text file each.
 *
 * No database and no serialisation library: a profile is a few kilobytes of hex, the app will
 * only ever have a handful, and keeping the format readable means a broken one can be deleted
 * or inspected without tooling.
 */
class DeckProfileStore(context: Context) {

    private val directory = File(context.filesDir, "deck-profiles").apply { mkdirs() }

    fun list(): List<DeckProfileSummary> =
        directory.listFiles { file -> file.extension == EXTENSION }
            ?.mapNotNull { file -> load(file.nameWithoutExtension) }
            ?.map { DeckProfileSummary(it.id, it.name, it.totalPips, it.isUsable) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun load(id: String): DeckProfile? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return runCatching { DeckProfile.decode(file.readText()) }.getOrNull()
    }

    fun save(profile: DeckProfile) {
        runCatching { fileFor(profile.id).writeText(profile.encode()) }
    }

    fun delete(id: String) {
        runCatching { fileFor(id).delete() }
    }

    fun create(name: String): DeckProfile {
        val profile = DeckProfile(id = "deck-${System.currentTimeMillis()}", name = name)
        save(profile)
        return profile
    }

    private fun fileFor(id: String) = File(directory, "${id.filter { it.isLetterOrDigit() || it == '-' }}.$EXTENSION")

    private companion object {
        const val EXTENSION = "deck"
    }
}
