package app.tuneroam.radio.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.playback.PlaybackNotice
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.playback.StreamUrlKind
import app.tuneroam.radio.presentation.PlayerHint
import app.tuneroam.radio.presentation.PlayerViewModel
import app.tuneroam.radio.presentation.TracksViewModel
import app.tuneroam.radio.ui.components.HealthDot
import app.tuneroam.radio.ui.components.SaveTrackButton
import app.tuneroam.radio.ui.components.SearchTrackButton
import app.tuneroam.radio.ui.components.StationMark
import app.tuneroam.radio.ui.fx.AuroraBackground
import app.tuneroam.radio.ui.fx.DecodeText
import app.tuneroam.radio.ui.fx.EqualizerBars
import app.tuneroam.radio.ui.fx.PlayPauseGlyph
import app.tuneroam.radio.ui.fx.SignalOrb
import app.tuneroam.radio.ui.fx.audioScale
import app.tuneroam.radio.ui.fx.drawSignalTrace
import app.tuneroam.radio.ui.fx.rememberAudioLevel
import app.tuneroam.radio.ui.fx.rememberStationColors
import app.tuneroam.radio.ui.theme.Motion
import app.tuneroam.radio.ui.theme.Signal
import app.tuneroam.radio.ui.theme.animationClock
import app.tuneroam.radio.ui.theme.focusRing
import app.tuneroam.radio.ui.theme.popOnChange
import app.tuneroam.radio.ui.theme.pressSquish
import app.tuneroam.radio.ui.theme.rememberReducedMotion
import app.tuneroam.radio.ui.theme.rememberWideLayout
import app.tuneroam.radio.ui.theme.sharedArtwork
import app.tuneroam.radio.ui.theme.usingRemote
import java.util.Locale
import kotlin.math.sin
import org.koin.androidx.compose.koinViewModel

/** The full-screen aurora behind Now Playing, in the playing station's colors. */
@Composable
fun PlayerBackdrop(snapshot: PlaybackSnapshot, modifier: Modifier = Modifier) {
    val (primary, secondary) = rememberStationColors(snapshot.station)
    val energy = animateFloatAsState(
        targetValue = if (snapshot.phase == PlaybackPhase.PLAYING) 1f else RESTING_ENERGY,
        animationSpec = tween(1_200),
        label = "aurora-energy",
    )
    // Paused, the curtains dim and then hold still, so the screen stops redrawing a full-screen shader.
    val resting = snapshot.phase != PlaybackPhase.PLAYING && snapshot.phase != PlaybackPhase.CONNECTING
    val settled by remember(resting) { derivedStateOf { resting && energy.value == RESTING_ENERGY } }
    AuroraBackground(
        primary = { primary.value },
        secondary = { secondary.value },
        energy = { energy.value },
        modifier = modifier,
        moving = !settled,
    )
}

/** How bright the aurora stays while nothing plays. */
private const val RESTING_ENERGY = 0.25f

