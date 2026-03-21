package com.ai.assistance.mmd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

class MmdGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = MmdPreviewRenderer(context.applicationContext)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(true)
        setBackgroundColor(Color.TRANSPARENT)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        requestHighRefreshRateIfSupported()
    }

    fun setModelPath(path: String) {
        queueEvent {
            renderer.setModelPath(path)
        }
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        queueEvent {
            renderer.setAnimationState(animationName, isLooping)
        }
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        queueEvent {
            renderer.setModelRotation(rotationX, rotationY, rotationZ)
        }
    }

    fun setCameraDistanceScale(scale: Float) {
        queueEvent {
            renderer.setCameraDistanceScale(scale)
        }
    }

    fun setCameraTargetHeight(height: Float) {
        queueEvent {
            renderer.setCameraTargetHeight(height)
        }
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        renderer.setOnErrorListener(listener)
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRateIfSupported()
    }

    private fun requestHighRefreshRateIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        val surface = holder.surface ?: return
        if (!surface.isValid) {
            return
        }

        try {
            val setFrameRateMethod =
                surface.javaClass.getMethod(
                    "setFrameRate",
                    Float::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                )
            setFrameRateMethod.invoke(surface, 120f, 0)
        } catch (_: Throwable) {
        }
    }
}

private data class LoadedTextureSlot(
    val textureId: Int,
    val flags: Int
)

