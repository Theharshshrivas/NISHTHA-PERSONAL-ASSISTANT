package com.harsh.aisha

import com.harsh.aisha.database.ConversationMessage
import com.harsh.aisha.database.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.harsh.aisha.ai.AIRequestRouter
import com.harsh.aisha.ai.LocalCommandEngine
import com.harsh.aisha.ai.ResponseSource
import com.harsh.aisha.privacy.OnlineApprovalGate
import kotlinx.coroutines.launch
import java.util.Locale

private val Background = Color(0xFF100D20)
private val Purple = Color(0xFF9B7BFF)
private val Teal = Color(0xFF55E4D0)
private val CardColor = Color(0xFF201A35)

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private val conversationRepository by lazy {
    ConversationRepository(applicationContext)
}

private var conversationHistory by mutableStateOf(
    emptyList<ConversationMessage>()
)

private var conversationStatus by mutableStateOf(
    "Loading local conversation history..."
)

private var conversationHistoryReady by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechStatus by mutableStateOf("Ready")
    private var ttsReady = false
    private var onRecognizedText: ((String) -> Unit)? = null
    private var requestMicrophonePermission: (() -> Unit)? = null

    private val requestRouter by lazy {
        AIRequestRouter(
            commandEngine = LocalCommandEngine("NISHTHA"),
            localModel = null,
            onlineProvider = null,
            approvalGate = OnlineApprovalGate()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            runOnUiThread {
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    val result = tts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        speechStatus = "Hindi TTS voice unavailable; check device voice settings"
                    }
                }
            }
        }

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) startListening()
                else speechStatus = "Microphone permission denied"
            }

            requestMicrophonePermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    secondary = Teal,
                    background = Background,
                    surface = CardColor
                )
            ) {
                NishthaApp(
    speechStatus = speechStatus,

    onSpeak = { speakText(it) },

    onListen = { callback ->
        onRecognizedText = callback
        requestOrStartListening()
    },

    conversationHistory = conversationHistory,
    conversationStatus = conversationStatus,
    conversationHistoryReady = conversationHistoryReady,

    onClearHistory = {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    conversationRepository.clearHistory()
                }

                conversationHistory = emptyList()
                conversationStatus =
                    "All chat history deleted from this device."

            } catch (_: Exception) {
                conversationStatus =
                    "Could not clear conversation history."
            }
        }
    },

    onSend = { input, onResult ->
        lifecycleScope.launch {

            val answer = requestRouter.respond(input)

            val sourceLabel = when (answer.source) {
                ResponseSource.LOCAL_COMMANDS -> "LOCAL COMMAND"
                ResponseSource.LOCAL_MODEL -> "ON-DEVICE AI"
                ResponseSource.ONLINE_MODEL -> "ONLINE (APPROVED)"
                ResponseSource.OFFLINE_FALLBACK -> "OFFLINE FALLBACK"
            }

            try {
                conversationHistory = withContext(Dispatchers.IO) {
                    conversationRepository.save(input, answer.text)
                    conversationRepository.getRecent(50)
                }

                conversationStatus =
                    "Conversation encrypted and saved locally."

            } catch (_: Exception) {
                conversationStatus =
                    "Reply generated, but history could not be saved."
            }

            onResult(answer.text, sourceLabel)
        }
    }
)
                
            }
        }
    }

    private fun speakText(message: String) {
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nishtha_response")
        } else {
            speechStatus = "Text-to-Speech is not ready"
        }
    }

    private fun requestOrStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechStatus = "Speech recognition service unavailable"
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            speechStatus = "On-device speech recognition requires Android 12 or newer"
            return
        }

        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            speechStatus = "On-device speech service unavailable. Use typed chat; no cloud voice fallback."
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            speechStatus = "Requesting microphone permission..."
            requestMicrophonePermission?.invoke()
            return
        }

        startListening()
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            speechStatus = "On-device voice recognition is unavailable on this device"
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            speechStatus = "Microphone permission required"
            return
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    speechStatus = "On-device listening... ab bolo"
                }

                override fun onBeginningOfSpeech() {
                    speechStatus = "Listening on this device..."
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    speechStatus = "Processing on-device speech..."
                }

                override fun onError(error: Int) {
                    speechStatus = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error. Please try again."
                        SpeechRecognizer.ERROR_CLIENT -> "Recognition stopped. Tap microphone to retry."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition busy. Wait and retry."
                        else -> "On-device voice recognition failed. Typed chat is still available."
                    }
                }

                override fun onResults(results: Bundle?) {
                    val spokenText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()

                    if (!spokenText.isNullOrBlank()) {
                        speechStatus = "On-device speech recognized"
                        onRecognizedText?.invoke(spokenText)
                    } else {
                        speechStatus = "No speech recognized. Try again."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "NISHTHA se baat karne ke liye bolo")
            }

            speechStatus = "Starting on-device microphone..."
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            speechStatus = "Could not start on-device microphone. Please retry."
        }
    }

    override fun onDestroy() {
        requestMicrophonePermission = null
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
@Composable
private fun NishthaApp(
    speechStatus: String,
    conversationHistory: List<ConversationMessage>,
    conversationStatus: String,
    conversationHistoryReady: Boolean,
    onClearHistory: () -> Unit,
    onSpeak: (String) -> Unit,
    onListen: (onResult: (String) -> Unit) -> Unit,
    onSend: (String, (String, String) -> Unit) -> Unit
) {

    var selectedTab by remember { mutableStateOf("Home") }
    var selectedView by remember { mutableStateOf("Close-up") }
    var selectedModel by remember { mutableIntStateOf(1) }
    var selectedOutfit by remember { mutableStateOf("Casual") }
    var message by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("Namaste! Main NISHTHA hoon. Mujhe Hello bolo!") }
    var responseSource by remember { mutableStateOf("LOCAL COMMAND") }
    var isSending by remember { mutableStateOf(false) }

    val tabs = listOf("Home", "Reminders", "Avatar", "Memory", "Settings")

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NISHTHA", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "YOUR PRIVATE PERSONAL ASSISTANT",
                        color = Teal,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                Text("✦", color = Purple, fontSize = 34.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab, fontSize = 9.sp) }
                    )
                }
            }

            when (selectedTab) {
                "Home" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(225.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF30234F), Color(0xFF152C3A))
                                ),
                                RoundedCornerShape(28.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .background(
                                        Brush.linearGradient(listOf(Purple, Teal)),
                                        RoundedCornerShape(52.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("N", fontSize = 58.sp, color = Background)
                            }
                            Text(
                                "Hey! I'm NISHTHA",
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Local-first assistant • Privacy by design",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    SectionTitle("QUICK ACTIONS")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard("💬", "Chat", Modifier.weight(1f)) { selectedTab = "Home" }
                        ActionCard("🔔", "Reminders", Modifier.weight(1f)) { selectedTab = "Reminders" }
                        ActionCard("🧠", "Memory", Modifier.weight(1f)) { selectedTab = "Memory" }
                    }

                    SectionTitle("TALK TO NISHTHA")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(reply, color = Color.White)
                            Text("Response: $responseSource", color = Teal, fontSize = 10.sp)

                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Type a message") },
                                maxLines = 4
                            )

                            Button(
                                onClick = { onListen { recognized -> message = recognized } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎙 Speak (on-device only)")
                            }

                            Text(speechStatus, color = Teal, fontSize = 12.sp)

                            Button(
                                onClick = {
                                    val input = message.trim()
                                    if (input.isBlank() || isSending) return@Button
                                    isSending = true
                                    onSend(input) { text, source ->
                                        reply = text
                                        responseSource = source
                                        isSending = false
                                        onSpeak(text)
                                    }
                                    message = ""
                                },
                                enabled = !isSending && conversationHistoryReady,
modifier = Modifier.fillMaxWidth()
) {
    Text(
        when {
            !conversationHistoryReady -> "Loading history..."
            isSending -> "Processing locally..."
            else -> "Send & Speak"
        }
    )
                            }
                        }
                    }
                }

                "Reminders" -> ReminderScreen(onSpeak = onSpeak)

                "Avatar" -> {
                    SectionTitle("AVATAR STUDIO")
                    Text("Choose a model", color = Color.White, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..5).forEach { number ->
                            FilterChip(
                                selected = selectedModel == number,
                                onClick = { selectedModel = number },
                                label = { Text("Model $number", fontSize = 10.sp) }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(CardColor, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("N$selectedModel", fontSize = 76.sp, color = Purple)
                    }
                    SectionTitle("CAMERA VIEW")
                    listOf("Close-up", "Half body", "Full body").forEach { view ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedView == view,
                                onClick = { selectedView = view }
                            )
                            Text(view, color = Color.White)
                        }
                    }
                    SectionTitle("OUTFIT")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Casual", "Traditional", "Smart", "Comfy").forEach { outfit ->
                            FilterChip(
                                selected = selectedOutfit == outfit,
                                onClick = { selectedOutfit = outfit },
                                label = { Text(outfit, fontSize = 10.sp) }
                            )
                        }
                    }
                    Text(
                        "Selected: Model $selectedModel • $selectedView • $selectedOutfit",
                        color = Teal
                    )
                    Text(
                        "Avatar controls are UI previews only. Real rigged 3D model and renderer are not connected yet.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }

                "Memory" -> MemoryScreen()

                "Settings" -> {
                    SectionTitle("SETTINGS")
                    InfoCard(
                        "Voice input",
                        "Uses Android's on-device speech recognizer only when the device reports it is available. There is no cloud voice fallback."
                    )
                    InfoCard(
                        "AI routing",
                        "Local commands are connected through AIRequestRouter. A local language model is not installed yet, so unsupported questions receive an honest offline fallback."
                    )
                    InfoCard(
                        "Online privacy gate",
                        "No online AI provider is configured. No chat text or memory is sent to an external AI service."
                    )
                    InfoCard(
                        "Encrypted memory",
                        "MemoryStore encrypts saved notes with AES-GCM and keeps the key in Android Keystore. Data stays in app-local storage."
                    )
                    InfoCard(
                        "Wake word & phone controls",
                        "Always-listening wake word, phone automation and external app integrations are not implemented."
                    )
                }
            }

            Text(
                "NISHTHA • PERSONAL ASSISTANT",
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun MemoryScreen() {
    val context = LocalContext.current
    val memoryStore = remember(context) { MemoryStore(context) }

    var draft by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(memoryStore.getAll()) }
    var status by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    fun refreshNotes() {
        notes = memoryStore.getAll()
    }

    SectionTitle("MY MEMORY")
    Text(
        "Save useful notes for NISHTHA to keep securely on this device.",
        color = Color.LightGray,
        fontSize = 13.sp
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should NISHTHA remember?") },
                placeholder = { Text("Example: My exam starts on Monday") },
                minLines = 2,
                maxLines = 4
            )
            Button(
                onClick = {
                    if (draft.isBlank()) {
                        status = "Please type a note first."
                    } else if (memoryStore.add(draft)) {
                        draft = ""
                        refreshNotes()
                        status = "Encrypted memory saved on this device."
                    } else {
                        status = memoryStore.lastError ?: "Secure memory save failed."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Save Memory")
            }
            if (status.isNotBlank()) Text(status, color = Teal, fontSize = 12.sp)
            memoryStore.lastError?.let {
                Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "SAVED MEMORIES (${notes.size})",
            color = Teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        TextButton(
            onClick = { showClearConfirmation = true },
            enabled = notes.isNotEmpty()
        ) { Text("Clear all") }
    }

    if (notes.isEmpty()) {
        InfoCard("No memories yet", "Write a note above and tap Save Memory.")
    } else {
        notes.forEach { note ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(note, color = Color.White, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                memoryStore.delete(note)
                                refreshNotes()
                                status = memoryStore.lastError ?: "Memory deleted."
                            }
                        ) { Text("🗑 Delete") }
                    }
                }
            }
        }
    }

    InfoCard(
        "Encryption",
        "Notes are encrypted with AES-GCM. The encryption key is held by Android Keystore. Clearing app data or uninstalling may remove the stored memories."
    )

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear all memories?") },
            text = { Text("This will permanently remove every saved note from this app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryStore.clearAll()
                        refreshNotes()
                        status = memoryStore.lastError ?: "All memories cleared."
                        showClearConfirmation = false
                    }
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        color = Teal,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ActionCard(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(95.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 23.sp)
            Text(title, color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun InfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}
