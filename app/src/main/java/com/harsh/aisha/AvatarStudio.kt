
package com.harsh.aisha

import android.content.Context
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.filament.Filament
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer

private enum class NishthaAvatarMode(
    val title: String,
    val assetPath: String,
    val description: String
) {
    HUMAN(
        "Human",
        "avatars/human.glb",
        "Realistic female digital human"
    ),
    HOLOGRAM(
        "Holographic",
        "avatars/holographic.glb",
        "Female holographic digital avatar"
    )
}

private object FilamentBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (!initialized) {
            Filament.init()
            initialized = true
        }
    }
}

@Composable
internal fun AvatarStudioScreen() {
    var mode by remember {
        mutableStateOf(NishthaAvatarMode.HUMAN)
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val modelAvailable = remember(context, mode) {
        runCatching {
            context.assets.open(mode.assetPath).use { }
            true
        }.getOrDefault(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("NISHTHA AVATAR STUDIO")

        Text(
            "Choose your avatar",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NishthaAvatarMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { mode = option },
                    label = { Text(option.title) }
                )
            }
        }

        Text(
            mode.description,
            color = Color(0xFF55E4D0),
            style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    if (mode == NishthaAvatarMode.HOLOGRAM)
                        Color(0xFF061C2D)
                    else
                        Color(0xFF171426),
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (modelAvailable) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        AvatarRenderView(viewContext).apply {
                            setAvatar(mode.assetPath)
                        }
                    },
                    update = { view ->
                        view.setAvatar(mode.assetPath)
                    }
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        if (mode == NishthaAvatarMode.HUMAN) "3D HUMAN"
                        else "3D HOLOGRAM",
                        color = if (mode == NishthaAvatarMode.HOLOGRAM)
                            Color(0xFF55E4FF)
                        else
                            Color(0xFFBDA8FF),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "Model asset is not installed yet.",
                        color = Color.White
                    )

                    Text(
                        "Required file: ${mode.assetPath}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        SectionTitle("HUMAN AVATAR CUSTOMIZATION")

        Text(
            "Customizable hair will be enabled after the human GLB " +
                "includes compatible hair variants.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        SectionTitle("HOLOGRAPHIC APPEARANCE")

        Text(
            "The holographic GLB needs its own translucent materials, " +
                "glow and holographic effects. A simple blue filter is not " +
                "being treated as a finished hologram.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        InfoCard(
            "3D engine",
            "Filament GLB renderer. Models are loaded from local app assets; " +
                "no model is downloaded or uploaded during normal viewing."
        )
    }
}

private class AvatarRenderView(
    context: Context
) : TextureView(context) {

    private var modelViewer: ModelViewer? = null
    private var activePath: String? = null
    private var frameRunning = false

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val viewer = modelViewer ?: return

            viewer.render(frameTimeNanos)

            if (frameRunning) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    init {
        isOpaque = false
    }

    fun setAvatar(path: String) {
        if (activePath == path) return

        activePath = path

        val viewer = modelViewer ?: return
        loadModel(viewer, path)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { initializeViewer() }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (width > 0 && height > 0) {
            initializeViewer()
        }
    }

    private fun initializeViewer() {
        if (!isAttachedToWindow ||
            width <= 0 ||
            height <= 0 ||
            modelViewer != null
        ) return

        try {
            FilamentBootstrap.initialize()

            val helper = UiHelper(
                UiHelper.ContextErrorPolicy.DONT_CHECK
            ).apply {
                isOpaque = false
            }

            val viewer = ModelViewer(
                this,
                uiHelper = helper
            )

            modelViewer = viewer
            setOnTouchListener(viewer)

            activePath?.let {
                loadModel(viewer, it)
            }

            frameRunning = true
            choreographer.postFrameCallback(frameCallback)

        } catch (error: Exception) {
            modelViewer = null
            android.util.Log.e(
                "NISHTHA_AVATAR",
                "Renderer initialization failed",
                error
            )
        }
    }

    private fun loadModel(
        viewer: ModelViewer,
        path: String
    ) {
        try {
            val bytes = context.assets.open(path).use {
                it.readBytes()
            }

            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.flip()

            viewer.loadModelGlb(buffer)
            viewer.autoPlayAnimations = true

        } catch (error: Exception) {
            android.util.Log.e(
                "NISHTHA_AVATAR",
                "Could not load model: $path",
                error
            )
        }
    }

    override fun onDetachedFromWindow() {
        frameRunning = false
        choreographer.removeFrameCallback(frameCallback)

        // ModelViewer registers its own detach listener and
        // destroys its Filament resources when this view detaches.
        modelViewer = null

        super.onDetachedFromWindow()
    }
}