private class MmdPreviewRenderer(
    private val appContext: Context
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "MmdGlRenderer"

        private const val STRIDE_FLOATS = 10
        private const val MATERIAL_SEGMENT_STRIDE = 7
        private const val TEXTURE_FLAG_HAS_ALPHA = 1
        private const val BUILTIN_ASSET_PREFIX = "@mmd_builtin/"

        private const val MAIN_HANDLE_COUNT = 24
        private const val EDGE_HANDLE_COUNT = 7

        private object MainHandle {
            const val PROGRAM = 0
            const val POSITION = 1
            const val NORMAL = 2
            const val TEX_COORD = 3
            const val ADD_UV1 = 4
            const val MVP_MATRIX = 5
            const val MODEL_VIEW_MATRIX = 6
            const val BASE_TEXTURE = 7
            const val SPHERE_TEXTURE = 8
            const val TOON_TEXTURE = 9
            const val USE_BASE_TEXTURE = 10
            const val USE_SPHERE_TEXTURE = 11
            const val USE_TOON_TEXTURE = 12
            const val SPHERE_MODE = 13
            const val DIFFUSE_COLOR = 14
            const val AMBIENT_COLOR = 15
            const val SPECULAR_COLOR = 16
            const val SPECULAR_POWER = 17
            const val TEXTURE_MUL_FACTOR = 18
            const val TEXTURE_ADD_FACTOR = 19
            const val SPHERE_MUL_FACTOR = 20
            const val SPHERE_ADD_FACTOR = 21
            const val TOON_MUL_FACTOR = 22
            const val TOON_ADD_FACTOR = 23
        }

        private object EdgeHandle {
            const val PROGRAM = 0
            const val POSITION = 1
            const val NORMAL = 2
            const val MVP_MATRIX = 3
            const val EDGE_COLOR = 4
            const val EDGE_SIZE = 5
            const val EDGE_SCALE = 6
        }
    }

    private var requestedModelPath: String? = null
    private var requestedAnimationName: String? = null
    private var requestedAnimationLooping: Boolean = false

    private var currentModelPath: String? = null
    private var activeMotionPath: String? = null
    private var activeAnimationLooping: Boolean = false

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount: Int = 0

    private var activeRotationX: Float = 0f
    private var activeRotationY: Float = 0f
    private var activeRotationZ: Float = 0f

    private var materialSegmentData: IntArray = IntArray(0)
    private var materialSegmentBuffer: IntBuffer? = null
    private var textureIdsBySlot: IntArray = IntArray(0)
    private var textureIdsBuffer: IntBuffer? = null
    private var textureFlagsBySlot: IntArray = IntArray(0)
    private var textureFlagsBuffer: IntBuffer? = null

    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f
    private var fitScale = 1f

    private var aspectRatio = 1f
    private var cameraDistance = 3f
    private var cameraDistanceScale = 1f
    private var cameraTargetHeight = 0f
    private var nearClip = 0.1f
    private var farClip = 100f

    private var mainProgramHandles: IntArray? = null
    private var edgeProgramHandles: IntArray? = null
    private var lastNativeRenderError: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var onErrorListener: ((String) -> Unit)? = null

    private val mainVertexShader = """
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelViewMatrix;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        attribute vec2 aAddUv1;
        varying vec3 vViewNormal;
        varying vec3 vViewPosition;
        varying vec2 vTexCoord;
        varying vec2 vAddUv1;
        void main() {
            vec4 viewPosition = uModelViewMatrix * vec4(aPosition, 1.0);
            vViewPosition = viewPosition.xyz;
            vViewNormal = normalize((uModelViewMatrix * vec4(aNormal, 0.0)).xyz);
            vTexCoord = aTexCoord;
            vAddUv1 = aAddUv1;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    private val mainFragmentShader = """
        precision mediump float;
        varying vec3 vViewNormal;
        varying vec3 vViewPosition;
        varying vec2 vTexCoord;
        varying vec2 vAddUv1;
        uniform sampler2D uBaseTexture;
        uniform sampler2D uSphereTexture;
        uniform sampler2D uToonTexture;
        uniform float uUseBaseTexture;
        uniform float uUseSphereTexture;
        uniform float uUseToonTexture;
        uniform float uSphereMode;
        uniform vec4 uDiffuseColor;
        uniform vec3 uAmbientColor;
        uniform vec3 uSpecularColor;
        uniform float uSpecularPower;
        uniform vec4 uTextureMulFactor;
        uniform vec4 uTextureAddFactor;
        uniform vec4 uSphereMulFactor;
        uniform vec4 uSphereAddFactor;
        uniform vec4 uToonMulFactor;
        uniform vec4 uToonAddFactor;

        vec2 flipUv(vec2 uv) {
            return vec2(uv.x, 1.0 - uv.y);
        }

        vec3 computeTexMulFactor(vec3 texColor, vec4 factor) {
            vec3 weightedColor = texColor * factor.rgb;
            return mix(vec3(1.0), weightedColor, factor.a);
        }

        vec3 computeTexAddFactor(vec3 texColor, vec4 factor) {
            vec3 adjusted = texColor + (texColor - vec3(1.0)) * factor.a;
            adjusted = clamp(adjusted, vec3(0.0), vec3(1.0)) + factor.rgb;
            return clamp(adjusted, vec3(0.0), vec3(1.0));
        }

        void main() {
            vec3 normal = normalize(vViewNormal);
            vec3 viewDir = normalize(-vViewPosition);
            vec3 keyLightDir = normalize(vec3(0.35, 0.65, 1.0));
            vec3 fillLightDir = normalize(vec3(-0.45, 0.25, 0.9));
            float key = dot(normal, keyLightDir);
            float fill = dot(normal, fillLightDir);
            float toonLight = clamp(max(key, fill) + 0.5, 0.0, 1.0);

            vec3 color = clamp(uDiffuseColor.rgb + uAmbientColor, vec3(0.0), vec3(1.0));
            float alpha = uDiffuseColor.a;
            if (uUseBaseTexture > 0.5) {
                vec4 baseSample = texture2D(uBaseTexture, flipUv(vTexCoord));
                vec3 baseColor = computeTexMulFactor(baseSample.rgb, uTextureMulFactor);
                baseColor = computeTexAddFactor(baseColor, uTextureAddFactor);
                color *= baseColor;
                alpha *= baseSample.a;
            }
            if (alpha <= 0.0) {
                discard;
            }

            vec2 sphereUv = vec2(normal.x * 0.5 + 0.5, 0.5 - normal.y * 0.5);
            if (uSphereMode > 2.5) {
                sphereUv = flipUv(vAddUv1);
            }
            if (uUseSphereTexture > 0.5) {
                vec3 sphereColor = texture2D(uSphereTexture, sphereUv).rgb;
                sphereColor = computeTexMulFactor(sphereColor, uSphereMulFactor);
                sphereColor = computeTexAddFactor(sphereColor, uSphereAddFactor);
                if (uSphereMode > 0.5 && uSphereMode < 1.5) {
                    color *= sphereColor;
                } else if (uSphereMode >= 1.5) {
                    color += sphereColor;
                }
            }

            if (uUseToonTexture > 0.5) {
                vec3 toonColor = texture2D(uToonTexture, vec2(0.0, 1.0 - toonLight)).rgb;
                toonColor = computeTexMulFactor(toonColor, uToonMulFactor);
                toonColor = computeTexAddFactor(toonColor, uToonAddFactor);
                color *= toonColor;
            }

            if (uSpecularPower > 0.0) {
                vec3 halfVec = normalize(viewDir + keyLightDir);
                float specular = pow(max(dot(halfVec, normal), 0.0), max(uSpecularPower, 1.0));
                color += uSpecularColor * specular;
            }

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
        }
    """

    private val edgeVertexShader = """
        uniform mat4 uMvpMatrix;
        uniform float uEdgeSize;
        uniform float uEdgeScale;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        void main() {
            vec3 expandedPosition = aPosition + aNormal * max(uEdgeSize, 0.0) * uEdgeScale;
            gl_Position = uMvpMatrix * vec4(expandedPosition, 1.0);
        }
    """

    private val edgeFragmentShader = """
        precision mediump float;
        uniform vec4 uEdgeColor;
        void main() {
            gl_FragColor = uEdgeColor;
        }
    """

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorListener = listener
    }

    fun setModelPath(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty()) return

        requestedModelPath = normalizedPath
        if (normalizedPath == currentModelPath && vertexBuffer != null && vertexCount > 0) {
            return
        }

        if (loadPreviewAssets(normalizedPath)) {
            currentModelPath = normalizedPath
            syncMotionPathWithRequest(forceRestartClock = true)
        } else {
            currentModelPath = null
            activeMotionPath = null
        }
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        val normalizedName = animationName?.trim()?.takeIf { it.isNotEmpty() }
        if (requestedAnimationName == normalizedName && requestedAnimationLooping == isLooping) {
            return
        }

        requestedAnimationName = normalizedName
        requestedAnimationLooping = isLooping
        syncMotionPathWithRequest(forceRestartClock = true)
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        activeRotationX = rotationX
        activeRotationY = rotationY
        activeRotationZ = rotationZ
    }

    fun setCameraDistanceScale(scale: Float) {
        cameraDistanceScale = scale.coerceIn(0.02f, 12.0f)
    }

    fun setCameraTargetHeight(height: Float) {
        cameraTargetHeight = height.coerceIn(-2.0f, 2.0f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        currentModelPath = null
        activeMotionPath = null
        activeAnimationLooping = requestedAnimationLooping
        lastNativeRenderError = null

        clearMesh()
        clearTextureSet()
        clearPrograms()

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        mainProgramHandles = createMainProgramHandles()
        edgeProgramHandles = createEdgeProgramHandles()
        if (mainProgramHandles == null || edgeProgramHandles == null) {
            dispatchError("Failed to create GL programs for MMD renderer.")
            return
        }

        requestedModelPath?.takeIf { it.isNotBlank() }?.let { modelPath ->
            if (loadPreviewAssets(modelPath)) {
                currentModelPath = modelPath
                syncMotionPathWithRequest(forceRestartClock = true)
            } else {
                currentModelPath = null
                activeMotionPath = null
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val safeHeight = if (height <= 0) 1 else height
        aspectRatio = width.toFloat() / safeHeight.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val effectiveCameraDistance = cameraDistance * cameraDistanceScale
        val effectiveNearClip = (nearClip * cameraDistanceScale).coerceIn(0.005f, 0.1f)

        val renderSuccess = MmdNative.nativeRenderPreviewFrame(
            pathModel = currentModelPath,
            pathMotion = activeMotionPath,
            isLooping = activeAnimationLooping,
            restart = false,
            rotationX = activeRotationX,
            rotationY = activeRotationY,
            rotationZ = activeRotationZ,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            fitScale = fitScale,
            cameraDistance = effectiveCameraDistance,
            cameraTargetHeight = cameraTargetHeight,
            aspectRatio = aspectRatio,
            nearClip = effectiveNearClip,
            farClip = farClip,
            vertexBuffer = vertexBuffer,
            vertexCount = vertexCount,
            materialSegments = materialSegmentBuffer,
            textureIdsBySlot = textureIdsBuffer,
            textureFlagsBySlot = textureFlagsBuffer,
            mainProgramHandles = mainProgramHandles,
            edgeProgramHandles = edgeProgramHandles
        )

        if (!renderSuccess) {
            val latestError = MmdInspector.getLastError().ifBlank {
                "Failed to render animated MMD preview frame."
            }
            if (latestError != lastNativeRenderError) {
                dispatchError(latestError)
                lastNativeRenderError = latestError
            }
        } else {
            lastNativeRenderError = null
        }
    }

    private fun syncMotionPathWithRequest(forceRestartClock: Boolean) {
        val resolvedMotionPath =
            if (requestedAnimationName.isNullOrBlank() || currentModelPath.isNullOrBlank()) {
                null
            } else {
                File(File(currentModelPath!!).parentFile, requestedAnimationName!!).absolutePath
            }

        val motionChanged = resolvedMotionPath != activeMotionPath
        val loopingChanged = requestedAnimationLooping != activeAnimationLooping
        activeMotionPath = resolvedMotionPath
        activeAnimationLooping = requestedAnimationLooping

        if (forceRestartClock || motionChanged || loopingChanged) {
            Log.d(
                TAG,
                "Updated animation binding. motionChanged=$motionChanged loopingChanged=$loopingChanged motionPath=$resolvedMotionPath"
            )
        }
    }

    private fun loadPreviewAssets(modelPath: String): Boolean {
        if (!MmdInspector.isAvailable()) {
            dispatchError(MmdInspector.unavailableReason().ifBlank { "MMD backend is unavailable." })
            clearMesh()
            clearTextureSet()
            return false
        }

        val rawMesh = MmdNative.nativeBuildPreviewMesh(modelPath)
        if (rawMesh == null) {
            dispatchError(MmdInspector.getLastError().ifBlank { "Failed to build preview mesh from model." })
            clearMesh()
            clearTextureSet()
            return false
        }

        if (rawMesh.isEmpty() || rawMesh.size % STRIDE_FLOATS != 0) {
            dispatchError("Invalid preview mesh data returned by native layer.")
            clearMesh()
            clearTextureSet()
            return false
        }

        val rawMaterialSegments = MmdNative.nativeBuildPreviewMaterialSegments(modelPath)
        if (
            rawMaterialSegments == null ||
            rawMaterialSegments.isEmpty() ||
            rawMaterialSegments.size % MATERIAL_SEGMENT_STRIDE != 0
        ) {
            dispatchError("Invalid preview material segment data returned by native layer.")
            clearMesh()
            clearTextureSet()
            return false
        }

        val floatBuffer = ByteBuffer
            .allocateDirect(rawMesh.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(rawMesh)
                position(0)
            }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (index in rawMesh.indices step STRIDE_FLOATS) {
            val x = rawMesh[index]
            val y = rawMesh[index + 1]
            val z = rawMesh[index + 2]

            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }

        centerX = (minX + maxX) * 0.5f
        centerY = (minY + maxY) * 0.5f
        centerZ = (minZ + maxZ) * 0.5f

        val extentX = maxX - minX
        val extentY = maxY - minY
        val extentZ = maxZ - minZ
        val maxExtent = max(extentX, max(extentY, extentZ)).coerceAtLeast(0.0001f)

        fitScale = 1f
        val radius = (maxExtent * 0.5f).coerceAtLeast(0.1f)
        cameraDistance = max(1.8f, radius * 3.0f + 0.7f)
        nearClip = max(0.01f, radius / 100f)
        farClip = max(120f, cameraDistance + radius * 40f)

        clearTextureSet()
        val texturePaths = MmdNative.nativeReadPreviewTexturePaths(modelPath).orEmpty()
        val loadedTextureSlots = loadTextureSlots(texturePaths)
        textureIdsBySlot = IntArray(loadedTextureSlots.size) { loadedTextureSlots[it].textureId }
        textureFlagsBySlot = IntArray(loadedTextureSlots.size) { loadedTextureSlots[it].flags }
        textureIdsBuffer = textureIdsBySlot.toDirectIntBuffer()
        textureFlagsBuffer = textureFlagsBySlot.toDirectIntBuffer()

        vertexBuffer = floatBuffer
        vertexCount = rawMesh.size / STRIDE_FLOATS
        materialSegmentData = rawMaterialSegments
        materialSegmentBuffer = materialSegmentData.toDirectIntBuffer()

        val loadedTextures = textureIdsBySlot.count { it != 0 }
        val alphaTextures = textureFlagsBySlot.count { (it and TEXTURE_FLAG_HAS_ALPHA) != 0 }
        Log.i(
            TAG,
            "Loaded MMD preview assets. vertices=$vertexCount materials=${materialSegmentData.size / MATERIAL_SEGMENT_STRIDE} textures=$loadedTextures/${textureIdsBySlot.size} alphaTextures=$alphaTextures modelPath=$modelPath"
        )
        return true
    }

    private fun loadTextureSlots(texturePaths: Array<out String>): Array<LoadedTextureSlot> {
        if (texturePaths.isEmpty()) {
            return emptyArray()
        }
        return Array(texturePaths.size) { index ->
            loadTextureFromPath(texturePaths[index])
        }
    }

    private fun loadTextureFromPath(texturePath: String?): LoadedTextureSlot {
        val normalizedPath = texturePath?.trim()?.takeIf { it.isNotEmpty() } ?: return LoadedTextureSlot(0, 0)
        return if (normalizedPath.startsWith(BUILTIN_ASSET_PREFIX)) {
            loadTextureFromAsset(normalizedPath.removePrefix(BUILTIN_ASSET_PREFIX))
        } else {
            loadTextureFromFile(normalizedPath)
        }
    }

    private fun loadTextureFromAsset(assetPath: String): LoadedTextureSlot {
        return try {
            appContext.assets.open(assetPath).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null) {
                    Log.w(TAG, "Failed to decode builtin texture asset: $assetPath")
                    LoadedTextureSlot(0, 0)
                } else {
                    uploadBitmapTexture(bitmap)
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to open builtin texture asset: $assetPath", error)
            LoadedTextureSlot(0, 0)
        }
    }

    private fun loadTextureFromFile(texturePath: String): LoadedTextureSlot {
        val textureFile = File(texturePath)
        if (!textureFile.exists() || !textureFile.isFile) {
            Log.w(TAG, "Texture file does not exist: $texturePath")
            return LoadedTextureSlot(0, 0)
        }

        BitmapFactory.decodeFile(texturePath)?.let { bitmap ->
            return uploadBitmapTexture(bitmap)
        }

        val size = MmdNative.nativeDecodeImageSize(texturePath)
        val rgbaBytes = MmdNative.nativeDecodeImageRgba(texturePath)
        if (size == null || size.size < 2 || rgbaBytes == null) {
            Log.w(TAG, "Failed to decode texture bitmap: $texturePath")
            return LoadedTextureSlot(0, 0)
        }

        val width = size[0]
        val height = size[1]
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid native decoded image size: ${width}x$height, file=$texturePath")
            return LoadedTextureSlot(0, 0)
        }

        val expectedSizeLong = width.toLong() * height.toLong() * 4L
        if (expectedSizeLong <= 0L || expectedSizeLong > Int.MAX_VALUE) {
            Log.w(TAG, "Native decoded image size overflow: file=$texturePath")
            return LoadedTextureSlot(0, 0)
        }

        val expectedSize = expectedSizeLong.toInt()
        if (rgbaBytes.size < expectedSize) {
            Log.w(TAG, "Native decoded image buffer too small: got=${rgbaBytes.size} expected=$expectedSize file=$texturePath")
            return LoadedTextureSlot(0, 0)
        }

        val textureId = allocateTextureId(texturePath) ?: return LoadedTextureSlot(0, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        configureTextureParameters()
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        val rgbaBuffer = ByteBuffer.wrap(rgbaBytes, 0, expectedSize)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            rgbaBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return LoadedTextureSlot(textureId, if (detectAlpha(rgbaBytes, expectedSize)) TEXTURE_FLAG_HAS_ALPHA else 0)
    }

    private fun uploadBitmapTexture(bitmap: Bitmap): LoadedTextureSlot {
        val textureId = allocateTextureId("bitmap") ?: run {
            bitmap.recycle()
            return LoadedTextureSlot(0, 0)
        }

        val hasAlpha = bitmap.hasAlpha()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        configureTextureParameters()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return LoadedTextureSlot(textureId, if (hasAlpha) TEXTURE_FLAG_HAS_ALPHA else 0)
    }

    private fun allocateTextureId(debugLabel: String): Int? {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val generatedTextureId = textureIds[0]
        if (generatedTextureId == 0) {
            Log.w(TAG, "Failed to allocate OpenGL texture id for: $debugLabel")
            return null
        }
        return generatedTextureId
    }

    private fun configureTextureParameters() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun detectAlpha(rgbaBytes: ByteArray, byteCount: Int): Boolean {
        var cursor = 3
        while (cursor < byteCount) {
            if (rgbaBytes[cursor].toInt() and 0xFF != 0xFF) {
                return true
            }
            cursor += 4
        }
        return false
    }

    private fun IntArray.toDirectIntBuffer(): IntBuffer {
        return ByteBuffer
            .allocateDirect(size * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply {
                put(this@toDirectIntBuffer)
                position(0)
            }
    }

    private fun clearMesh() {
        vertexBuffer = null
        vertexCount = 0
        materialSegmentData = IntArray(0)
        materialSegmentBuffer = null
    }

    private fun clearTextureSet() {
        if (textureIdsBySlot.isNotEmpty()) {
            val textures = textureIdsBySlot.filter { it != 0 }.toIntArray()
            if (textures.isNotEmpty()) {
                GLES20.glDeleteTextures(textures.size, textures, 0)
            }
        }
        textureIdsBySlot = IntArray(0)
        textureIdsBuffer = null
        textureFlagsBySlot = IntArray(0)
        textureFlagsBuffer = null
    }

    private fun clearPrograms() {
        mainProgramHandles?.getOrNull(MainHandle.PROGRAM)?.takeIf { it != 0 }?.let {
            GLES20.glDeleteProgram(it)
        }
        edgeProgramHandles?.getOrNull(EdgeHandle.PROGRAM)?.takeIf { it != 0 }?.let {
            GLES20.glDeleteProgram(it)
        }
        mainProgramHandles = null
        edgeProgramHandles = null
    }

    private fun dispatchError(message: String) {
        if (message.isBlank()) return
        Log.e(TAG, message)
        mainHandler.post {
            onErrorListener?.invoke(message)
        }
    }

    private fun createMainProgramHandles(): IntArray? {
        val program = createProgram(mainVertexShader, mainFragmentShader)
        if (program == 0) {
            return null
        }

        val handles = IntArray(MAIN_HANDLE_COUNT)
        handles[MainHandle.PROGRAM] = program
        handles[MainHandle.POSITION] = GLES20.glGetAttribLocation(program, "aPosition")
        handles[MainHandle.NORMAL] = GLES20.glGetAttribLocation(program, "aNormal")
        handles[MainHandle.TEX_COORD] = GLES20.glGetAttribLocation(program, "aTexCoord")
        handles[MainHandle.ADD_UV1] = GLES20.glGetAttribLocation(program, "aAddUv1")
        handles[MainHandle.MVP_MATRIX] = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        handles[MainHandle.MODEL_VIEW_MATRIX] = GLES20.glGetUniformLocation(program, "uModelViewMatrix")
        handles[MainHandle.BASE_TEXTURE] = GLES20.glGetUniformLocation(program, "uBaseTexture")
        handles[MainHandle.SPHERE_TEXTURE] = GLES20.glGetUniformLocation(program, "uSphereTexture")
        handles[MainHandle.TOON_TEXTURE] = GLES20.glGetUniformLocation(program, "uToonTexture")
        handles[MainHandle.USE_BASE_TEXTURE] = GLES20.glGetUniformLocation(program, "uUseBaseTexture")
        handles[MainHandle.USE_SPHERE_TEXTURE] = GLES20.glGetUniformLocation(program, "uUseSphereTexture")
        handles[MainHandle.USE_TOON_TEXTURE] = GLES20.glGetUniformLocation(program, "uUseToonTexture")
        handles[MainHandle.SPHERE_MODE] = GLES20.glGetUniformLocation(program, "uSphereMode")
        handles[MainHandle.DIFFUSE_COLOR] = GLES20.glGetUniformLocation(program, "uDiffuseColor")
        handles[MainHandle.AMBIENT_COLOR] = GLES20.glGetUniformLocation(program, "uAmbientColor")
        handles[MainHandle.SPECULAR_COLOR] = GLES20.glGetUniformLocation(program, "uSpecularColor")
        handles[MainHandle.SPECULAR_POWER] = GLES20.glGetUniformLocation(program, "uSpecularPower")
        handles[MainHandle.TEXTURE_MUL_FACTOR] = GLES20.glGetUniformLocation(program, "uTextureMulFactor")
        handles[MainHandle.TEXTURE_ADD_FACTOR] = GLES20.glGetUniformLocation(program, "uTextureAddFactor")
        handles[MainHandle.SPHERE_MUL_FACTOR] = GLES20.glGetUniformLocation(program, "uSphereMulFactor")
        handles[MainHandle.SPHERE_ADD_FACTOR] = GLES20.glGetUniformLocation(program, "uSphereAddFactor")
        handles[MainHandle.TOON_MUL_FACTOR] = GLES20.glGetUniformLocation(program, "uToonMulFactor")
        handles[MainHandle.TOON_ADD_FACTOR] = GLES20.glGetUniformLocation(program, "uToonAddFactor")

        return if (handles.all { it >= 0 || it == program }) {
            handles
        } else {
            Log.e(TAG, "Main GL program missing required handles: ${handles.joinToString()}")
            GLES20.glDeleteProgram(program)
            null
        }
    }

    private fun createEdgeProgramHandles(): IntArray? {
        val program = createProgram(edgeVertexShader, edgeFragmentShader)
        if (program == 0) {
            return null
        }

        val handles = IntArray(EDGE_HANDLE_COUNT)
        handles[EdgeHandle.PROGRAM] = program
        handles[EdgeHandle.POSITION] = GLES20.glGetAttribLocation(program, "aPosition")
        handles[EdgeHandle.NORMAL] = GLES20.glGetAttribLocation(program, "aNormal")
        handles[EdgeHandle.MVP_MATRIX] = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        handles[EdgeHandle.EDGE_COLOR] = GLES20.glGetUniformLocation(program, "uEdgeColor")
        handles[EdgeHandle.EDGE_SIZE] = GLES20.glGetUniformLocation(program, "uEdgeSize")
        handles[EdgeHandle.EDGE_SCALE] = GLES20.glGetUniformLocation(program, "uEdgeScale")

        return if (handles.all { it >= 0 || it == program }) {
            handles
        } else {
            Log.e(TAG, "Edge GL program missing required handles: ${handles.joinToString()}")
            GLES20.glDeleteProgram(program)
            null
        }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val shaderProgram = GLES20.glCreateProgram()
        if (shaderProgram == 0) {
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
            GLES20.glDeleteProgram(shaderProgram)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return shaderProgram
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            return 0
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}
