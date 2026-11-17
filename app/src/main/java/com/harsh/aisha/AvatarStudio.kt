
package com.harsh.aisha

import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import android.content.Context
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.filament.Filament
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer


private enum class NishthaAvatarMode(
    val title: String,
    val description: String
) {
    HUMAN(
        "Human",
        "Realistic female digital human"
    ),
    HOLOGRAM(
        "Holographic",
        "Futuristic holographic projection"
    )
}

private enum class AvatarCameraAngle(
    val title: String,
    val targetY: Double,
    val eyeZ: Double
) {
    FULL_BODY("Full Body", 0.0, -0.0),
    BODY_ZOOM("Body Zoom", 0.25, -1.0),
    UPPER_BODY("Upper Body", 0.64, -2.0)
}

private object FilamentBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (!initialized) {
            Filament.init()
            Gltfio.init()
            Utils.init()

            initialized = true
        }
    }
}


@Composable
internal fun AvatarStudioScreen() {
    
var mode by remember {
    mutableStateOf(NishthaAvatarMode.HUMAN)
}

var cameraAngle by remember {
    mutableStateOf(AvatarCameraAngle.FULL_BODY)
}


    val context = androidx.compose.ui.platform.LocalContext.current

    val modelAvailable = remember(context) {
        runCatching {
            context.assets.open("avatars/human.glb").use { }
            true
        }.getOrDefault(false)
    }

    val transition = rememberInfiniteTransition(label = "hologram")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanSweep"
    )

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
        
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    AvatarCameraAngle.entries.forEach { angle ->
        FilterChip(
            selected = cameraAngle == angle,
            onClick = {
                cameraAngle = angle
            },
            label = {
                Text(angle.title)
            }
        )
    }
}


        Text(
            mode.description,
            color = if (mode == NishthaAvatarMode.HOLOGRAM)
                Color(0xFF55E4FF)
            else
                Color(0xFF55E4D0),
            style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    if (mode == NishthaAvatarMode.HOLOGRAM)
                        Color(0xFF041725)
                    else
                        Color(0xFF171426),
                    RoundedCornerShape(24.dp)
                )
                .drawWithContent {
                    drawContent()

                    if (mode == NishthaAvatarMode.HOLOGRAM) {
                        // Cyan holographic tint
                        drawRect(
                            color = Color(0xFF00CFFF).copy(alpha = 0.13f),
                            blendMode = BlendMode.Screen
                        )

                        // Horizontal scan lines
                        val spacing = 7.dp.toPx()
                        var y = 0f

                        while (y < size.height) {
                            drawLine(
                                color = Color(0xFF55EFFF).copy(alpha = 0.12f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            y += spacing
                        }

                        // Animated scanning beam
                        val beamY = size.height * sweep

                        drawLine(
                            color = Color(0xFF72F4FF).copy(alpha = 0.65f),
                            start = Offset(0f, beamY),
                            end = Offset(size.width, beamY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Projection frame
                        val inset = 14.dp.toPx()
                        val corner = 24.dp.toPx()

                        drawLine(
                            Color.Cyan.copy(alpha = 0.65f),
                            Offset(inset, inset),
                            Offset(inset + corner, inset),
                            2.dp.toPx()
                        )
                        drawLine(
                            Color.Cyan.copy(alpha = 0.65f),
                            Offset(inset, inset),
                            Offset(inset, inset + corner),
                            2.dp.toPx()
                        )
                        drawLine(
                            Color.Cyan.copy(alpha = 0.65f),
                            Offset(size.width - inset, inset),
                            Offset(size.width - inset - corner, inset),
                            2.dp.toPx()
                        )
                        drawLine(
                            Color.Cyan.copy(alpha = 0.65f),
                            Offset(size.width - inset, inset),
                            Offset(size.width - inset, inset + corner),
                            2.dp.toPx()
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (modelAvailable) {
                AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { viewContext ->
        AvatarRenderView(viewContext).apply {
            setAvatar("avatars/human.glb")
            setCameraAngle(cameraAngle)
        }
    },
    update = { view ->
        view.setAvatar("avatars/human.glb")
        view.setCameraAngle(cameraAngle)
    }
)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "3D HUMAN MODEL",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Model asset not found.",
                        color = Color.LightGray
                    )
                    Text(
                        "Expected: avatars/human.glb",
                        color = Color.LightGray
                    )
                }
            }
        }

        SectionTitle("HUMAN AVATAR CUSTOMIZATION")

        Text(
            "Hair customization will be added after compatible hair variants are prepared in the 3D model.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        SectionTitle("HOLOGRAPHIC MODE")

        Text(
            "Uses the same human model with a cyan projection overlay, animated scan lines and scanning beam. A separate holographic GLB can be added later.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        InfoCard(
            "3D engine",
            "Filament GLB renderer. The model is loaded locally from app assets."
        )
    }
}

private class AvatarRenderView(
    context: Context
) : TextureView(context) {

    private var activePath: String? = null
    private var currentCameraAngle =
    AvatarCameraAngle.FULL_BODY
    private var modelViewer: ModelViewer? = null
    private var activePath: String? = null
    private var frameRunning = false
    private var animationStartTimeNanos = 0L

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
        val viewer = modelViewer ?: return

        viewer.animator?.let { animator ->
            if (animator.animationCount > 0) {
                if (animationStartTimeNanos == 0L) {
                    animationStartTimeNanos = frameTimeNanos
                }

                val duration = animator.getAnimationDuration(0)

                val elapsed = (
                    (frameTimeNanos - animationStartTimeNanos) /
                        1_000_000_000.0
                    ).toFloat()

                val animationTime = if (duration > 0f) {
                    elapsed % duration
                } else {
                    elapsed
                }

                animator.applyAnimation(0, animationTime)
                animator.updateBoneMatrices()
            }
        }

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
        modelViewer?.let { loadModel(it, path) }
    }
fun setCameraAngle(angle: AvatarCameraAngle) {
    currentCameraAngle = angle
    applyCameraAngle()
}

private fun applyCameraAngle() {
    modelViewer?.camera?.lookAt(
        0.0,
        currentCameraAngle.targetY,
        currentCameraAngle.eyeZ,
        0.0,
        currentCameraAngle.targetY,
        -4.0,
        0.0,
        1.0,
        0.0
    )
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
    uiHelper = helper,
    manipulator = null
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
viewer.transformToUnitCube()
applyCameraAngle()
animationStartTimeNanos = 0L

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
        modelViewer = null
        super.onDetachedFromWindow()
    }
}
