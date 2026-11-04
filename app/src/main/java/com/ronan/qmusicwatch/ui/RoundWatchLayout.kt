package com.ronan.qmusicwatch.ui

import android.view.LayoutInflater
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.ronan.qmusicwatch.R
import com.ronan.qmusicwatch.MainActivity
import com.heytap.wearable.support.widget.HeySwitch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.content.ContextCompat
import java.util.Date
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Fit the complete item rectangle inside the circular display, including its corners. */
internal fun roundItemScale(width: Float, height: Float, centerY: Float, diameter: Float): Float {
    if (width <= 0 || height <= 0 || diameter <= 0) return 1f
    val radius = diameter / 2f - 3f
    val distance = abs(centerY - diameter / 2f)
    if (distance >= radius) return .3f
    // (w*s/2)^2 + (distance+h*s/2)^2 = radius^2
    val a = (width * width + height * height) / 4f
    val b = distance * height
    val c = distance * distance - radius * radius
    return ((-b + sqrt((b * b - 4f * a * c).coerceAtLeast(0f))) / (2f * a))
        .coerceIn(.3f, 1f)
}

internal fun roundItemScaleFromTop(width: Float, height: Float, top: Float, diameter: Float): Float {
    if (width <= 0 || height <= 0 || diameter <= 0) return 1f
    val radius = diameter / 2f - 3f
    val dy = top - diameter / 2f
    if (abs(dy) >= radius) return .3f
    val topLimit = 2f * sqrt((radius * radius - dy * dy).coerceAtLeast(0f)) / width
    val a = width * width / 4f + height * height
    val b = 2f * dy * height
    val c = dy * dy - radius * radius
    val bottomLimit = (-b + sqrt((b * b - 4f * a * c).coerceAtLeast(0f))) / (2f * a)
    return minOf(1f, topLimit, bottomLimit).coerceAtLeast(.3f)
}

private fun Modifier.roundListItem(
    state: LazyListState, index: Int, containerTop: () -> Float,
): Modifier = composed {
    if (!LocalWatchDimensions.current.isRound) return@composed this
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val diameter = with(density) { minOf(config.screenWidthDp, config.screenHeightDp).dp.toPx() }
    // Read scroll position only in the render layer. Changing item measurement
    // while scrolling creates a feedback loop between item heights and offsets.
    this.graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val scale = if (item == null) .3f else roundItemScale(
            size.width, size.height,
            containerTop() + item.offset - info.viewportStartOffset + item.size / 2f, diameter,
        )
        scaleX = scale
        scaleY = scale
    }
}

private class CrownViewport { var bounds: Rect? = null }

/** Preserve original indices so queue drag ordering and stable keys stay intact. */
private class RoundListScope(
    private val delegate: LazyListScope,
    private val state: LazyListState,
    private val containerTop: () -> Float,
) : LazyListScope {
    private var nextIndex = 0
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val index = nextIndex++
        delegate.item(key, contentType) {
            Box(Modifier.fillMaxWidth().testTag("round-list-item").roundListItem(this@RoundListScope.state, index, this@RoundListScope.containerTop), contentAlignment = Alignment.Center) { content() }
        }
    }

    override fun items(
        count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?,
        itemContent: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        val start = nextIndex
        nextIndex += count
        delegate.items(count, key, contentType) { index ->
            Box(Modifier.fillMaxWidth().testTag("round-list-item").roundListItem(this@RoundListScope.state, start + index, this@RoundListScope.containerTop), contentAlignment = Alignment.Center) { itemContent(index) }
        }
    }
}