@Composable
fun MiniPlayerBar(
    onOpen: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
    onNext: () -> Unit = viewModel::next,
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val station = playback.station ?: return
    val (primary, _) = rememberStationColors(station)
    val playing = playback.phase == PlaybackPhase.PLAYING
    val tracePath = remember { Path() }
    val reduced = rememberReducedMotion()
    // Steps rather than runs every frame: this bar sits on every browse screen while a station plays.
    val time = animationClock(
        running = !reduced && (playing || playback.phase == PlaybackPhase.CONNECTING),
        stepMillis = Motion.AMBIENT_STEP_MS,
    )
    val noise by animateFloatAsState(
        targetValue = if (playback.phase == PlaybackPhase.CONNECTING) 0.9f else 0f,
        animationSpec = tween(500),
        label = "mini-noise",
    )
    val audioLevel = rememberAudioLevel(active = playing && !reduced, stepMillis = Motion.AMBIENT_STEP_MS)
    val audioWeight by animateFloatAsState(if (playing && !reduced) 1f else 0f, tween(500), label = "mini-audio")
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .focusRing(shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(primary.value.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceVariant),
                ),
            )
            .border(1.dp, primary.value.copy(alpha = 0.25f), shape)
            .clickable(onClick = onOpen)
            .drawBehind {
                // A live signal line along the bottom edge: static while connecting, a carrier once on air.
                drawSignalTrace(
                    path = tracePath,
                    center = Offset(size.width / 2f, size.height - 3.dp.toPx()),
                    width = size.width * 0.9f,
                    amplitude = 2.5.dp.toPx(),
                    time = time.value,
                    noise = noise,
                    locked = primary.value.copy(alpha = if (playing) 0.7f else 0.25f),
                    static = Color.White.copy(alpha = 0.5f),
                    audio = audioScale(audioLevel.value, audioWeight),
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationMark(station, Modifier.size(42.dp).sharedArtwork(station.stationUuid))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EqualizerBars(
                    playing = playing,
                    color = primary.value,
                    modifier = Modifier.size(width = 14.dp, height = 12.dp),
                    stepMillis = Motion.AMBIENT_STEP_MS,
                )
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DecodeText(
                text = playback.icyTitle ?: station.country.ifBlank { stringResource(R.string.now_playing) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (playback.icyTitle != null) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayButton(playing = playing, size = 44, onClick = viewModel::togglePlay)
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_station))
        }
    }
}

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
    tracks: TracksViewModel = koinViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val trackSaved by tracks.currentSaved.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val station = playback.station
    val (primary, secondary) = rememberStationColors(station)
    // Which way the last skip went, so the next station slides in from that side.
    var direction by remember { mutableIntStateOf(1) }
    val wide = rememberWideLayout()
    val playFocus = remember { FocusRequester() }
    val remote = usingRemote()
    // A remote lands on play/pause, the button it most likely wants.
    LaunchedEffect(station == null) {
        if (remote && station != null) playFocus.requestFocus()
    }
    // Wide, the whole screen fits and only the details column scrolls.
    Column(
        modifier = if (wide) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val context = LocalContext.current
        val shareTitle = stringResource(R.string.share_station)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.now_playing).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (station != null) {
                IconButton(
                    onClick = {
                        val text = viewModel.shareText() ?: return@IconButton
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = shareTitle)
                }
            } else {
                Box(Modifier.size(48.dp))
            }
        }
        if (station == null) {
            LinkHint(hint)
            Text(
                text = stringResource(R.string.empty_popular),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            return
        }
        val playing = playback.phase == PlaybackPhase.PLAYING
        val orb: @Composable (Modifier) -> Unit = { orbModifier ->
            SignalOrb(
                phase = playback.phase,
                primary = { primary.value },
                secondary = { secondary.value },
                modifier = orbModifier,
            ) {
                AnimatedContent(
                    targetState = station,
                    contentKey = { it.stationUuid },
                    transitionSpec = {
                        (fadeIn(tween(280, delayMillis = 100)) + scaleIn(tween(380), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 1.3f))
                    },
                    label = "artwork",
                ) { shown ->
                    StationMark(shown, Modifier.size(196.dp).sharedArtwork(shown.stationUuid))
                }
            }
        }
        val details: @Composable () -> Unit = {
            OnAirLine(playback, Modifier.padding(top = 2.dp))
            AnimatedContent(
                targetState = station,
                contentKey = { it.stationUuid },
                transitionSpec = {
                    val dir = direction
                    (slideInHorizontally(tween(320)) { it / 3 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 3 * dir } + fadeOut(tween(180))) using
                        SizeTransform(clip = false)
                },
                label = "station-name",
            ) { shown ->
                Text(
                    text = shown.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 24.dp, end = 24.dp),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center,
                )
            }
            playback.icyTitle?.let { title ->
                DecodeText(
                    text = title,
                    modifier = Modifier.padding(top = 10.dp, start = 28.dp, end = 28.dp),
                    color = Signal,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SaveTrackButton(saved = trackSaved, onToggle = tracks::toggleCurrent)
                    SearchTrackButton(title)
                }
            }
            Row(
                modifier = Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.favorite_remove else R.string.favorite_add,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .popOnChange(favorite),
                    )
                }
                IconButton(
                    onClick = {
                        direction = -1
                        viewModel.previous()
                    },
                    enabled = canGoBack,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_station),
                        modifier = Modifier.size(34.dp),
                    )
                }
                PlayButton(
                    playing = playing,
                    size = 78,
                    onClick = viewModel::togglePlay,
                    glow = primary.value,
                    modifier = Modifier.focusRequester(playFocus),
                )
                IconButton(
                    onClick = {
                        direction = 1
                        viewModel.next()
                    },
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.next_station),
                        modifier = Modifier.size(34.dp),
                    )
                }
                Box(Modifier.size(48.dp))
            }
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HealthDot(ok = station.lastCheckOk)
                Text(
                    text = stringResource(if (station.lastCheckOk) R.string.health_ok else R.string.health_down),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val technical = station.technicalLabel()
            if (technical.isNotBlank() || station.tagList.isNotEmpty()) {
                Text(
                    text = listOfNotNull(
                        technical.takeIf { it.isNotBlank() },
                        station.tagList.take(4).joinToString(" · ").takeIf { it.isNotBlank() },
                    ).joinToString("   "),
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Notice(playback)
            when (hint) {
                PlayerHint.SEARCHING -> Text(
                    text = stringResource(R.string.searching_similar),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
                PlayerHint.NO_SIMILAR -> Text(
                    text = stringResource(R.string.no_similar),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                PlayerHint.OPENING_LINK, PlayerHint.LINK_NOT_FOUND -> LinkHint(hint)
                null -> Unit
            }
            if (playback.notice == PlaybackNotice.UNAVAILABLE || playback.phase == PlaybackPhase.FAILED) {
                Button(
                    onClick = viewModel::playSimilar,
                    enabled = hint != PlayerHint.SEARCHING,
                    modifier = Modifier.padding(top = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.next_similar))
                }
            }
            SleepTimerControl(viewModel, Modifier.padding(top = 24.dp))
        }
        if (wide) {
            // Landscape: the orb on the left, the station and its controls beside it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    orb(Modifier.size(320.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        details()
                    }
                }
            }
        } else {
            orb(Modifier.size(340.dp))
            details()
        }
    }
}

