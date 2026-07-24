package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DecisionLog
import com.example.ui.theme.*
import com.example.ui.components.CalmingUpliftingAnimation
import com.example.voice.VoiceRecognizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val viewModel: MainViewModel = viewModel()
    val activeTab by viewModel.activeTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        bottomBar = {
            BottomNavBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (shared except during active settings sometimes, but kept clean)
                HeaderSection(
                    activeTab = activeTab,
                    onHistoryClick = { viewModel.selectTab(ActiveTab.INSIGHTS) },
                    onFocusClick = { viewModel.selectTab(ActiveTab.FOCUS) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        ActiveTab.FOCUS -> FocusTabScreen(viewModel = viewModel)
                        ActiveTab.INSIGHTS -> InsightsTabScreen(viewModel = viewModel)
                        ActiveTab.CALIBRATE -> CalibrateTabScreen(viewModel = viewModel)
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    activeTab: ActiveTab,
    onHistoryClick: () -> Unit,
    onFocusClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_gordian_knot_1784487466464),
                contentDescription = "Gordian Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .shadow(elevation = 15.dp, shape = RoundedCornerShape(8.dp), clip = false)
            )
            Text(
                text = "Gordian",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            )
        }

        if (activeTab == ActiveTab.FOCUS) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, shape = CircleShape)
                    .clickable { onHistoryClick() }
                    .testTag("history_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "View History",
                    tint = GoldPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else if (activeTab == ActiveTab.INSIGHTS) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, shape = CircleShape)
                    .clickable { onFocusClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = "Back to Focus",
                    tint = GoldPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun FocusTabScreen(viewModel: MainViewModel) {
    val countdown by viewModel.countdownSeconds.collectAsState()
    val isTimerActive by viewModel.isTimerActive.collectAsState()
    val selectedTopic by viewModel.selectedTopic.collectAsState()
    val activeQuestions by viewModel.activeQuestions.collectAsState()
    val currentQuestionIndex by viewModel.currentQuestionIndex.collectAsState()
    val sentimentWaveRms by viewModel.sentimentWaveRms.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val transcription by viewModel.transcription.collectAsState()
    val sentimentLabel by viewModel.sentimentLabel.collectAsState()
    val aiReflection by viewModel.aiReflection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val focusScreenState by viewModel.focusScreenState.collectAsState()
    val rapidFireAnswers by viewModel.rapidFireAnswers.collectAsState()
    val confrontedProbe by viewModel.confrontedProbe.collectAsState()

    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var clarifyingInput by remember { mutableStateOf("") }

    val currentOnResult by rememberUpdatedState { result: String ->
        viewModel.setRecording(false)
        when (focusScreenState) {
            FocusScreenState.HOME -> {
                textInput = result
            }
            FocusScreenState.CLARIFYING -> {
                clarifyingInput = result
            }
            else -> {
                viewModel.submitRapidFireAnswer(choice = "REFLECT", customText = result)
            }
        }
    }

    val voiceRecognizer = remember {
        VoiceRecognizer(
            context = context,
            onStart = {
                viewModel.setRecording(true)
            },
            onResult = { result ->
                currentOnResult(result)
            },
            onError = { error ->
                viewModel.setRecording(false)
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            },
            onRmsChanged = { rms ->
                viewModel.updateRms(rms)
            }
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceRecognizer.startListening()
        } else {
            Toast.makeText(context, "Microphone permission required for rapid reflection voice analysis.", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecognizer.stopListening()
        }
    }

    when (focusScreenState) {
        FocusScreenState.HOME -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Logo & Title
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_gordian_knot_1784487466464),
                            contentDescription = "Gordian Knot Logo",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
                                .shadow(10.dp, RoundedCornerShape(16.dp))
                        )
                        Text(
                            text = "GORDIAN",
                            color = GoldPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "BYPASS COGNITIVE OVERLOAD",
                            color = TextLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "A modern Art Deco therapeutic tool. Speak or type your scenario to let the cognitive analysis engine formulate hyper-personalized bypass questions.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // Simplified Scenario Entry Input Area
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "UNRAVEL A COGNITIVE KNOT",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = "Type or speak your dilemma in full detail (e.g., 'Should I take the new job or stay comfortable?')...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .testTag("dilemma_text_input")
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.key == Key.Enter) {
                                        if (textInput.isNotBlank()) {
                                            viewModel.startDilemmaSetup(textInput)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (textInput.isNotBlank()) {
                                    viewModel.startDilemmaSetup(textInput)
                                }
                            })
                        )

                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(GoldPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Listening to your gut...",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }

                        // Voice Trigger Button
                        Button(
                            onClick = {
                                val permission = Manifest.permission.RECORD_AUDIO
                                if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    if (isRecording) {
                                        voiceRecognizer.stopListening()
                                    } else {
                                        voiceRecognizer.startListening()
                                    }
                                } else {
                                    requestPermissionLauncher.launch(permission)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) RedAccent.copy(alpha = 0.2f) else DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .border(
                                    1.dp,
                                    if (isRecording) RedAccent else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Speak Dilemma",
                                    tint = if (isRecording) RedAccent else GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isRecording) "STOP SPEAKING" else "TAP TO SPEAK SCENARIO",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // CTA Button: "UNTIE THE KNOT"
                item {
                    Button(
                        onClick = {
                            if (textInput.isNotEmpty()) {
                                viewModel.startDilemmaSetup(textInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(12.dp, RoundedCornerShape(24.dp)),
                        enabled = textInput.isNotEmpty()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Untie My Knot",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "UNTIE MY KNOT",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        FocusScreenState.CLARIFYING -> {
            val dilemmaScenario by viewModel.dilemmaScenario.collectAsState()
            val clarifyingQuestions by viewModel.clarifyingQuestions.collectAsState()
            val currentClarifyingQuestionIndex by viewModel.currentClarifyingQuestionIndex.collectAsState()
            val isGeneratingQuestions by viewModel.isGeneratingQuestions.collectAsState()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Info
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = "Analysis",
                            tint = GoldPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "COGNITIVE CLARIFICATION",
                            color = GoldPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "THE ENGINE IS UNRAVELING YOUR DILEMMA",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isGeneratingQuestions || clarifyingQuestions.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(48.dp))
                                Text(
                                    text = "FORMULATING PROBES...",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "The deep psychological engine is drilling into your cognitive knot to generate hyper-personalized bypass questions.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val currentQuestion = clarifyingQuestions.getOrNull(currentClarifyingQuestionIndex) ?: ""

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CLARIFICATION PROBE",
                                        color = GoldPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "STEP ${currentClarifyingQuestionIndex + 1} OF ${clarifyingQuestions.size}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = currentQuestion,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 24.sp
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                 OutlinedTextField(
                                    value = clarifyingInput,
                                    onValueChange = { clarifyingInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Answer honestly, or use voice. Keep it raw...",
                                            color = TextMuted,
                                            fontSize = 13.sp
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.key == Key.Enter) {
                                                viewModel.submitClarifyingAnswer(clarifyingInput)
                                                clarifyingInput = ""
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = {
                                        viewModel.submitClarifyingAnswer(clarifyingInput)
                                        clarifyingInput = ""
                                    })
                                )

                                if (isRecording) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(GoldPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Listening to your voice...",
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Voice input button
                                    Button(
                                        onClick = {
                                            val permission = Manifest.permission.RECORD_AUDIO
                                            if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                if (isRecording) {
                                                    voiceRecognizer.stopListening()
                                                } else {
                                                    voiceRecognizer.startListening()
                                                }
                                            } else {
                                                requestPermissionLauncher.launch(permission)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRecording) RedAccent.copy(alpha = 0.2f) else DarkSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .weight(1f)
                                            .border(
                                                1.dp,
                                                if (isRecording) RedAccent else Color.White.copy(alpha = 0.05f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                                contentDescription = "Speak",
                                                tint = if (isRecording) RedAccent else GoldPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isRecording) "STOP" else "SPEAK ANSWER",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Next Button
                                    Button(
                                        onClick = {
                                            viewModel.submitClarifyingAnswer(clarifyingInput)
                                            clarifyingInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .weight(1.2f)
                                    ) {
                                        Text(
                                            text = if (currentClarifyingQuestionIndex + 1 == clarifyingQuestions.size) "BEGIN BYPASS" else "NEXT PROBE",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        TextButton(
                            onClick = { viewModel.skipClarifications() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SKIP CLARIFICATIONS & START SESSION",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        FocusScreenState.ACTIVE_SESSION -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Circular Progress Timer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clickable {
                                if (isTimerActive) {
                                    viewModel.pauseTimer()
                                } else {
                                    viewModel.startTimer()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer background circle
                            drawCircle(
                                color = DarkSurface,
                                radius = size.minDimension / 2f - 8.dp.toPx(),
                                style = Stroke(width = 6.dp.toPx())
                            )
                            // Animated arc
                            val sweepAngle = (countdown / 60f) * 360f
                            drawArc(
                                color = GoldPrimary,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(
                                    width = 6.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = countdown.toString(),
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = if (isTimerActive) "SECONDS" else "TAP TO START",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    Text(
                        text = "Simulation: ${selectedTopic.title}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "RAPID ANSWERS: ${rapidFireAnswers.size}",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // 2. Psychology Card Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .background(DarkSurface, shape = RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(32.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PsychologyAlt,
                            contentDescription = "Psychology Bypass",
                            tint = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )

                        val activeQuestion = activeQuestions.getOrNull(currentQuestionIndex) ?: "Ready to test your gut?"
                        Text(
                            text = activeQuestion,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Indicator dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeQuestions.forEachIndexed { idx, _ ->
                                Box(
                                    modifier = Modifier
                                        .size(if (idx == currentQuestionIndex) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (idx == currentQuestionIndex) GoldPrimary else Color.White.copy(
                                                alpha = 0.2f
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                // 3. Bottom interaction controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sound Wave/Reflection view
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(24.dp)
                            ) {
                                val baseHeights = listOf(6, 12, 18, 24, 16, 8)
                                baseHeights.forEach { heightVal ->
                                    val dynamicHeight = if (isRecording) {
                                        (heightVal * (0.3f + sentimentWaveRms * 1.5f)).dp
                                    } else {
                                        4.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(dynamicHeight)
                                            .background(GoldPrimary, shape = RoundedCornerShape(2.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = if (isRecording) "RECORDING REFLECTION..." else "MICROPHONE OPTIONAL",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // AI Reflection diagnostic text
                        Text(
                            text = if (isRecording) transcription else "Speak or write your raw reflection now. Press YES/NO to decide.",
                            color = if (isRecording) Color.White else TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )

                        // Optional text box to write reflection if mic is noisy/not available
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Or type rapid reflection here...", fontSize = 11.sp, color = TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (textInput.isNotEmpty()) {
                                        viewModel.submitRapidFireAnswer(choice = "REFLECT", customText = textInput)
                                        textInput = ""
                                    }
                                },
                                enabled = textInput.isNotEmpty(),
                                modifier = Modifier
                                    .background(if (textInput.isNotEmpty()) GoldPrimary else DarkSurface, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Send text", tint = Color.White)
                            }

                            // Hold/Click to speak mic button
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        voiceRecognizer.stopListening()
                                        viewModel.setRecording(false)
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .background(if (isRecording) RedAccent else DarkSurface, shape = RoundedCornerShape(12.dp))
                                    .testTag("mic_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice reflect",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // YES / NO Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // NO Button
                        Button(
                            onClick = {
                                viewModel.submitRapidFireAnswer("NO")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("no_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "No", tint = RedAccent)
                                Text(text = "No", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // YES Button
                        Button(
                            onClick = {
                                viewModel.submitRapidFireAnswer("YES")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("yes_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Yes", tint = Color.Black)
                                Text(text = "Yes", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Abort Simulation Button
                    TextButton(
                        onClick = { viewModel.resetActiveSimulation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "ABORT SESSION",
                            color = RedAccent.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        FocusScreenState.VERDICT -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Calm, Peaceful Sunrise Header Design
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CalmingUpliftingAnimation(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        Text(
                            text = "THE GORDIAN NODE UNTIED",
                            color = GoldPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "BREATHE DEEPLY • COGNITIVE CLARITY RESTORED",
                            color = TextLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Serene Verdict Insights Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLoading) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(48.dp))
                                    Text(
                                        text = "DECODING YOUR INNER INTUITION...",
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = "Releasing analytical hesitation and aligning your subconscious desire...",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                // Peaceful state header
                                Text(
                                    text = "CONGRATULATIONS. YOU HAVE CROSSED THE HURDLE.",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.5.sp
                                )

                                // Diagnostic Labels
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Sentiment State Pill
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(GoldPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("AFFECTIVE RESPONSE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = sentimentLabel,
                                                color = GoldPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // Action Pill
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("SENSE OF RELEASE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "CALM / FREE",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                // Question block
                                if (confrontedProbe.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "FINAL ALIGNING PROBE",
                                            color = GoldPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "\"$confrontedProbe\"",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }

                                // Deep Serene Insight
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Spa,
                                            contentDescription = "Peaceful Analysis",
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "GUT-LEVEL ALIGNMENT & REFLECTION",
                                            color = GoldPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Text(
                                        text = aiReflection,
                                        color = TextLight,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Interaction controls for next steps
                if (!isLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // COMPLETE & RETURN HOME CTA
                            Button(
                                onClick = { viewModel.resetActiveSimulation() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("COMPLETE & RETURN HOME", color = Color.Black, fontWeight = FontWeight.Bold)
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            }

                            // CHECK CHRONOLOGY
                            Button(
                                onClick = { viewModel.selectTab(ActiveTab.INSIGHTS) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Analytics, contentDescription = "Log", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Text("VIEW IN CHRONOLOGY LOG", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsTabScreen(viewModel: MainViewModel) {
    val decisions by viewModel.allDecisions.collectAsState()

    if (decisions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Analytics,
                contentDescription = "Empty insights",
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No decisions logged yet.",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Run a 60-second Gordian decision-making simulation to populate insights and bypass cognitive overload.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // Calculate Statistics
        val total = decisions.size
        val yesCount = decisions.count { it.choice == "YES" }
        val noCount = decisions.count { it.choice == "NO" }
        val reflectCount = decisions.count { it.choice == "REFLECT" }

        val sentimentDistribution = decisions.groupBy { it.sentiment }
            .mapValues { it.value.size }

        val topSentiment = sentimentDistribution.maxByOrNull { it.value }?.key ?: "UNSURE"

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Stats summary card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "GUT ANALYTICS",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Runs", color = TextMuted, fontSize = 11.sp)
                                Text("$total", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Yes / No Split", color = TextMuted, fontSize = 11.sp)
                                Text("$yesCount / $noCount", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Dominant Gut State", color = TextMuted, fontSize = 11.sp)
                                Text(topSentiment, color = GoldPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Distribution bar
                        if (total > 0) {
                            val yesPct = yesCount.toFloat() / total
                            val noPct = noCount.toFloat() / total
                            val refPct = reflectCount.toFloat() / total
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                if (yesPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(yesPct)
                                            .fillMaxHeight()
                                            .background(GoldPrimary)
                                    )
                                }
                                if (noPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(noPct)
                                            .fillMaxHeight()
                                            .background(RedAccent)
                                    )
                                }
                                if (refPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(refPct)
                                            .fillMaxHeight()
                                            .background(Color.White.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "DECISION CHRONOLOGY",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            items(decisions) { decision ->
                DecisionCard(decision = decision, onDelete = { viewModel.deleteDecision(decision.id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecisionCard(decision: DecisionLog, onDelete: () -> Unit) {
    val sdf = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
    val formattedDate = sdf.format(Date(decision.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("decision_log_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = decision.simulationTitle.uppercase(),
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = formattedDate,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Choice Pill
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (decision.choice) {
                                    "YES" -> GoldPrimary.copy(alpha = 0.15f)
                                    "NO" -> RedAccent.copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = decision.choice,
                            color = when (decision.choice) {
                                "YES" -> GoldPrimary
                                "NO" -> RedAccent
                                else -> Color.White
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "\"${decision.question}\"",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (decision.reflection.isNotEmpty() && decision.reflection != "None") {
                Text(
                    text = "Reflected: ${decision.reflection}",
                    color = TextLight,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = "AI Analysis",
                    tint = GoldPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = decision.aiAnalysis,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrateTabScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var userApiKey by remember { mutableStateOf(viewModel.getSavedApiKey()) }

    // List of curated guides
    data class DecisionGuide(
        val id: Int,
        val title: String,
        val description: String,
        val origin: String,
        val readTime: String,
        val fullContent: String,
        val coreTakeaway: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    val decisionGuides = remember {
        listOf(
            DecisionGuide(
                id = 1,
                title = "The Gordian Cut",
                description = "Decisive bold actions that render complex deliberations completely irrelevant.",
                origin = "Ancient Greek Strategy",
                readTime = "2 min read",
                icon = Icons.Default.ContentCut,
                fullContent = "In 333 BC, Alexander the Great confronted the Gordian Knot—an incredibly complex tangle of cornel-bark rope. Ancient prophecy held that whoever untied it would rule Asia. Instead of spending days trying to carefully loosen the knot, Alexander drew his sword and slashed through it with a single stroke.\n\n" +
                        "In psychology, a 'Gordian Cut' is any decisive, bold action that renders the entire complex problem irrelevant. When stuck in decision loops, we often over-analyze variables that don't matter. \n\n" +
                        "How to apply it:\n" +
                        "1. Identify the single variable holding you back.\n" +
                        "2. Ask: 'What is the most direct, irreversible action that makes this whole debate unnecessary?'\n" +
                        "3. Execute that action with absolute confidence, accepting that perfection is an illusion.",
                coreTakeaway = "Don't untangle the knot when you can simply slice it in half."
            ),
            DecisionGuide(
                id = 2,
                title = "The 10/10/10 Rule",
                description = "A powerful heuristic to bypass short-term panic and focus on future consequence.",
                origin = "Suzy Welch (Cognitive Psychology)",
                readTime = "3 min read",
                icon = Icons.Default.Update,
                fullContent = "Our analytical brains are easily hijacked by near-term emotional anxiety. The fear of failure, social rejection, or temporary discomfort makes any decision feel like life or death. The 10/10/10 rule forces immediate cognitive distance.\n\n" +
                "To use this framework, look at your primary choice and ask three simple questions:\n" +
                "1. How will I feel about this choice 10 minutes from now?\n" +
                "2. How will I feel about it 10 months from now?\n" +
                "3. How will I feel about it 10 years from now?\n\n" +
                "Typically, what feels like an agonizingly difficult choice today is completely forgotten in ten months, let alone ten years. This perspective immediately lowers your cortisol levels and allows your gut instinct to speak clearly.",
                coreTakeaway = "Immediate pain is temporary. Future clarity is permanent."
            ),
            DecisionGuide(
                id = 3,
                title = "Regret Minimization",
                description = "Aligning your choices with your future elderly self to eliminate fear of failure.",
                origin = "Jeff Bezos (Decisional Architecture)",
                readTime = "2 min read",
                icon = Icons.Default.Spa,
                fullContent = "When deciding whether to quit a secure wall street job to start Amazon, Jeff Bezos formulated the Regret Minimization Framework. He projected himself forward to age 80 and looked back on his life.\n\n" +
                "At age 80, he realized he wouldn't regret trying and failing to build a startup. But he would absolutely regret never trying at all. That realization instantly bypassed all the complex calculations about short-term compensation, career stability, and business risk.\n\n" +
                "How to apply it:\n" +
                "Imagine you are 80 years old, looking back. Which path will you regret not taking? Choose that path.",
                coreTakeaway = "We rarely regret bold failures. We always regret safe inactions."
            ),
            DecisionGuide(
                id = 4,
                title = "Two-Way Door Principle",
                description = "Speed up decisions by separating irreversible actions from reversible ones.",
                origin = "Type 1 vs. Type 2 Decisions",
                readTime = "3 min read",
                icon = Icons.Default.MeetingRoom,
                fullContent = "Decisional paralysis often occurs because we treat every decision as a monumental, permanent event. In reality, decisions fall into two categories:\n\n" +
                "Type 1 (One-Way Doors): These decisions are nearly irreversible. If you walk through, you cannot return. Examples: selling a company, signing a long-term commercial lease, or getting a tattoo. These should be made slowly, deliberately, with counsel.\n\n" +
                "Type 2 (Two-Way Doors): These are easily reversible. If you don't like the outcome, you can walk back through the door. Examples: changing a pricing tier, launching a pilot feature, or hiring a contractor. These should be made as rapidly as possible, often with only 70% of the desired information.\n\n" +
                "Bypass rule: If it's a two-way door, stop thinking and ship it immediately. Fail fast and iterate.",
                coreTakeaway = "If you can walk back, don't stand at the door over-thinking."
            )
        )
    }

    var expandedGuideId by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (expandedGuideId != null) {
            val guide = decisionGuides.first { it.id == expandedGuideId }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedGuideId = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "BACK TO METHODS",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Article Title Header
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = guide.origin.uppercase(),
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = guide.title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = guide.readTime, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.size(4.dp).background(TextMuted, CircleShape))
                            Text(text = "Curated Blog View", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    // Friendly Reader Body Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = guide.fullContent,
                                color = TextLight,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            // Takeaway block
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GoldPrimary.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "CORE TAKEAWAY",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = guide.coreTakeaway,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Back to methods footer CTA
                    Button(
                        onClick = { expandedGuideId = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("FINISH READING", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        } else {
            // Header Section
            item {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "DECISION-MAKING GUIDES",
                        color = GoldPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Curated psychologist models and mental frameworks to bypass analytical loops and anxiety.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // List of pre-configured guide cards
            items(decisionGuides) { guide ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedGuideId = guide.id }
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(GoldPrimary.copy(alpha = 0.1f), shape = CircleShape)
                                .border(1.dp, GoldPrimary.copy(alpha = 0.3f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = guide.icon,
                                contentDescription = guide.title,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = guide.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = guide.readTime,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = guide.description,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Section: API Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DEEP COGNITIVE CALIBRATION",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Enter your Cognitive API key to unlock real-time, deep psychological analysis and personalized diagnostic breakthroughs.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = userApiKey,
                            onValueChange = {
                                userApiKey = it
                                viewModel.saveApiKey(it)
                            },
                            placeholder = { Text("Enter API Key", fontSize = 12.sp, color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Text(
                            text = "Note: If left empty, the app will fall back to local rule-based psychology models, keeping you fully functional.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            // Section: Reset Data
            item {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        Toast.makeText(context, "Log history successfully cleared.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text("Purge Log History", color = RedAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeTab: ActiveTab,
    onTabSelected: (ActiveTab) -> Unit
) {
    NavigationBar(
        containerColor = DarkBackground,
        tonalElevation = 0.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f)) // clean border
    ) {
        NavigationBarItem(
            selected = activeTab == ActiveTab.FOCUS,
            onClick = { onTabSelected(ActiveTab.FOCUS) },
            icon = {
                Icon(
                    imageVector = if (activeTab == ActiveTab.FOCUS) Icons.Default.Bolt else Icons.Outlined.Bolt,
                    contentDescription = "Focus"
                )
            },
            label = { Text("FOCUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoldPrimary,
                selectedTextColor = GoldPrimary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            selected = activeTab == ActiveTab.INSIGHTS,
            onClick = { onTabSelected(ActiveTab.INSIGHTS) },
            icon = {
                Icon(
                    imageVector = if (activeTab == ActiveTab.INSIGHTS) Icons.Default.Analytics else Icons.Outlined.Analytics,
                    contentDescription = "Insights"
                )
            },
            label = { Text("INSIGHTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoldPrimary,
                selectedTextColor = GoldPrimary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = Color.Transparent
            )
        )

        NavigationBarItem(
            selected = activeTab == ActiveTab.CALIBRATE,
            onClick = { onTabSelected(ActiveTab.CALIBRATE) },
            icon = {
                Icon(
                    imageVector = if (activeTab == ActiveTab.CALIBRATE) Icons.Default.MenuBook else Icons.Outlined.MenuBook,
                    contentDescription = "Guides"
                )
            },
            label = { Text("GUIDES", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoldPrimary,
                selectedTextColor = GoldPrimary,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted,
                indicatorColor = Color.Transparent
            )
        )
    }
}
