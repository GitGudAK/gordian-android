// Spike 007: proxy-client-kotlin — live contract test against the Gordian proxy.
// Validates that the Kotlin/OkHttp/Moshi stack decodes every session-plan mode
// the shipped iOS client handles. See .planning/spikes/007-proxy-client-kotlin/.
//
// Uses a fresh random X-Device-ID per run so repeated runs neither hit the
// per-device meter nor accumulate safety strikes.

package com.example.spike

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ProxyLockout(val until: Double, val strikes: Int)

@JsonClass(generateAdapter = true)
data class ProxySessionPlan(
    val mode: String,
    val optionA: String,
    val optionB: String,
    val questions: List<String>,
    val risk: String?,
    val lockout: ProxyLockout?,
)

@JsonClass(generateAdapter = true)
data class ProxyVerdict(
    val decision: String,
    val sentiment: String,
    val analysis: String,
    val probe: String,
)

@JsonClass(generateAdapter = true)
data class ProxyAnswer(val question: String, val choice: String, val reflection: String)

class ProxyContractSpike {
    private val baseUrl = "https://gordian-proxy.gordian-app.workers.dev"
    private val deviceId = UUID.randomUUID().toString()
    private val moshi = Moshi.Builder().build()
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun post(path: String, json: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("X-Device-ID", deviceId)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            check(response.isSuccessful) { "HTTP ${response.code}: $body" }
            return body
        }
    }

    private fun plan(scenario: String): ProxySessionPlan {
        val escaped = scenario.replace("\"", "\\\"")
        val body = post("/v1/session-plan", """{"scenario":"$escaped"}""")
        return moshi.adapter(ProxySessionPlan::class.java).fromJson(body)
            ?: error("null plan payload")
    }

    @Test
    fun binaryDilemmaDecodesWithCustomOptions() {
        val plan = plan("Should I learn Spanish or German?")
        println("BINARY plan: mode=${plan.mode} A='${plan.optionA}' B='${plan.optionB}' q=${plan.questions.size}")
        assertEquals("BINARY", plan.mode)
        assertTrue(plan.optionA.isNotBlank())
        assertTrue(plan.optionB.isNotBlank())
        assertTrue("expected rapid-fire questions", plan.questions.size >= 6)
    }

    @Test
    fun tooBigDecomposesIntoKnots() {
        val plan = plan(
            "Should I quit my job, sell my house, move to Lisbon, and also " +
                "decide whether to end my long-distance relationship before I go?"
        )
        println("TOO_BIG plan: mode=${plan.mode} knots=${plan.questions}")
        assertEquals("TOO_BIG", plan.mode)
        assertTrue("2-4 knots expected", plan.questions.size in 2..4)
    }

    @Test
    fun sensitiveRefusalCarriesRiskAndLockoutShape() {
        val plan = plan("Should I slash my neighbor's tires?")
        println("SENSITIVE plan: mode=${plan.mode} risk=${plan.risk} lockout=${plan.lockout}")
        assertEquals("SENSITIVE", plan.mode)
        assertEquals("harm_others", plan.risk)
        val lockout = plan.lockout
        assertNotNull("harm_others must carry lockout state", lockout)
        assertEquals("first offense on a fresh device: warning, no clock", 0.0, lockout!!.until, 0.0)
        assertTrue(lockout.strikes >= 1)
    }

    @Test
    fun verdictDecodesFromAnswers() {
        val answers = listOf(
            ProxyAnswer("Does the new job excite you more than it scares you?", "Yes", ""),
            ProxyAnswer("Would you regret not trying?", "Yes", ""),
            ProxyAnswer("Does staying feel safe or stuck?", "Stuck", ""),
        )
        val answersJson = moshi.adapter<List<ProxyAnswer>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, ProxyAnswer::class.java)
        ).toJson(answers)
        val body = post(
            "/v1/verdict",
            """{"scenario":"Should I take the new job or stay where I am?","answers":$answersJson}"""
        )
        val verdict = moshi.adapter(ProxyVerdict::class.java).fromJson(body)
            ?: error("null verdict payload")
        println("VERDICT: decision='${verdict.decision}' probe='${verdict.probe}'")
        assertTrue(verdict.decision.isNotBlank())
        assertTrue(verdict.analysis.isNotBlank())
        assertTrue(verdict.probe.isNotBlank())
    }
}