/** "● ON AIR · Country" with a blinking dot while the stream plays. */
@Composable
private fun OnAirLine(playback: PlaybackSnapshot, modifier: Modifier = Modifier) {
    val station = playback.station ?: return
    val playing = playback.phase == PlaybackPhase.PLAYING
    val reduced = rememberReducedMotion()
    val time = animationClock(running = playing && !reduced)
    val live = MaterialTheme.colorScheme.error
    val label = stringResource(if (playing) R.string.roam_status_on_air else R.string.now_playing)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .drawBehind {
                    val alpha = if (playing) 0.35f + 0.65f * (0.5f + 0.5f * sin(time.value * 5f)) else 0.4f
                    drawCircle((if (playing) live else Color.White).copy(alpha = alpha))
                },
        )
        Text(
            text = listOfNotNull(
                label.uppercase(Locale.getDefault()),
                station.country.takeIf { it.isNotBlank() },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A round play button whose glyph folds between play and pause; it glows while playing. */
@Composable
private fun PlayButton(
    playing: Boolean,
    size: Int,
    onClick: () -> Unit,
    glow: Color? = null,
    modifier: Modifier = Modifier,
) {
    val press = remember { MutableInteractionSource() }
    val description = stringResource(if (playing) R.string.pause else R.string.play)
    val shine by animateFloatAsState(if (playing && glow != null) 1f else 0f, tween(600), label = "shine")
    Box(
        modifier = modifier
            .size(size.dp)
            .pressSquish(press)
            .drawBehind {
                if (glow != null && shine > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.45f * shine), Color.Transparent),
                            radius = this.size.minDimension * 0.9f,
                        ),
                        radius = this.size.minDimension * 0.9f,
                    )
                }
            }
            .focusRing(CircleShape, outset = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(
            playing = playing,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * 0.46f).dp),
        )
    }
}

@Composable
private fun LinkHint(hint: PlayerHint?) {
    val (text, color) = when (hint) {
        PlayerHint.OPENING_LINK -> stringResource(R.string.opening_link) to MaterialTheme.colorScheme.secondary
        PlayerHint.LINK_NOT_FOUND -> stringResource(R.string.link_not_found) to MaterialTheme.colorScheme.error
        else -> return
    }
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Notice(playback: PlaybackSnapshot) {
    val text = when (playback.notice) {
        PlaybackNotice.TRYING_ALTERNATE -> stringResource(R.string.trying_alternate)
        PlaybackNotice.UNAVAILABLE -> stringResource(R.string.stream_unavailable)
        PlaybackNotice.NEXT_SIMILAR -> stringResource(R.string.next_similar_auto)
        PlaybackNotice.NONE -> if (playback.activeKind == StreamUrlKind.ORIGINAL && playback.phase == PlaybackPhase.PLAYING) {
            stringResource(R.string.using_original_url)
        } else {
            null
        }
    } ?: return
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, start = 28.dp, end = 28.dp),
        color = if (playback.notice == PlaybackNotice.UNAVAILABLE) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.secondary
        },
        textAlign = TextAlign.Center,
    )
}
