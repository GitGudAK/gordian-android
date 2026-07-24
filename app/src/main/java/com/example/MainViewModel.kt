package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.RetrofitClient
import com.example.api.Content as ApiContent
import com.example.api.Part as ApiPart
import com.example.data.DecisionDatabase
import com.example.data.DecisionLog
import com.example.data.DecisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ActiveTab {
    FOCUS, INSIGHTS, CALIBRATE
}

enum class FocusScreenState {
    HOME, CLARIFYING, ACTIVE_SESSION, VERDICT
}

data class SimulationTopic(
    val title: String,
    val description: String,
    val defaultQuestions: List<String>
)

data class RapidFireAnswer(
    val question: String,
    val choice: String,
    val reflectionText: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DecisionRepository

    init {
        val database = DecisionDatabase.getDatabase(application)
        repository = DecisionRepository(database.decisionDao())
    }

    val allDecisions: StateFlow<List<DecisionLog>> = repository.allDecisions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI Tab Navigation
    private val _activeTab = MutableStateFlow(ActiveTab.FOCUS)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _focusScreenState = MutableStateFlow(FocusScreenState.HOME)
    val focusScreenState: StateFlow<FocusScreenState> = _focusScreenState.asStateFlow()

    fun selectTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun setFocusScreenState(state: FocusScreenState) {
        _focusScreenState.value = state
    }

    // Default Simulation Scenarios
    val availableTopics = listOf(
        SimulationTopic(
            title = "Career Shift",
            description = "Job offer in hand, mounting anxiety about leaving your stable routine.",
            defaultQuestions = listOf(
                "Are you choosing this out of ambition or fear?",
                "If you were forbidden from explaining this choice to anyone, would you still do it?",
                "If you flip a coin on this choice, which side do you secretly hope it lands on?",
                "Is your current stability actually growth, or is it just comfortable stagnation?",
                "Would the 80-year-old version of you regret staying or regret leaving?"
            )
        ),
        SimulationTopic(
            title = "Meeting Loop",
            description = "Teams stuck in a circular alignment debate, bleeding hours to consensus paralysis.",
            defaultQuestions = listOf(
                "Is this meeting meant to build a perfect plan, or to avoid individual responsibility?",
                "If we wait for 100% consensus, will our competitors ship before we agree?",
                "Is the risk of being wrong worse than the certainty of being slow?",
                "If this project fails, is it because of the decision itself, or because we took too long?",
                "Would you be willing to take full personal ownership of shipping this today?"
            )
        ),
        SimulationTopic(
            title = "Mounting Anxiety",
            description = "Multiple browser tabs open, brain doing daily simulations, overload mounting.",
            defaultQuestions = listOf(
                "Are these open tabs representing active progress, or visual monuments to avoidance?",
                "If you closed all tabs right now, what is the single most critical task you'd miss?",
                "Is your anxiety telling you to work harder, or is it telling you to simplify?",
                "Would 1 hour of fully focused effort do more than 5 hours of multi-tasking?",
                "Are you seeking consensus to dilute the risk of your own intuition?"
            )
        ),
        SimulationTopic(
            title = "Micro-Decisions",
            description = "A quick daily dilemma: Buy the expensive subscription or build it yourself?",
            defaultQuestions = listOf(
                "Are you buying this to solve a real bottleneck, or just to buy the feeling of progress?",
                "If this cost three times as much, would you still buy it?",
                "Is your time saved worth more than the dollars spent?",
                "Is this a screaming 'hell yes' or is it actually a distraction?",
                "Are you overthinking this to delay doing the real work?"
            )
        )
    )

    private val _selectedTopic = MutableStateFlow(availableTopics[0])
    val selectedTopic: StateFlow<SimulationTopic> = _selectedTopic.asStateFlow()

    // Active Simulation State
    private val _countdownSeconds = MutableStateFlow(60)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _isTimerActive = MutableStateFlow(false)
    val isTimerActive: StateFlow<Boolean> = _isTimerActive.asStateFlow()

    private val _activeQuestions = MutableStateFlow(availableTopics[0].defaultQuestions)
    val activeQuestions: StateFlow<List<String>> = _activeQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    // Voice & Sentiment Input
    private val _sentimentWaveRms = MutableStateFlow(0f)
    val sentimentWaveRms: StateFlow<Float> = _sentimentWaveRms.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Dilemma Setup Flow State
    private val _dilemmaScenario = MutableStateFlow("")
    val dilemmaScenario: StateFlow<String> = _dilemmaScenario.asStateFlow()

    private val _clarifyingQuestions = MutableStateFlow<List<String>>(emptyList())
    val clarifyingQuestions: StateFlow<List<String>> = _clarifyingQuestions.asStateFlow()

    private val _currentClarifyingQuestionIndex = MutableStateFlow(0)
    val currentClarifyingQuestionIndex: StateFlow<Int> = _currentClarifyingQuestionIndex.asStateFlow()

    private val _clarifyingAnswers = MutableStateFlow<List<String>>(emptyList())
    val clarifyingAnswers: StateFlow<List<String>> = _clarifyingAnswers.asStateFlow()

    private val _isGeneratingQuestions = MutableStateFlow(false)
    val isGeneratingQuestions: StateFlow<Boolean> = _isGeneratingQuestions.asStateFlow()

    private val _rapidFireAnswers = MutableStateFlow<List<RapidFireAnswer>>(emptyList())
    val rapidFireAnswers: StateFlow<List<RapidFireAnswer>> = _rapidFireAnswers.asStateFlow()

    private val _confrontedProbe = MutableStateFlow("")
    val confrontedProbe: StateFlow<String> = _confrontedProbe.asStateFlow()

    fun startDilemmaSetup(scenario: String) {
        _dilemmaScenario.value = scenario
        _clarifyingQuestions.value = emptyList()
        _currentClarifyingQuestionIndex.value = 0
        _clarifyingAnswers.value = emptyList()
        _rapidFireAnswers.value = emptyList()
        _confrontedProbe.value = ""
        _focusScreenState.value = FocusScreenState.CLARIFYING
        generateClarifyingQuestions()
    }

    private fun generateClarifyingQuestions() {
        val scenario = _dilemmaScenario.value
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback clarifying questions
            val fallback = listOf(
                "What is the single greatest risk holding you back from making this choice?",
                "If both options cost the same and had the same social status, which would you pick?"
            )
            _clarifyingQuestions.value = fallback
            return
        }

        viewModelScope.launch {
            _isGeneratingQuestions.value = true
            val systemInstruction = "You are an expert cognitive psychologist specializing in decision-making under intense pressure. " +
                    "The user has described their dilemma: '$scenario'. " +
                    "Assess whether you need clarifying questions to understand their scenario, emotional state, or core trade-offs better before formulating deep psychological bypass questions. " +
                    "If clarifying questions are indeed needed, generate exactly 1 or 2 short, direct, psychologically targeted clarifying questions (maximum 12 words each). " +
                    "If the scenario is already extremely clear, detailed, and specific, and no clarifying questions are needed to get to the core, return an empty JSON array: []. " +
                    "Format your response as a JSON array of strings: [\"Question 1?\", \"Question 2?\"] or []. " +
                    "Output ONLY the JSON array. No markdown, no formatting, no code blocks."

            val request = GenerateContentRequest(
                contents = listOf(
                    ApiContent(
                        parts = listOf(
                            ApiPart(text = "Generate clarifying questions only if needed, otherwise empty array.")
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    responseMimeType = "application/json"
                ),
                systemInstruction = ApiContent(
                    parts = listOf(ApiPart(text = systemInstruction))
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonArray = JSONArray(cleanJson)
                val generated = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    generated.add(jsonArray.getString(i))
                }
                if (generated.isNotEmpty()) {
                    _clarifyingQuestions.value = generated
                    _currentClarifyingQuestionIndex.value = 0
                } else {
                    // No clarifying questions needed! Proceed directly to bypass questions.
                    _clarifyingQuestions.value = emptyList()
                    generateBypassQuestionsAndStart()
                }
            } catch (e: Exception) {
                _clarifyingQuestions.value = listOf(
                    "What is the single greatest risk holding you back from making this choice?",
                    "If both options cost the same and had the same social status, which would you pick?"
                )
            } finally {
                _isGeneratingQuestions.value = false
            }
        }
    }

    fun submitClarifyingAnswer(answer: String) {
        val currentAnswers = _clarifyingAnswers.value.toMutableList()
        currentAnswers.add(answer.ifEmpty { "Skipped" })
        _clarifyingAnswers.value = currentAnswers

        val nextIndex = _currentClarifyingQuestionIndex.value + 1
        if (nextIndex < _clarifyingQuestions.value.size) {
            _currentClarifyingQuestionIndex.value = nextIndex
        } else {
            generateBypassQuestionsAndStart()
        }
    }

    fun skipClarifications() {
        generateBypassQuestionsAndStart()
    }

    private fun generateBypassQuestionsAndStart() {
        val scenario = _dilemmaScenario.value
        val questions = _clarifyingQuestions.value
        val answers = _clarifyingAnswers.value

        val qaPairs = StringBuilder()
        for (i in 0 until questions.size) {
            val q = questions.getOrNull(i) ?: ""
            val a = answers.getOrNull(i) ?: "Skipped"
            qaPairs.append("Q: $q -> A: $a\n")
        }

        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback bypass questions
            val smartQuestions = listOf(
                "Is your hesitation actually saving you, or stalling you?",
                "If no one was looking, what would your answer be?",
                "Are you choosing out of ambition or fear?",
                "Would your 80-year-old self regret choosing stagnation?",
                "Is this a screaming 'hell yes', or is it actually a distraction?",
                "Are you overthinking this to delay doing the real work?",
                "If both options cost the same, which would you pick?",
                "What is the single greatest risk holding you back?",
                "Are you seeking consensus to dilute your own risk?",
                "Is comfort more important to you than growth?",
                "What is the choice you are most afraid of making?",
                "Will you be thinking about this same problem next year?"
            )
            val topicTitle = if (scenario.length > 25) scenario.take(25) + "..." else scenario
            _selectedTopic.value = SimulationTopic(
                title = topicTitle,
                description = scenario,
                defaultQuestions = smartQuestions
            )
            _activeQuestions.value = smartQuestions
            _currentQuestionIndex.value = 0
            _focusScreenState.value = FocusScreenState.ACTIVE_SESSION
            _countdownSeconds.value = 60
            startTimer()
            return
        }

        viewModelScope.launch {
            _isGeneratingQuestions.value = true
            val systemInstruction = "You are an expert cognitive psychologist specializing in rapid gut-instinct bypass. " +
                    "The user has a dilemma: '$scenario'.\n" +
                    "Insights gathered from clarification dialogue:\n$qaPairs\n" +
                    "Generate exactly 12 rapid-fire, high-intensity bypass questions (maximum 10 words each, answers should be Yes or No) " +
                    "designed to bypass the analytical brain, force an immediate gut response, and highlight subconscious desires or core fears.\n" +
                    "Format your response as a JSON array of strings: [\"Question 1?\", \"Question 2?\", ..., \"Question 12?\"] " +
                    "Output ONLY the JSON array. No markdown, no formatting, no code blocks."

            val request = GenerateContentRequest(
                contents = listOf(
                    ApiContent(
                        parts = listOf(
                            ApiPart(text = "Generate exactly 12 psychological bypass questions based on the dilemma and dialogue.")
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.8f,
                    responseMimeType = "application/json"
                ),
                systemInstruction = ApiContent(
                    parts = listOf(ApiPart(text = systemInstruction))
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonArray = JSONArray(cleanJson)
                val generated = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    generated.add(jsonArray.getString(i))
                }

                if (generated.isNotEmpty()) {
                    val topicTitle = if (scenario.length > 25) scenario.take(25) + "..." else scenario
                    _selectedTopic.value = SimulationTopic(
                        title = topicTitle,
                        description = scenario,
                        defaultQuestions = generated
                    )
                    _activeQuestions.value = generated
                    _currentQuestionIndex.value = 0
                    _focusScreenState.value = FocusScreenState.ACTIVE_SESSION
                    _countdownSeconds.value = 60
                    startTimer()
                } else {
                    throw Exception("Empty list")
                }
            } catch (e: Exception) {
                // Fallback
                val smartQuestions = listOf(
                    "Is your hesitation actually saving you, or stalling you?",
                    "If no one was looking, what would your answer be?",
                    "Are you choosing out of ambition or fear?",
                    "Would your 80-year-old self regret choosing stagnation?",
                    "Is this a screaming 'hell yes', or is it actually a distraction?",
                    "Are you overthinking this to delay doing the real work?",
                    "If both options cost the same, which would you pick?",
                    "What is the single greatest risk holding you back?",
                    "Are you seeking consensus to dilute your own risk?",
                    "Is comfort more important to you than growth?",
                    "What is the choice you are most afraid of making?",
                    "Will you be thinking about this same problem next year?"
                )
                val topicTitle = if (scenario.length > 25) scenario.take(25) + "..." else scenario
                _selectedTopic.value = SimulationTopic(
                    title = topicTitle,
                    description = scenario,
                    defaultQuestions = smartQuestions
                )
                _activeQuestions.value = smartQuestions
                _currentQuestionIndex.value = 0
                _focusScreenState.value = FocusScreenState.ACTIVE_SESSION
                _countdownSeconds.value = 60
                startTimer()
            } finally {
                _isGeneratingQuestions.value = false
            }
        }
    }

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _sentimentLabel = MutableStateFlow("UNSURE")
    val sentimentLabel: StateFlow<String> = _sentimentLabel.asStateFlow()

    private val _aiReflection = MutableStateFlow("Tap YES or NO rapidly, or hold Mic to reflect your raw reaction.")
    val aiReflection: StateFlow<String> = _aiReflection.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var timerJob: Job? = null

    fun selectTopic(topic: SimulationTopic) {
        _selectedTopic.value = topic
        resetActiveSimulation()
    }

    fun startTimer() {
        if (_isTimerActive.value) return
        _isTimerActive.value = true
        timerJob = viewModelScope.launch {
            while (_countdownSeconds.value > 0 && _isTimerActive.value) {
                delay(1000)
                _countdownSeconds.value = _countdownSeconds.value - 1
            }
            if (_countdownSeconds.value == 0) {
                _isTimerActive.value = false
                evaluateFullSessionAndLog()
            }
        }
    }

    fun pauseTimer() {
        _isTimerActive.value = false
        timerJob?.cancel()
    }

    fun resetActiveSimulation() {
        pauseTimer()
        _countdownSeconds.value = 60
        _currentQuestionIndex.value = 0
        _activeQuestions.value = _selectedTopic.value.defaultQuestions
        _transcription.value = ""
        _sentimentLabel.value = "UNSURE"
        _aiReflection.value = "Tap YES or NO rapidly, or hold Mic to reflect your raw reaction."
        _confrontedProbe.value = ""
        _rapidFireAnswers.value = emptyList()
        _focusScreenState.value = FocusScreenState.HOME
    }

    fun nextQuestion() {
        val nextIndex = _currentQuestionIndex.value + 1
        if (nextIndex < _activeQuestions.value.size) {
            _currentQuestionIndex.value = nextIndex
            _transcription.value = ""
            _sentimentLabel.value = "UNSURE"
            _aiReflection.value = "Tap YES or NO rapidly, or hold Mic to reflect your raw reaction."
            _focusScreenState.value = FocusScreenState.ACTIVE_SESSION
            _countdownSeconds.value = 60
            startTimer()
        } else {
            resetActiveSimulation()
        }
    }

    fun updateRms(rms: Float) {
        // Map rms (which is often around -2 to 10) to a nicer wave size 0..1
        val normalized = ((rms + 2) / 12f).coerceIn(0f, 1f)
        _sentimentWaveRms.value = normalized
    }

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
        if (recording) {
            _transcription.value = "Listening to your gut..."
        }
    }

    fun submitRapidFireAnswer(choice: String, customText: String = "") {
        val currentQuestion = _activeQuestions.value.getOrNull(_currentQuestionIndex.value) ?: ""
        val reflectionText = if (customText.isNotEmpty()) customText else _transcription.value.ifEmpty { "" }
        
        val answer = RapidFireAnswer(
            question = currentQuestion,
            choice = choice,
            reflectionText = reflectionText
        )
        
        _rapidFireAnswers.value = _rapidFireAnswers.value + answer
        _transcription.value = ""
        
        val nextIndex = _currentQuestionIndex.value + 1
        if (_activeQuestions.value.isNotEmpty()) {
            _currentQuestionIndex.value = nextIndex % _activeQuestions.value.size
        } else {
            _currentQuestionIndex.value = 0
        }
    }

    fun evaluateFullSessionAndLog() {
        pauseTimer()
        _focusScreenState.value = FocusScreenState.VERDICT

        val scenario = _dilemmaScenario.value
        val questions = _clarifyingQuestions.value
        val answers = _clarifyingAnswers.value

        val clarifyingQA = StringBuilder()
        for (i in 0 until questions.size) {
            val q = questions.getOrNull(i) ?: ""
            val a = answers.getOrNull(i) ?: "Skipped"
            clarifyingQA.append("Q: $q -> A: $a\n")
        }

        val rapidFireQA = StringBuilder()
        _rapidFireAnswers.value.forEachIndexed { index, ans ->
            rapidFireQA.append("${index + 1}. Q: ${ans.question} -> Response: ${ans.choice} ${if (ans.reflectionText.isNotEmpty()) "(Reflection: ${ans.reflectionText})" else ""}\n")
        }

        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            viewModelScope.launch {
                _isLoading.value = true
                val sentiment = "DECIDED"
                val analysis = "You have survived the 60-second high-intensity pressure bypass. Your subconscious has processed the raw trade-offs. The path of least regret is calling you. Go forth with confidence."
                val probe = "Are you ready to commit to this path with absolute certainty?"
                _sentimentLabel.value = sentiment
                _aiReflection.value = analysis
                _confrontedProbe.value = probe

                val decision = DecisionLog(
                    simulationTitle = _selectedTopic.value.title,
                    question = "Gordian Knot Untied",
                    choice = "CALM / FREE",
                    sentiment = sentiment,
                    reflection = "Completed 60s session with ${_rapidFireAnswers.value.size} answers.",
                    aiAnalysis = analysis
                )
                repository.insert(decision)
                _isLoading.value = false
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val clarStr = clarifyingQA.toString().ifEmpty { "None (Bypassed clarifying step)" }
            val rfStr = rapidFireQA.toString().ifEmpty { "None (User was silent during rapid-fire)" }

            val systemInstruction = "You are an expert cognitive psychologist specializing in rapid gut-instinct bypass and final decisional resolution. " +
                    "The user has this dilemma: '$scenario'.\n" +
                    "Dialogue where we clarified their dilemma:\n$clarStr\n" +
                    "During a high-pressure 60-second rapid-fire session, they gave the following reactions:\n$rfStr\n\n" +
                    "Analyze their answers deeply. Look for inconsistencies, emotional triggers, subconscious patterns, and where their gut stance truly lies versus their rationalizations. " +
                    "Synthesize this into a final definitive diagnostic breakthrough (The Gordian Verdict). " +
                    "Your response MUST be in JSON format with exactly three string fields:\n" +
                    "1. \"sentiment\": A single short status or affective state representing their emotional stance (e.g., 'CONFRONTED', 'RESOLVED', 'EMERGENT CLARITY', 'DIVIDED GUTS').\n" +
                    "2. \"analysis\": A powerful, deep, compassionate 3-4 sentence psychological breakdown showing them what their gut actually wants and how to untie the knot.\n" +
                    "3. \"probe\": A final provoking, empowering query or action step for them to move forward.\n" +
                    "Output ONLY the JSON object. Do not include markdown or formatting."

            val request = GenerateContentRequest(
                contents = listOf(
                    ApiContent(
                        parts = listOf(
                            ApiPart(text = "Synthesize a final Gordian Verdict and return JSON.")
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.8f,
                    responseMimeType = "application/json"
                ),
                systemInstruction = ApiContent(
                    parts = listOf(ApiPart(text = systemInstruction))
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonObject = JSONObject(cleanJson)
                val sentiment = jsonObject.optString("sentiment", "CONFRONTED").uppercase()
                val analysis = jsonObject.optString("analysis", "Your cognitive alignment is complete. Release your analytical hesitation and trust your deeper instincts.")
                val probe = jsonObject.optString("probe", "Are you ready to take the first step?")

                _sentimentLabel.value = sentiment
                _aiReflection.value = analysis
                _confrontedProbe.value = probe

                val decision = DecisionLog(
                    simulationTitle = _selectedTopic.value.title,
                    question = "Gordian Knot Untied",
                    choice = "CALM / FREE",
                    sentiment = sentiment,
                    reflection = "Completed 60s session with ${_rapidFireAnswers.value.size} responses.",
                    aiAnalysis = "$analysis\n\n**CONFRONTED PROBE:** $probe"
                )
                repository.insert(decision)
            } catch (e: Exception) {
                // Fallback
                val sentiment = "DECIDED"
                val analysis = "The 60s pressure session has concluded. Your subconscious has spoken through the rapid answers. Move forward without looking back."
                val probe = "Are you ready to commit to this path with absolute certainty?"
                _sentimentLabel.value = sentiment
                _aiReflection.value = analysis
                _confrontedProbe.value = probe

                val decision = DecisionLog(
                    simulationTitle = _selectedTopic.value.title,
                    question = "Gordian Knot Untied",
                    choice = "CALM / FREE",
                    sentiment = sentiment,
                    reflection = "Completed 60s session.",
                    aiAnalysis = analysis
                )
                repository.insert(decision)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Save decision log
    fun logDecision(choice: String, customText: String = "") {
        val question = _activeQuestions.value.getOrNull(_currentQuestionIndex.value) ?: "No active question"
        val topic = _selectedTopic.value.title
        val reflectionText = if (customText.isNotEmpty()) customText else _transcription.value.ifEmpty { "None" }

        pauseTimer()
        _focusScreenState.value = FocusScreenState.VERDICT

        viewModelScope.launch {
            _isLoading.value = true
            val (sentiment, analysis) = performSentimentAnalysis(topic, question, choice, reflectionText)
            
            _sentimentLabel.value = sentiment
            _aiReflection.value = analysis

            val decision = DecisionLog(
                simulationTitle = topic,
                question = question,
                choice = choice,
                sentiment = sentiment,
                reflection = reflectionText,
                aiAnalysis = analysis
            )
            repository.insert(decision)
            _isLoading.value = false
        }
    }

    // Delete a decision log
    fun deleteDecision(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    // Clear all history
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Key management
    private fun getApiKey(): String {
        val prefs = getApplication<Application>().getSharedPreferences("gordian_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        if (savedKey.isNotEmpty()) return savedKey
        return BuildConfig.GEMINI_API_KEY
    }

    fun saveApiKey(key: String) {
        val prefs = getApplication<Application>().getSharedPreferences("gordian_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun getSavedApiKey(): String {
        val prefs = getApplication<Application>().getSharedPreferences("gordian_prefs", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    // AI sentiment analysis and feedback logic
    private suspend fun performSentimentAnalysis(
        topic: String,
        question: String,
        choice: String,
        reflectionText: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback logic when no Gemini Key is provided
            return@withContext runLocalSentimentFallback(choice, reflectionText)
        }

        val systemInstruction = "You are a sharp cognitive psychologist. The user is in a 60-second high-intensity decision-making 'Gordian'. " +
                "They answered '$choice' to the question '$question' under the topic '$topic'. " +
                "They added this raw reflection: '$reflectionText'. " +
                "Analyze their state and output a valid JSON containing exactly two string fields: " +
                "'sentiment' (must be one of: CONFIDENT, FEARFUL, AMBITIOUS, ANXIOUS, UNSURE) and " +
                "'reflection' (a sharp, diagnostic 1-sentence assessment of their state, cutting through excuses). " +
                "Output ONLY raw JSON. No markdown formatting, no code blocks."

        val request = GenerateContentRequest(
            contents = listOf(
                ApiContent(
                    parts = listOf(
                        ApiPart(text = "Analyze this state: Topic='$topic', Question='$question', Choice='$choice', Reflection='$reflectionText'. Please return JSON.")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.5f,
                responseMimeType = "application/json"
            ),
            systemInstruction = ApiContent(
                parts = listOf(ApiPart(text = systemInstruction))
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            // Clean up any potential markdown wrap
            val cleanJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObject = JSONObject(cleanJson)
            val sentiment = jsonObject.optString("sentiment", "UNSURE").uppercase()
            val reflection = jsonObject.optString("reflection", "Your gut choice is logged. Trust your subconscious.")
            Pair(sentiment, reflection)
        } catch (e: Exception) {
            // If API call fails, gracefully use local fallback
            runLocalSentimentFallback(choice, reflectionText)
        }
    }

    private fun runLocalSentimentFallback(choice: String, reflection: String): Pair<String, String> {
        val lowerText = reflection.lowercase()
        val sentiment = when {
            lowerText.contains("fear") || lowerText.contains("scared") || lowerText.contains("worry") || lowerText.contains("afraid") -> "FEARFUL"
            lowerText.contains("excited") || lowerText.contains("want") || lowerText.contains("grow") || lowerText.contains("ambition") -> "AMBITIOUS"
            lowerText.contains("anxious") || lowerText.contains("panic") || lowerText.contains("stress") -> "ANXIOUS"
            lowerText.contains("yes") || lowerText.contains("confident") || lowerText.contains("sure") || lowerText.contains("trust") -> "CONFIDENT"
            lowerText.contains("maybe") || lowerText.contains("not sure") || lowerText.contains("don't know") -> "UNSURE"
            else -> if (choice == "YES") "CONFIDENT" else "FEARFUL"
        }

        val diagnostic = when (sentiment) {
            "FEARFUL" -> "You are resisting action to protect yourself from uncertainty. Is safety worth stagnation?"
            "AMBITIOUS" -> "Your alignment points towards expansion. The risk excites you more than the failure scares you."
            "ANXIOUS" -> "You are letting circular overthinking delay the inevitable choice. Stop simulating, start shipping."
            "CONFIDENT" -> "Pristine execution. You made this choice cleanly without requiring consensus validation."
            else -> "A hazy gut is usually a cover for a truth you already know but are avoiding."
        }

        return Pair(sentiment, diagnostic)
    }

    // AI customized question generation
    fun generateAiQuestionsForTopic(customTopicName: String, onComplete: () -> Unit = {}) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // No API Key, create local smart questions
            val smartQuestions = listOf(
                "Is your hesitation with '$customTopicName' actually saving you, or stalling you?",
                "If no one was looking, what would your answer be?",
                "Are you choosing out of ambition or fear for '$customTopicName'?",
                "Would the 5-years-older version of you regret staying or leaving?",
                "Is this a screaming 'hell yes', or a distraction?"
            )
            _selectedTopic.value = SimulationTopic(
                title = customTopicName,
                description = "Customized simulation on $customTopicName",
                defaultQuestions = smartQuestions
            )
            _activeQuestions.value = smartQuestions
            resetActiveSimulation()
            onComplete()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val systemInstruction = "You are a sharp cognitive psychologist. The user wants 5 quick, high-intensity decision-making questions (max 10 words each) designed to bypass the analytical brain and force a gut instinct answer for the topic: '$customTopicName'. " +
                    "Format your response as a JSON array of strings, like: [\"Question 1?\", \"Question 2?\"] " +
                    "Output ONLY the JSON array. No markdown formatting, no code blocks."

            val request = GenerateContentRequest(
                contents = listOf(
                    ApiContent(
                        parts = listOf(
                            ApiPart(text = "Generate 5 rapid-fire bypass questions in JSON array for: $customTopicName")
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    responseMimeType = "application/json"
                ),
                systemInstruction = ApiContent(
                    parts = listOf(ApiPart(text = systemInstruction))
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonArray = JSONArray(cleanJson)
                val generated = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    generated.add(jsonArray.getString(i))
                }

                if (generated.size >= 3) {
                    _selectedTopic.value = SimulationTopic(
                        title = customTopicName,
                        description = "Custom simulation: $customTopicName",
                        defaultQuestions = generated
                    )
                    _activeQuestions.value = generated
                    resetActiveSimulation()
                }
            } catch (e: Exception) {
                // Ignore, fall back to local smart questions
                val smartQuestions = listOf(
                    "Is your hesitation with '$customTopicName' actually saving you, or stalling you?",
                    "If no one was looking, what would your answer be?",
                    "Are you choosing out of ambition or fear?",
                    "Would the 5-years-older version of you regret staying or leaving?",
                    "Is this a screaming 'hell yes', or a distraction?"
                )
                _selectedTopic.value = SimulationTopic(
                    title = customTopicName,
                    description = "Custom simulation on $customTopicName",
                    defaultQuestions = smartQuestions
                )
                _activeQuestions.value = smartQuestions
                resetActiveSimulation()
            } finally {
                _isLoading.value = false
                onComplete()
            }
        }
    }
}
