/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.antiglitch.yetanothernotifier.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.antiglitch.yetanothernotifier.player.buttons.ExtraControls
import  com.antiglitch.yetanothernotifier.player.buttons.MinimalControls
import  com.antiglitch.yetanothernotifier.player.data.videos
import androidx.media3.demo.compose.layout.CONTENT_SCALES
import androidx.media3.demo.compose.layout.noRippleClickable
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

class ExoPlayerFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { ExoPlayerComposable() }
    }
  }
}

@Composable
fun ExoPlayerComposable(
  player: Player? = null,
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = RectangleShape
) {
  val context = LocalContext.current
  var internalPlayer by remember { mutableStateOf<Player?>(null) }
  
  // Use provided player or create internal one
  val currentPlayer = player ?: internalPlayer

  // Only manage lifecycle if no external player is provided
  if (player == null) {
    if (Build.VERSION.SDK_INT > 23) {
      LifecycleStartEffect(Unit) {
        internalPlayer = initializePlayer(context)
        onStopOrDispose {
          internalPlayer?.apply { release() }
          internalPlayer = null
        }
      }
    } else {
      LifecycleResumeEffect(Unit) {
        internalPlayer = initializePlayer(context)
        onPauseOrDispose {
          internalPlayer?.apply { release() }
          internalPlayer = null
        }
      }
    }
  }

  currentPlayer?.let { MediaPlayerScreen(player = it, modifier = modifier.fillMaxSize(), shape = shape) }
}

private fun initializePlayer(context: Context): Player =
  ExoPlayer.Builder(context).build().apply {
    setMediaItems(videos.map(MediaItem::fromUri))
    prepare()
  }

@OptIn(UnstableApi::class)
@Composable
private fun MediaPlayerScreen(
  player: Player, 
  modifier: Modifier = Modifier,
  shape: androidx.compose.ui.graphics.Shape = RectangleShape
) {
  var showControls by remember { mutableStateOf(false) } // Hide controls by default for notification
  var currentContentScaleIndex by remember { mutableIntStateOf(0) }
  val contentScale = CONTENT_SCALES[currentContentScaleIndex].second

  val presentationState = rememberPresentationState(player)
  val scaledModifier = Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)

  Box(modifier.clip(shape)) {
    PlayerSurface(
      player = player,
      surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
      modifier = scaledModifier
        .fillMaxSize()
        .clip(shape)
        .noRippleClickable { showControls = !showControls },
    )

    if (presentationState.coverSurface) {
      // Cover the surface that is being prepared with a shutter
      // Do not use scaledModifier here, makes the Box be measured at 0x0
      Box(Modifier.matchParentSize().background(Color.Black))
    }

    if (showControls) {
      // drawn on top of a potential shutter
      MinimalControls(player, Modifier.align(Alignment.Center))
      ExtraControls(
        player,
        Modifier.fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(Color.Gray.copy(alpha = 0.4f))
          .navigationBarsPadding(),
      )
    }
  }
}
