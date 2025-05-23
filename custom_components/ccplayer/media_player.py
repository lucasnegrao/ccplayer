"""Media player platform for CC Player."""

import logging
from typing import Any, Dict, List, Optional
from homeassistant.components import media_source
from homeassistant.components.media_player.browse_media import (
    async_process_play_media_url,
)
from homeassistant import util
from homeassistant.helpers import template
from homeassistant.components.media_player import (
    ATTR_APP_ID,
    ATTR_APP_NAME,
    ATTR_INPUT_SOURCE,
    ATTR_INPUT_SOURCE_LIST,
    ATTR_MEDIA_ALBUM_ARTIST,
    ATTR_MEDIA_ALBUM_NAME,
    ATTR_MEDIA_ARTIST,
    ATTR_MEDIA_CHANNEL,
    ATTR_MEDIA_CONTENT_ID,
    ATTR_MEDIA_CONTENT_TYPE,
    ATTR_MEDIA_DURATION,
    ATTR_MEDIA_EPISODE,
    ATTR_MEDIA_PLAYLIST,
    ATTR_MEDIA_POSITION,
    ATTR_MEDIA_POSITION_UPDATED_AT,
    ATTR_MEDIA_REPEAT,
    ATTR_MEDIA_SEASON,
    ATTR_MEDIA_SEEK_POSITION,
    ATTR_MEDIA_SERIES_TITLE,
    ATTR_MEDIA_SHUFFLE,
    ATTR_MEDIA_TITLE,
    ATTR_MEDIA_TRACK,
    ATTR_MEDIA_VOLUME_LEVEL,
    ATTR_MEDIA_VOLUME_MUTED,
    ATTR_SOUND_MODE,
    ATTR_SOUND_MODE_LIST,
    DEVICE_CLASSES_SCHEMA,
    DOMAIN as MEDIA_PLAYER_DOMAIN,
    PLATFORM_SCHEMA as MEDIA_PLAYER_PLATFORM_SCHEMA,
    SERVICE_CLEAR_PLAYLIST,
    SERVICE_PLAY_MEDIA,
    SERVICE_SELECT_SOUND_MODE,
    SERVICE_SELECT_SOURCE,
    BrowseMedia,
    MediaPlayerDeviceClass,
    MediaPlayerEnqueue,
    MediaPlayerEntity,
    MediaPlayerEntityFeature,
    MediaPlayerState,
    MediaType,
    RepeatMode,
)
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import (
    ATTR_ASSUMED_STATE,
    ATTR_ENTITY_ID,
    ATTR_ENTITY_PICTURE,
    ATTR_SUPPORTED_FEATURES,
    CONF_DEVICE_CLASS,
    CONF_NAME,
    CONF_STATE,
    CONF_STATE_TEMPLATE,
    CONF_UNIQUE_ID,
    EVENT_HOMEASSISTANT_START,
    SERVICE_MEDIA_NEXT_TRACK,
    SERVICE_MEDIA_PAUSE,
    SERVICE_MEDIA_PLAY,
    SERVICE_MEDIA_PLAY_PAUSE,
    SERVICE_MEDIA_PREVIOUS_TRACK,
    SERVICE_MEDIA_SEEK,
    SERVICE_MEDIA_STOP,
    SERVICE_REPEAT_SET,
    SERVICE_SHUFFLE_SET,
    SERVICE_TOGGLE,
    SERVICE_TURN_OFF,
    SERVICE_TURN_ON,
    SERVICE_VOLUME_DOWN,
    SERVICE_VOLUME_MUTE,
    SERVICE_VOLUME_SET,
    SERVICE_VOLUME_UP,
    STATE_OFF,
    STATE_ON,
    STATE_UNAVAILABLE,
    STATE_UNKNOWN,
)
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.entity_component import EntityComponent
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_state_change_event
from homeassistant.helpers.template import Template
from homeassistant.helpers.service import async_call_from_config
from copy import deepcopy