/** Shared circular list: proportional end space, crown scrolling and chord-aware items. */
@Composable
fun WatchLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    roundInsets: Boolean = true,
    pinnedTitle: String? = null,
    onPinnedBack: (() -> Unit)? = null,
    pinnedAction: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val dimensions = LocalWatchDimensions.current
    val round = dimensions.isRound
    val config = LocalConfiguration.current
    val direction = LocalLayoutDirection.current
    val focus = remember { FocusRequester() }
    val crown = rememberCrownScroll(state, userScrollEnabled)
    @android.annotation.SuppressLint("ContextCastToActivity")
    val activity = LocalContext.current as? MainActivity
    val nativeCrownHandler = remember(crown) { { pixels: Float, timeMs: Long -> crown.offer(pixels, timeMs) } }
    val view = LocalView.current
    val crownViewport = remember { CrownViewport() }
    DisposableEffect(activity, nativeCrownHandler, round) {
        if (round) activity?.registerListCrownHandler(nativeCrownHandler) {
            val bounds = crownViewport.bounds
            view.hasWindowFocus() && bounds != null && bounds.contains(
                androidx.compose.ui.geometry.Offset(view.width / 2f, view.height / 2f),
            )
        }
        onDispose { activity?.removeListCrownHandler(nativeCrownHandler) }
    }
    val density = LocalDensity.current
    val diameter = with(density) { minOf(config.screenWidthDp, config.screenHeightDp).dp.toPx() }
    var containerTop by remember { mutableFloatStateOf(0f) }
    val miniPlayer = LocalMiniPlayerController.current
    LaunchedEffect(state, miniPlayer) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) miniPlayer?.onListScrollStarted()
        }
    }
    val padding = if (round && roundInsets) PaddingValues(
        start = contentPadding.calculateStartPadding(direction),
        end = contentPadding.calculateEndPadding(direction),
        top = maxOf(contentPadding.calculateTopPadding(), config.screenHeightDp.dp * .15f),
        bottom = maxOf(contentPadding.calculateBottomPadding(), config.screenHeightDp.dp * .34f),
    ) else contentPadding
    LaunchedEffect(round) { if (round) focus.requestFocus() }
    val showPinned by remember(state, pinnedTitle, density) {
        derivedStateOf {
            pinnedTitle != null && (state.firstVisibleItemIndex > 0 ||
                state.firstVisibleItemScrollOffset > with(density) { 10.dp.toPx() })
        }
    }
    Box(modifier) {
    LazyColumn(
        modifier = if (round) Modifier.fillMaxSize().onGloballyPositioned {
            crownViewport.bounds = it.boundsInWindow()
            val screenHeight = view.height.toFloat().takeIf { h -> h > 0f } ?: diameter
            containerTop = it.positionInWindow().y - (screenHeight - diameter) / 2f
        }.focusRequester(focus).onRotaryScrollEvent {
            crown.offer(it.verticalScrollPixels, it.uptimeMillis)
        }.focusable() else Modifier.fillMaxSize(),
        state = state, contentPadding = padding, reverseLayout = reverseLayout,
        verticalArrangement = if (round) Arrangement.spacedBy(dimensions.itemSpacing) else verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled,
    ) {
        if (round) RoundListScope(this, state) { containerTop }.content() else content()
    }
    if (showPinned) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(colorStops = arrayOf(
                0f to androidx.compose.ui.graphics.Color.Black,
                .9f to androidx.compose.ui.graphics.Color.Black,
                1f to androidx.compose.ui.graphics.Color.Transparent)))
            .padding(top = if (round) 20.dp else 0.dp)
            .padding(horizontal = if (round) config.screenWidthDp.dp * .11f else 0.dp).padding(bottom = 5.dp)
            .testTag("pinned-list-header")) {
            WatchPageHeader(pinnedTitle.orEmpty(), onPinnedBack ?: {}, compact = true, action = pinnedAction)
        }
    }
    }
}

/** Compact row stays below the clock; the original header still shrinks with the list. */
@Composable
fun WatchPageHeader(
    title: String,
    onBack: () -> Unit,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(if (compact) 36.dp else 42.dp),
        verticalAlignment = Alignment.CenterVertically) {
        WatchIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回",
            buttonSize = if (compact) 30.dp else 32.dp, onClick = onBack)
        Text(title, Modifier.weight(1f).padding(horizontal = 3.dp),
            fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) action() else Spacer(Modifier.width(if (compact) 30.dp else 32.dp))
    }
}

@Composable
fun OppoRoundClock(modifier: Modifier = Modifier) {
    if (!LocalWatchDimensions.current.isRound) return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var formatRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
                formatRevision++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { formatRevision++ }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    val time = remember(now, formatRevision, LocalConfiguration.current) {
        DateFormat.getTimeFormat(context).format(Date(now))
    }
    val fontSp = dimensionResource(com.heytap.wearable.R.dimen.hey_m_desription).value / LocalDensity.current.fontScale
    val background = androidx.compose.ui.graphics.Color.Black
    val shadowRadius = with(LocalDensity.current) { 3.dp.toPx() }
    // Draw in the same layer as page content: API 30 AndroidView clocks can
    // disappear until their next minute tick after a navigation animation.
    Box(modifier.fillMaxWidth().graphicsLayer()
        .background(Brush.verticalGradient(colorStops = arrayOf(
            0f to background.copy(alpha = .84f),
            .55f to background.copy(alpha = .78f),
            .86f to background.copy(alpha = .22f),
            1f to background.copy(alpha = 0f),
        ))).testTag("round-clock-scrim").padding(bottom = 3.dp),
        contentAlignment = Alignment.TopCenter) {
        Text(time, Modifier.wrapContentWidth().testTag("round-clock"), color = WatchTextPrimary,
            fontSize = fontSp.sp, fontWeight = FontWeight.Bold, letterSpacing = .05.em, maxLines = 1,
            style = TextStyle(shadow = Shadow(color = background.copy(alpha = .85f), blurRadius = shadowRadius)))
    }
}

@Composable
fun OppoRoundSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // The supplied AAR links to firmware-only haptic classes. Do not load its
    // switch on AOSP or older devices without the matching system service.
    val nativeSupported = remember(context) {
        runCatching {
            Class.forName("android.os.linearmotorvibrator.LinearmotorVibrator")
            context.getSystemService("linearmotor") != null
        }.getOrDefault(false)
    }
    if (!nativeSupported) {
        Box(Modifier.size(40.dp).background(if (checked) WatchAccent else WatchSurfaceRaised, CircleShape)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
            Icon(if (checked) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null,
                Modifier.size(24.dp), tint = WatchTextPrimary)
        }
        return
    }
    val listener by rememberUpdatedState(onCheckedChange)
    AndroidView(
        factory = { LayoutInflater.from(it).inflate(R.layout.oppo_round_switch, null) as HeySwitch },
        modifier = Modifier.size(40.dp),
        update = {
            it.setOnCheckedChangeListener(null)
            it.isChecked = checked
            it.contentDescription = if (checked) "已开启" else "已关闭"
            it.setOnCheckedChangeListener { _, value -> listener(value) }
        },
    )
}
