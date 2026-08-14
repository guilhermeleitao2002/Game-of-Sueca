package pt.up.fe.asma.sueca.vision

import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.engine.Rank
import pt.up.fe.asma.sueca.engine.Suit
import java.time.Duration

/**
 * Reads a whole hand out of one photograph, using Claude's vision.
 *
 * This is the *optional* half of the scanner, and it is deliberately shaped differently from the
 * on-device pipeline next to it:
 *
 *  - **One photo, not a video stream.** A model call costs a fraction of a cent and takes a few
 *    seconds, so it is worth doing once over ten cards rather than thirty times a second over
 *    one.
 *  - **The user's own API key.** Nothing is shipped in the APK — see [pt.up.fe.asma.sueca.data.AppSettings].
 *  - **Never the default.** With no key, the app behaves exactly as before, offline.
 *
 * The on-device pipeline stays the fast, free, private path; this is the one to reach for when a
 * deck defeats it.
 */
class ClaudeCardReader(private val apiKey: String, private val io: CoroutineDispatcher = Dispatchers.IO) {

    sealed interface Outcome {
        /** Cards the model was confident about, plus whatever it wanted to say about the photo. */
        data class Read(val cards: List<Card>, val note: String?) : Outcome

        data class Failed(val message: String) : Outcome
    }

    private val client by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(90))
            .build()
    }

    suspend fun read(jpeg: ByteArray): Outcome = withContext(io) {
        runCatching { request(jpeg) }.getOrElse { error ->
            Outcome.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private fun request(jpeg: ByteArray): Outcome {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    // android.util.Base64, not java.util.Base64: the latter is API 26 and this
                    // app runs from 24.
                    .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    .build(),
            )
            .build()

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(2048)
            .outputConfig(
                // A schema rather than a "please answer in JSON" instruction: the response is
                // then guaranteed to parse, so a misread is a wrong card and never a crash.
                OutputConfig.builder().format(
                    JsonOutputFormat.builder()
                        .schema(
                            JsonOutputFormat.Schema.builder()
                                .putAdditionalProperty("type", JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(PROPERTIES))
                                .putAdditionalProperty("required", JsonValue.from(listOf("cards", "note")))
                                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                                .build(),
                        )
                        .build(),
                ).build(),
            )
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build()),
                ),
            )
            .build()

        val message = client.messages().create(params)

        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            return Outcome.Failed("Claude declined to answer for this image.")
        }

        val text = message.content().firstNotNullOfOrNull { block ->
            block.text().orElse(null)?.text()
        } ?: return Outcome.Failed("Claude returned no text to read.")

        return parse(text)
    }

    private fun parse(json: String): Outcome {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Outcome.Failed("Could not read Claude's answer.")
        }

        val array = root.optJSONArray("cards") ?: return Outcome.Failed("No cards in Claude's answer.")
        val cards = buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val rank = Rank.fromLabel(entry.optString("rank")) ?: continue
                val suit = Suit.fromId(entry.optString("suit")) ?: continue
                add(Card(suit, rank))
            }
        }.distinct()

        val note = root.optString("note").takeIf { it.isNotBlank() }
        return if (cards.isEmpty()) {
            Outcome.Failed(note ?: "Claude did not find any cards in that photo.")
        } else {
            Outcome.Read(cards, note)
        }
    }

    private companion object {
        const val MODEL = "claude-opus-5"

        val PROMPT = """
            This photograph shows playing cards from a game of Sueca, laid out or held in a hand.

            List every card you can identify. A Sueca deck is a 40 card French suited deck: the
            ranks are A, 2, 3, 4, 5, 6, 7, J, Q, K — there are no 8s, 9s or 10s, so if you think
            you see one you have misread the index. Some decks print R, D and V instead of K, Q
            and J.

            Only list a card when you can actually see enough of it to be sure of both its rank
            and its suit. Cards may be overlapping, angled, or partly hidden; leave out the ones
            you cannot read rather than guessing at them. Never list the same card twice.

            Use the note field to say anything the person should know — cards you could see but
            could not identify, glare, or a photo too blurry to read.
        """.trimIndent()

        /** The shape of the answer, as JSON Schema. */
        val PROPERTIES: Map<String, Any> = mapOf(
            "cards" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "rank" to mapOf(
                            "type" to "string",
                            "enum" to Rank.entries.map { it.label },
                        ),
                        "suit" to mapOf(
                            "type" to "string",
                            "enum" to Suit.entries.map { it.id },
                        ),
                    ),
                    "required" to listOf("rank", "suit"),
                    "additionalProperties" to false,
                ),
            ),
            "note" to mapOf(
                "type" to "string",
                "description" to "Anything worth telling the user; empty when there is nothing.",
            ),
        )
    }
}