from .const import (
    CONF_ACTIONS,
    CONF_CLEAR_PLAYLIST_ACTION,
    CONF_MEDIA_ALBUM_ENTITY,
    CONF_MEDIA_ARTIST_ENTITY,
    CONF_MEDIA_DURATION_ENTITY,
    CONF_MEDIA_IMAGE_ENTITY,
    CONF_MEDIA_POSITION_ENTITY,
    CONF_MEDIA_TITLE_ENTITY,
    CONF_MUTE_ENTITY,
    CONF_NEXT_ACTION,
    CONF_PAUSE_ACTION,
    CONF_PLAY_ACTION,
    CONF_PLAY_MEDIA_ACTION,
    CONF_PLAY_PAUSE_ACTION,
    CONF_PLAYER_STATE_ENTITY,
    CONF_POWER_ENTITY,
    CONF_PREVIOUS_ACTION,
    CONF_REPEAT_SET_ACTION,
    CONF_SEEK_ACTION,
    CONF_SHUFFLE_SET_ACTION,
    CONF_SOURCE_ENTITY,
    CONF_SOURCE_LIST_ENTITY,
    CONF_STOP_ACTION,
    CONF_TOGGLE_ACTION,
    CONF_VOLUME_ENTITY,
    CONF_VOLUME_STEP,
    DEFAULT_NAME,
    DOMAIN,
)

_LOGGER = logging.getLogger(__name__)


async def async_setup_entry(
    hass: HomeAssistant,
    config_entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Set up the CC Player media player from a config entry."""
    name = config_entry.data.get(CONF_NAME, DEFAULT_NAME)
    async_add_entities([CCPlayerMediaPlayer(hass, config_entry, name)], True)


class CCPlayerMediaPlayer(MediaPlayerEntity):
    """Implementation of the CC Player media player."""

    _attr_has_entity_name = True
    _attr_device_class = MediaPlayerDeviceClass.RECEIVER

    def __init__(
        self, hass: HomeAssistant, config_entry: ConfigEntry, name: str
    ) -> None:
        """Initialize the CC Player media player."""
        self.hass = hass
        self._config_entry = config_entry
        self._attr_name = name
        self._attr_unique_id = config_entry.entry_id
        self._attr_supported_features = MediaPlayerEntityFeature(0)
        self._attr_state = None
        self._attr_source = None
        self._attr_source_list = None
        self._attr_volume_level = None
        self._attr_is_volume_muted = None
        self._attr_media_title = None
        self._attr_media_artist = None
        self._attr_media_album_name = None
        self._attr_media_image_url = None
        self._attr_media_position = None
        self._attr_media_duration = None
        self._attr_media_position_updated_at = None

        # Additional state tracking
        self._last_position_update = None

        # Entity references
        self._entity_refs = {}
        self._actions = {}
        self._unsubscribe_callbacks = []

        # Set up initial entities from config
        self._setup_from_config()

    async def async_added_to_hass(self) -> None:
        """Register callbacks when entity is added to hass."""
        await super().async_added_to_hass()

        # Set up state change listeners
        await self._setup_listeners()

        # Initial refresh
        await self._refresh_states()

        # Subscribe to config changes
        self._config_entry.add_update_listener(self._handle_config_update)

    async def async_will_remove_from_hass(self) -> None:
        """Clean up when entity is removed from hass."""
        for unsub in self._unsubscribe_callbacks:
            unsub()
        self._unsubscribe_callbacks = []

    @callback
    def _setup_from_config(self) -> None:
        """Set up player based on config."""
        options = self._config_entry.options
        _LOGGER.debug("Setting up from config with options: %s", options)

        # Store entity references
        self._entity_refs = {
            CONF_POWER_ENTITY: options.get(CONF_POWER_ENTITY),
            CONF_PLAYER_STATE_ENTITY: options.get(CONF_PLAYER_STATE_ENTITY),
            CONF_VOLUME_ENTITY: options.get(CONF_VOLUME_ENTITY),
            CONF_MUTE_ENTITY: options.get(CONF_MUTE_ENTITY),
            CONF_SOURCE_ENTITY: options.get(CONF_SOURCE_ENTITY),
            CONF_SOURCE_LIST_ENTITY: options.get(CONF_SOURCE_LIST_ENTITY),
            CONF_MEDIA_TITLE_ENTITY: options.get(CONF_MEDIA_TITLE_ENTITY),
            CONF_MEDIA_ARTIST_ENTITY: options.get(CONF_MEDIA_ARTIST_ENTITY),
            CONF_MEDIA_ALBUM_ENTITY: options.get(CONF_MEDIA_ALBUM_ENTITY),
            CONF_MEDIA_IMAGE_ENTITY: options.get(CONF_MEDIA_IMAGE_ENTITY),
            CONF_MEDIA_POSITION_ENTITY: options.get(CONF_MEDIA_POSITION_ENTITY),
            CONF_MEDIA_DURATION_ENTITY: options.get(CONF_MEDIA_DURATION_ENTITY),
        }

        # Store action configurations - handle both old and new format
        actions_config = options.get(CONF_ACTIONS, {})
        self._actions = {}

        _LOGGER.debug("Raw actions config from options: %s", actions_config)

        # Convert action configurations to proper format
        for action_key in [
            CONF_PLAY_ACTION,
            CONF_PAUSE_ACTION,
            CONF_STOP_ACTION,
            CONF_NEXT_ACTION,
            CONF_PREVIOUS_ACTION,
            CONF_TOGGLE_ACTION,
            CONF_PLAY_PAUSE_ACTION,
            CONF_SEEK_ACTION,
            CONF_PLAY_MEDIA_ACTION,
            CONF_CLEAR_PLAYLIST_ACTION,
            CONF_SHUFFLE_SET_ACTION,
            CONF_REPEAT_SET_ACTION,
        ]:
            if action_key in actions_config:
                self._actions[action_key] = actions_config[action_key]
                _LOGGER.debug(
                    "Loaded action %s: %s", action_key, actions_config[action_key]
                )
            # Also check if actions are stored directly in options (legacy support)
            elif action_key in options:
                self._actions[action_key] = options[action_key]
                _LOGGER.debug(
                    "Loaded legacy action %s: %s", action_key, options[action_key]
                )

        _LOGGER.debug("Total actions loaded: %s", list(self._actions.keys()))
        _LOGGER.debug("Actions dict: %s", self._actions)

        # Determine supported features based on configured entities and actions
        self._update_supported_features()

    async def _setup_listeners(self) -> None:
        """Set up state change listeners for tracked entities."""
        # Clear any existing listeners
        for unsub in self._unsubscribe_callbacks:
            unsub()
        self._unsubscribe_callbacks = []

        # Add listeners for each configured entity
        for entity_id in [entity for entity in self._entity_refs.values() if entity]:
            unsub = async_track_state_change_event(
                self.hass, [entity_id], self._handle_state_changed
            )
            self._unsubscribe_callbacks.append(unsub)

    async def _refresh_states(self) -> None:
        """Refresh all entity states."""
        import time

        # Determine player state from dedicated player state entity or power entity
        current_state = None

        # First try player state entity if configured
        if player_state_entity := self._entity_refs.get(CONF_PLAYER_STATE_ENTITY):
            state = self.hass.states.get(player_state_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                # Map common state values to MediaPlayerState
                state_value = state.state.lower()
                match state_value:
                    case "playing" | "play":
                        current_state = MediaPlayerState.PLAYING
                    case "paused" | "pause":
                        current_state = MediaPlayerState.PAUSED
                    case "stopped" | "stop":
                        current_state = MediaPlayerState.IDLE
                    case "idle":
                        current_state = MediaPlayerState.IDLE
                    case "buffering":
                        current_state = MediaPlayerState.BUFFERING
                    case "on" | "true":
                        current_state = MediaPlayerState.ON
                    case "off" | "false":
                        current_state = MediaPlayerState.OFF
                    case _:
                        # For unknown states, try to infer from media info
                        if self._has_active_media():
                            current_state = MediaPlayerState.PLAYING
                        else:
                            current_state = MediaPlayerState.IDLE

        # Fallback to power entity if no player state entity
        elif power_entity := self._entity_refs.get(CONF_POWER_ENTITY):
            state = self.hass.states.get(power_entity)
            if state:
                if state.state == STATE_ON:
                    # Try to determine if playing based on media info
                    if self._has_active_media():
                        current_state = MediaPlayerState.PLAYING
                    else:
                        current_state = MediaPlayerState.ON
                elif state.state == STATE_OFF:
                    current_state = MediaPlayerState.OFF

        # Volume
        if volume_entity := self._entity_refs.get(CONF_VOLUME_ENTITY):
            state = self.hass.states.get(volume_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                try:
                    # Assuming the volume entity has a value between 0 and 100
                    self._attr_volume_level = float(state.state) / 100
                except (ValueError, TypeError):
                    self._attr_volume_level = None

        # Mute
        if mute_entity := self._entity_refs.get(CONF_MUTE_ENTITY):
            state = self.hass.states.get(mute_entity)
            if state:
                self._attr_is_volume_muted = state.state == STATE_ON

        # Source
        if source_entity := self._entity_refs.get(CONF_SOURCE_ENTITY):
            state = self.hass.states.get(source_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                self._attr_source = state.state

                # Try to get source list from entity attributes
                if "options" in state.attributes:
                    self._attr_source_list = state.attributes["options"]

        # Source list from separate entity
        if source_list_entity := self._entity_refs.get(CONF_SOURCE_LIST_ENTITY):
            state = self.hass.states.get(source_list_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                try:
                    # Try to parse as a list if it's a string
                    import json

                    self._attr_source_list = json.loads(state.state)
                except (ValueError, TypeError):
                    # If not parseable as JSON, split by commas
                    self._attr_source_list = [s.strip() for s in state.state.split(",")]

        # Media info
        for attr, entity_key in [
            ("_attr_media_title", CONF_MEDIA_TITLE_ENTITY),
            ("_attr_media_artist", CONF_MEDIA_ARTIST_ENTITY),
            ("_attr_media_album_name", CONF_MEDIA_ALBUM_ENTITY),
        ]:
            if entity_id := self._entity_refs.get(entity_key):
                state = self.hass.states.get(entity_id)
                if state and state.state not in (None, "unknown", "unavailable", ""):
                    setattr(self, attr, state.state)
                else:
                    setattr(self, attr, None)

        # Media duration
        if duration_entity := self._entity_refs.get(CONF_MEDIA_DURATION_ENTITY):
            state = self.hass.states.get(duration_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                try:
                    self._attr_media_duration = float(state.state) / 1000
                except (ValueError, TypeError):
                    self._attr_media_duration = None
            else:
                self._attr_media_duration = None

        # Media image (special handling for image entity)
        if image_entity := self._entity_refs.get(CONF_MEDIA_IMAGE_ENTITY):
            state = self.hass.states.get(image_entity)
            if state:
                # For image entities, use the entity_picture attribute if available
                if "entity_picture" in state.attributes:
                    self._attr_media_image_url = state.attributes["entity_picture"]
                elif state.state not in (None, "unknown", "unavailable", ""):
                    # Fallback to state value if it looks like a URL
                    if state.state.startswith(("http://", "https://", "/local/")):
                        self._attr_media_image_url = state.state
                    else:
                        self._attr_media_image_url = None
                else:
                    self._attr_media_image_url = None

        # Media position - improved tracking
        if position_entity := self._entity_refs.get(CONF_MEDIA_POSITION_ENTITY):
            state = self.hass.states.get(position_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                try:
                    new_position = float(state.state) / 1000
                    current_time = time.time()

                    # Always update position and timestamp
                    self._attr_media_position = new_position
                    self._attr_media_position_updated_at = util.dt.utcnow()
                    self._last_position_update = current_time

                    # If position changed recently, we're likely playing
                    if (
                        current_state in [MediaPlayerState.ON, None]
                        and new_position > 0
                        and self._attr_media_duration
                        and new_position < self._attr_media_duration
                    ):
                        current_state = MediaPlayerState.PLAYING

                    _LOGGER.debug(
                        "Updated media position: %s at time %s",
                        new_position,
                        current_time,
                    )
                except (ValueError, TypeError):
                    self._attr_media_position = None
                    self._attr_media_position_updated_at = None
            else:
                self._attr_media_position = None
                self._attr_media_position_updated_at = None

        # Update the final state after all processing
        self._attr_state = current_state
        self.async_write_ha_state()

    @property
    def device_class(self) -> MediaPlayerDeviceClass | None:
        """Return the device class."""
        # Use TV for video content since we're dealing with media players
        if (
            self._attr_media_title
            or self._attr_media_artist
            or self._attr_media_album_name
            or self._attr_media_image_url
        ):
            return MediaPlayerDeviceClass.TV
        return MediaPlayerDeviceClass.RECEIVER

    @property
    def media_content_type(self) -> str | None:
        """Return the media content type."""
        # For now, always return video since this appears to be a video player
        if self._attr_media_title or self._attr_media_duration:
            return "video"
        return None

    @property
    def extra_state_attributes(self) -> dict[str, Any] | None:
        """Return entity specific state attributes."""
        attrs = {}

        # Always include configured entities for debugging
        attrs["configured_entities"] = {
            key: entity_id
            for key, entity_id in self._entity_refs.items()
            if entity_id is not None
        }

        # Include configured actions with full details for debugging
        attrs["configured_actions"] = self._actions
        attrs["config_entry_options"] = dict(self._config_entry.options)

        # Media info
        if self._attr_media_title:
            attrs["media_title"] = self._attr_media_title
        if self._attr_media_artist:
            attrs["media_artist"] = self._attr_media_artist
        if self._attr_media_album_name:
            attrs["media_album"] = self._attr_media_album_name
        if self._attr_media_duration:
            attrs["media_duration"] = self._attr_media_duration
        if self._attr_media_position is not None:
            attrs["media_position"] = self._attr_media_position

        return attrs

    @callback
    def _update_supported_features(self) -> None:
        """Update the supported features based on the configured entities and actions."""
        features = MediaPlayerEntityFeature(0)

        # Power control
        if self._entity_refs.get(CONF_POWER_ENTITY):
            features |= (
                MediaPlayerEntityFeature.TURN_ON | MediaPlayerEntityFeature.TURN_OFF
            )

        # Volume control
        if self._entity_refs.get(CONF_VOLUME_ENTITY):
            features |= (
                MediaPlayerEntityFeature.VOLUME_SET
                | MediaPlayerEntityFeature.VOLUME_STEP
            )

        # Mute control
        if self._entity_refs.get(CONF_MUTE_ENTITY):
            features |= MediaPlayerEntityFeature.VOLUME_MUTE

        # Source selection
        if self._entity_refs.get(CONF_SOURCE_ENTITY):
            features |= MediaPlayerEntityFeature.SELECT_SOURCE

        # Transport controls based on configured actions
        if self._actions.get(CONF_PLAY_ACTION):
            features |= MediaPlayerEntityFeature.PLAY

        if self._actions.get(CONF_PAUSE_ACTION):
            features |= MediaPlayerEntityFeature.PAUSE

        if self._actions.get(CONF_STOP_ACTION):
            features |= MediaPlayerEntityFeature.STOP

        if self._actions.get(CONF_NEXT_ACTION):
            features |= MediaPlayerEntityFeature.NEXT_TRACK

        if self._actions.get(CONF_PREVIOUS_ACTION):
            features |= MediaPlayerEntityFeature.PREVIOUS_TRACK

        if self._actions.get(CONF_PLAY_PAUSE_ACTION):
            features |= MediaPlayerEntityFeature.PLAY | MediaPlayerEntityFeature.PAUSE

        if self._actions.get(CONF_SEEK_ACTION):
            features |= MediaPlayerEntityFeature.SEEK

        if self._actions.get(CONF_PLAY_MEDIA_ACTION):
            features |= MediaPlayerEntityFeature.PLAY_MEDIA

        if self._actions.get(CONF_CLEAR_PLAYLIST_ACTION):
            features |= MediaPlayerEntityFeature.CLEAR_PLAYLIST

        if self._actions.get(CONF_SHUFFLE_SET_ACTION):
            features |= MediaPlayerEntityFeature.SHUFFLE_SET

        if self._actions.get(CONF_REPEAT_SET_ACTION):
            features |= MediaPlayerEntityFeature.REPEAT_SET

        # Add browse media support
        features |= MediaPlayerEntityFeature.BROWSE_MEDIA

        self._attr_supported_features = features
        _LOGGER.debug(
            "Updated supported features: %s, actions: %s", features, self._actions
        )

    async def _handle_state_changed(self, event) -> None:
        """Handle state changes in tracked entities."""
        await self._refresh_states()

    async def _handle_config_update(self, hass, config_entry) -> None:
        """Handle configuration updates."""
        self._setup_from_config()
        await self._setup_listeners()
        await self._refresh_states()

    @property
    def device_info(self):
        """Return device information."""
        # Use the selected device's identifier if present, otherwise fallback to our own
        linked_identifier = self._config_entry.data.get("linked_device_identifier")
        if linked_identifier:
            # linked_identifier is a tuple (domain, id)
            return {
                "identifiers": {tuple(linked_identifier)},
                "name": self.name,
                "manufacturer": "Custom Component",
                "model": "CC Player",
                "sw_version": "1.0.0",
            }
        return {
            "identifiers": {(DOMAIN, self._config_entry.entry_id)},
            "name": self.name,
            "manufacturer": "Custom Component",
            "model": "CC Player",
            "sw_version": "1.0.0",
        }

    async def async_turn_on(self) -> None:
        """Turn the media player on."""
        if power_entity := self._entity_refs.get(CONF_POWER_ENTITY):
            await self.hass.services.async_call(
                "homeassistant", SERVICE_TURN_ON, {ATTR_ENTITY_ID: power_entity}
            )

    async def async_turn_off(self) -> None:
        """Turn the media player off."""
        if power_entity := self._entity_refs.get(CONF_POWER_ENTITY):
            await self.hass.services.async_call(
                "homeassistant", SERVICE_TURN_OFF, {ATTR_ENTITY_ID: power_entity}
            )

    async def async_volume_up(self) -> None:
        """Turn volume up."""
        if volume_entity := self._entity_refs.get(CONF_VOLUME_ENTITY):
            current = self.hass.states.get(volume_entity)
            if current and current.state not in (None, "unknown", "unavailable"):
                try:
                    current_value = float(current.state)
                    step = self._config_entry.options.get(CONF_VOLUME_STEP, 0.05) * 100
                    new_value = min(100, current_value + step)

                    domain = volume_entity.split(".", 1)[0]
                    service = "set_value" if domain == "input_number" else "set_value"

                    await self.hass.services.async_call(
                        domain,
                        service,
                        {ATTR_ENTITY_ID: volume_entity, "value": new_value},
                    )
                except (ValueError, TypeError):
                    _LOGGER.warning("Could not increase volume for %s", volume_entity)

    async def async_volume_down(self) -> None:
        """Turn volume down."""
        if volume_entity := self._entity_refs.get(CONF_VOLUME_ENTITY):
            current = self.hass.states.get(volume_entity)
            if current and current.state not in (None, "unknown", "unavailable"):
                try:
                    current_value = float(current.state)
                    step = self._config_entry.options.get(CONF_VOLUME_STEP, 0.05) * 100
                    new_value = max(0, current_value - step)

                    domain = volume_entity.split(".", 1)[0]
                    service = "set_value" if domain == "input_number" else "set_value"

                    await self.hass.services.async_call(
                        domain,
                        service,
                        {ATTR_ENTITY_ID: volume_entity, "value": new_value},
                    )
                except (ValueError, TypeError):
                    _LOGGER.warning("Could not decrease volume for %s", volume_entity)

    async def async_set_volume_level(self, volume: float) -> None:
        """Set volume level, range 0..1."""
        if volume_entity := self._entity_refs.get(CONF_VOLUME_ENTITY):
            domain = volume_entity.split(".", 1)[0]
            service = "set_value" if domain == "input_number" else "set_value"

            await self.hass.services.async_call(
                domain, service, {ATTR_ENTITY_ID: volume_entity, "value": volume * 100}
            )

    async def async_mute_volume(self, mute: bool) -> None:
        """Mute or unmute media player."""
        if mute_entity := self._entity_refs.get(CONF_MUTE_ENTITY):
            service = SERVICE_TURN_ON if mute else SERVICE_TURN_OFF
            await self.hass.services.async_call(
                "homeassistant", service, {ATTR_ENTITY_ID: mute_entity}
            )

    async def async_select_source(self, source: str) -> None:
        """Select input source."""
        if source_entity := self._entity_refs.get(CONF_SOURCE_ENTITY):
            domain = source_entity.split(".", 1)[0]
            service = "select_option" if domain == "input_select" else "select_option"

            await self.hass.services.async_call(
                domain, service, {ATTR_ENTITY_ID: source_entity, "option": source}
            )

    async def async_media_play(self) -> None:
        """Send play command."""
        await self._call_action_list(CONF_PLAY_ACTION)

    async def async_media_pause(self) -> None:
        """Send pause command."""
        await self._call_action_list(CONF_PAUSE_ACTION)

    async def async_media_stop(self) -> None:
        """Send stop command."""
        await self._call_action_list(CONF_STOP_ACTION)

    async def async_media_next_track(self) -> None:
        """Send next track command."""
        await self._call_action_list(CONF_NEXT_ACTION)

    async def async_media_previous_track(self) -> None:
        """Send previous track command."""
        await self._call_action_list(CONF_PREVIOUS_ACTION)

    async def async_toggle(self) -> None:
        """Toggle the media player."""
        toggle_actions = self._actions.get(CONF_TOGGLE_ACTION)
        if toggle_actions:
            await self._call_action_list(CONF_TOGGLE_ACTION)
        elif power_entity := self._entity_refs.get(CONF_POWER_ENTITY):
            # Fallback to power entity toggle
            await self.hass.services.async_call(
                "homeassistant", "toggle", {ATTR_ENTITY_ID: power_entity}
            )

    async def async_media_play_pause(self) -> None:
        """Play or pause the media player."""
        play_pause_actions = self._actions.get(CONF_PLAY_PAUSE_ACTION)
        if play_pause_actions:
            await self._call_action_list(CONF_PLAY_PAUSE_ACTION)
        else:
            # Fallback: try to determine current state and toggle appropriately
            if self.state == MediaPlayerState.PLAYING:
                await self.async_media_pause()
            else:
                await self.async_media_play()

    async def async_media_seek(self, position: float) -> None:
        """Send seek command."""
        seek_actions = self._actions.get(CONF_SEEK_ACTION)
        pos_pct = (
            (100 * position) / self._attr_media_duration
            if self._attr_media_duration
            else None
        )
        if pos_pct is not None:
            position = pos_pct
        if seek_actions:
            template_vars = {"position": position, "seek_position": position}
            await self._call_action_list(CONF_SEEK_ACTION, template_vars)

    @property
    def media_position(self) -> int | None:
        """Return current position of media in seconds."""
        if self._attr_media_position is not None:
            return int(self._attr_media_position)
        return None

    @property
    def media_duration(self) -> int | None:
        """Return duration of media in seconds."""
        if self._attr_media_duration is not None:
            return int(self._attr_media_duration)
        return None

    @property
    def media_position_updated_at(self) -> float | None:
        """Return the timestamp of when the position was last updated."""
        return self._attr_media_position_updated_at

    async def async_play_media(
        self,
        media_type: str,
        media_id: str,
        enqueue: MediaPlayerEnqueue | None = None,
        announce: bool | None = None,
        **kwargs: Any,
    ) -> None:
        """Play a piece of media using templated actions."""
        template_vars = {
            "media_type": media_type,
            "media_id": media_id,
            "media_content_type": media_type,
            "media_content_id": media_id,
            "enqueue": enqueue,
            "announce": announce,
            **kwargs,
        }
        await self._call_action_list(CONF_PLAY_MEDIA_ACTION, template_vars)

    async def async_clear_playlist(self) -> None:
        """Clear players playlist."""
        await self._call_action_list(CONF_CLEAR_PLAYLIST_ACTION)

    async def async_set_shuffle(self, shuffle: bool) -> None:
        """Enable/disable shuffle mode."""
        template_vars = {"shuffle": shuffle}
        await self._call_action_list(CONF_SHUFFLE_SET_ACTION, template_vars)

    async def async_set_repeat(self, repeat: str) -> None:
        """Set repeat mode."""
        template_vars = {"repeat": repeat}
        await self._call_action_list(CONF_REPEAT_SET_ACTION, template_vars)

    async def _call_action_list(
        self, action_key: str, template_vars: dict[str, Any] | None = None
    ) -> None:
        """Call all actions configured for the given action key."""
        actions_list = self._actions.get(action_key)
        if not actions_list:
            _LOGGER.debug("No actions configured for %s", action_key)
            return

        if not isinstance(actions_list, list):
            _LOGGER.warning(
                "Actions for %s are not a list: %s", action_key, actions_list
            )
            return

        if template_vars is None:
            template_vars = {}

        def has_template(value: Any) -> bool:
            """Check if a value contains a template."""
            if isinstance(value, str):
                return "{{" in value or "{%" in value
            return False

        for action_config in actions_list:
            if not isinstance(action_config, dict):
                _LOGGER.warning(
                    "Skipping invalid action config (not a dict): %s", action_config
                )
                continue

            try:
                # Create a deep copy to avoid modifying the original config
                config_copy = deepcopy(action_config)

                # Check if we have data with a value that might need templating
                if "data" in config_copy and isinstance(config_copy["data"], dict):
                    data_dict = config_copy["data"]

                    # Check each value in data for templates
                    for key, value in list(data_dict.items()):
                        if has_template(value):
                            data_dict[key] = Template(value, self.hass)

                # Use Home Assistant's service calling helper with the copied config
                await async_call_from_config(
                    self.hass,
                    config_copy,
                    variables=template_vars,
                    blocking=False,
                    validate_config=False,
                )

                _LOGGER.debug(
                    "Called action %s with config: %s", action_key, config_copy
                )
            except Exception as ex:
                _LOGGER.error(
                    "Error calling action %s with config %s: %s",
                    action_key,
                    action_config,
                    ex,
                )

    async def _call_service_action(self, action_config: dict[str, Any]) -> None:
        """Call service with provided configuration - deprecated, use _call_action_list."""
        _LOGGER.warning(
            "_call_service_action is deprecated, use _call_action_list instead"
        )

        if not isinstance(action_config, dict):
            _LOGGER.warning("Invalid action config (not a dict): %s", action_config)
            return

        try:
            await async_call_from_config(
                self.hass,
                action_config,
                variables={},
                blocking=False,
                validate_config=False,
            )
        except Exception as ex:
            _LOGGER.error("Error calling service action %s: %s", action_config, ex)

    def _has_active_media(self) -> bool:
        """Check if we have active media info indicating playback."""
        # Check if we have meaningful media info
        title_entity = self._entity_refs.get(CONF_MEDIA_TITLE_ENTITY)
        artist_entity = self._entity_refs.get(CONF_MEDIA_ARTIST_ENTITY)

        if title_entity:
            title_state = self.hass.states.get(title_entity)
            if title_state and title_state.state not in (
                None,
                "unknown",
                "unavailable",
                "",
            ):
                return True

        if artist_entity:
            artist_state = self.hass.states.get(artist_entity)
            if artist_state and artist_state.state not in (
                None,
                "unknown",
                "unavailable",
                "",
            ):
                return True

        # Check if position is updating (indicates active playback)
        position_entity = self._entity_refs.get(CONF_MEDIA_POSITION_ENTITY)
        if position_entity:
            state = self.hass.states.get(position_entity)
            if state and state.state not in (None, "unknown", "unavailable"):
                try:
                    new_position = float(state.state)
                    if new_position > 0:
                        return True
                except (ValueError, TypeError):
                    pass

        return False

    async def async_browse_media(
        self, media_content_type: str | None = None, media_content_id: str | None = None
    ) -> BrowseMedia:
        """Implement the websocket media browsing helper."""
        # If your media player has no own media sources to browse, route all browse commands
        # to the media source integration.
        return await media_source.async_browse_media(self.hass, media_content_id)
