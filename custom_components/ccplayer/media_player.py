"""Media player platform for CC Player."""

import json
import logging

from typing import Any
from copy import deepcopy

from homeassistant.components import media_source
from homeassistant.components import mqtt
from homeassistant.components.media_player import (
    MediaPlayerDeviceClass,
    MediaPlayerEnqueue,
    MediaPlayerEntity,
    MediaPlayerEntityFeature,
    MediaPlayerState,
    MediaClass,
    MediaType,
)

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import (
    ATTR_ENTITY_ID,
    CONF_NAME,
    SERVICE_TURN_OFF,
    SERVICE_TURN_ON,
    STATE_OFF,
    STATE_ON,
)
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_state_change_event
from homeassistant.helpers.template import Template
from homeassistant.helpers.service import async_call_from_config
from homeassistant import util
from homeassistant.components.media_player.browse_media import (  # noqa: F401
    BrowseMedia,
    SearchMedia,
    SearchMediaQuery,
    async_process_play_media_url,
)
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
    DEVICE_MANUFACTURER,
    DEVICE_MODEL,
    DEVICE_NAME_DEFAULT,
    DEVICE_SW_VERSION,
    DOMAIN,
)

_LOGGER = logging.getLogger(__name__)

# State mapping for player state entity
PLAYER_STATE_MAP = {
    "playing": MediaPlayerState.PLAYING,
    "play": MediaPlayerState.PLAYING,
    "paused": MediaPlayerState.PAUSED,
    "pause": MediaPlayerState.PAUSED,
    "stopped": MediaPlayerState.IDLE,
    "stop": MediaPlayerState.IDLE,
    "idle": MediaPlayerState.IDLE,
    "buffering": MediaPlayerState.BUFFERING,
    "on": MediaPlayerState.ON,
    "true": MediaPlayerState.ON,
    "off": MediaPlayerState.OFF,
    "false": MediaPlayerState.OFF,
}


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
        self._device_id = config_entry.data.get("device_id")  # Use device_id from config

        # Entity references and actions
        self._entity_refs = {}
        self._actions = {}
        self._unsubscribe_callbacks = []

        # Playlists from MQTT
        self._playlists = []  # Store playlists from MQTT
        self._mqtt_unsub = None  # MQTT unsubscribe handle
        self._mediaqueue = []  # Store mediaqueue playlist from MQTT

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

        # Subscribe to MQTT playlists topic
        await self._subscribe_playlists_mqtt()
        await self._subscribe_mediaqueue_mqtt()  # <-- add this line

    async def async_will_remove_from_hass(self) -> None:
        """Clean up when entity is removed from hass."""
        for unsub in self._unsubscribe_callbacks:
            unsub()
        self._unsubscribe_callbacks = []

        # Unsubscribe from MQTT
        if self._mqtt_unsub:
            self._mqtt_unsub()
            self._mqtt_unsub = None
        if hasattr(self, "_mediaqueue_mqtt_unsub") and self._mediaqueue_mqtt_unsub:
            self._mediaqueue_mqtt_unsub()
            self._mediaqueue_mqtt_unsub = None

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

    async def _subscribe_playlists_mqtt(self):
        """Subscribe to the MQTT playlists/available topic."""
        base_topic = self._device_id or "ccplayer"
        playlists_topic = f"yan/{base_topic}/status/playlists/available"
        _LOGGER.debug("Subscribing to MQTT playlists topic: %s", playlists_topic)
        print(f"DEBUG: Subscribing to MQTT playlists topic: {playlists_topic}")

        @callback
        def handle_mqtt_message(msg):
            try:
                payload = json.loads(msg.payload)
                self._playlists = payload.get("playlists", [])
                self.async_write_ha_state()
            except Exception as ex:
                _LOGGER.error("Failed to parse playlists MQTT payload: %s", ex)
                print(f"DEBUG: Failed to parse playlists MQTT payload: {ex}")

        self._mqtt_unsub = await mqtt.async_subscribe(
            self.hass, playlists_topic, handle_mqtt_message, 1
        )
        _LOGGER.debug("MQTT subscription set up for: %s", playlists_topic)
        print(f"DEBUG: MQTT subscription set up for: {playlists_topic}")

    async def _subscribe_mediaqueue_mqtt(self):
        """Subscribe to the MQTT mediaqueue topic for sources."""
        base_topic = self._device_id or "ccplayer"
        mediaqueue_topic = f"yan/{base_topic}/status/media_queue"
        _LOGGER.debug("Subscribing to MQTT mediaqueue topic: %s", mediaqueue_topic)
        print(f"DEBUG: Subscribing to MQTT mediaqueue topic: {mediaqueue_topic}")

        @callback
        def handle_mediaqueue_message(msg):

            try:
                payload = json.loads(msg.payload)
                self._mediaqueue = payload.get("playlist", [])
                self.async_write_ha_state()
            except Exception as ex:
                _LOGGER.error("Failed to parse mediaqueue MQTT payload: %s", ex)
                print(f"DEBUG: Failed to parse mediaqueue MQTT payload: {ex}")

        self._mediaqueue_mqtt_unsub = await mqtt.async_subscribe(
            self.hass, mediaqueue_topic, handle_mediaqueue_message, 1
        )
        _LOGGER.debug("MQTT subscription set up for: %s", mediaqueue_topic)
        print(f"DEBUG: MQTT subscription set up for: {mediaqueue_topic}")

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

    def _get_entity_state_value(self, entity_id: str, default=None):
        """Get entity state value, handling common invalid states."""
        if not entity_id:
            return default

        state = self.hass.states.get(entity_id)
        if not state or state.state in (None, "unknown", "unavailable", ""):
            return default
        return state.state

    def _get_numeric_state_value(self, entity_id: str, default=None):
        """Get numeric entity state value with error handling."""
        value = self._get_entity_state_value(entity_id)
        if value is None:
            return default

        try:
            return float(value)
        except (ValueError, TypeError):
            _LOGGER.warning("Could not convert %s state to float: %s", entity_id, value)
            return default

    def _parse_list_from_state(self, entity_id: str, default=None):
        """Parse a list from entity state (JSON or comma-separated)."""
        value = self._get_entity_state_value(entity_id)
        if not value:
            return default

        try:
            return json.loads(value)
        except (ValueError, TypeError):
            # Try comma-separated fallback
            return [s.strip() for s in value.split(",") if s.strip()]

    def _get_volume_range(self) -> tuple[float, float]:
        """Get volume entity's min and max values from its attributes."""
        volume_entity = self._entity_refs.get(CONF_VOLUME_ENTITY)
        if not volume_entity:
            return 0.0, 1.0  # Default range

        state = self.hass.states.get(volume_entity)
        if not state:
            return 0.0, 1.0

        # Try to get min/max from entity attributes
        min_value = state.attributes.get("min", 0.0)
        max_value = state.attributes.get("max", 1.0)

        try:
            return float(min_value), float(max_value)
        except (ValueError, TypeError):
            _LOGGER.warning(
                "Could not parse volume range for %s, using defaults", volume_entity
            )
            return 0.0, 1.0

    def _normalize_volume_to_entity_range(self, volume_level: float) -> float:
        """Convert Home Assistant volume level (0-1) to entity's range."""
        min_val, max_val = self._get_volume_range()
        return min_val + (volume_level * (max_val - min_val))

    def _normalize_volume_from_entity_range(self, entity_value: float) -> float:
        """Convert entity's volume value to Home Assistant range (0-1)."""
        min_val, max_val = self._get_volume_range()
        if max_val == min_val:
            return 0.0
        return (entity_value - min_val) / (max_val - min_val)

    async def _refresh_states(self) -> None:
        """Refresh all entity states."""
        current_state = self._determine_player_state()

        # Volume - use dynamic range from entity
        volume_value = self._get_numeric_state_value(
            self._entity_refs.get(CONF_VOLUME_ENTITY)
        )
        if volume_value is not None:
            self._attr_volume_level = self._normalize_volume_from_entity_range(
                volume_value
            )
        else:
            self._attr_volume_level = None

        # Mute
        mute_state = self._get_entity_state_value(
            self._entity_refs.get(CONF_MUTE_ENTITY)
        )
        self._attr_is_volume_muted = mute_state == STATE_ON if mute_state else None

        # Source
        self._attr_source = self._get_entity_state_value(
            self._entity_refs.get(CONF_SOURCE_ENTITY)
        )

        # Source list from source entity attributes or separate entity
        source_entity = self._entity_refs.get(CONF_SOURCE_ENTITY)
        if source_entity:
            state = self.hass.states.get(source_entity)
            if state and "options" in state.attributes:
                self._attr_source_list = state.attributes["options"]

        if not self._attr_source_list:
            self._attr_source_list = self._parse_list_from_state(
                self._entity_refs.get(CONF_SOURCE_LIST_ENTITY)
            )

        # Media info
        self._refresh_media_info()

        # Update final state
        self._attr_state = current_state
        self.async_write_ha_state()

    def _determine_player_state(self) -> MediaPlayerState | None:
        """Determine player state from configured entities."""
        # Try player state entity first
        player_state_entity = self._entity_refs.get(CONF_PLAYER_STATE_ENTITY)
        if player_state_entity:
            state_value = self._get_entity_state_value(player_state_entity)
            if state_value:
                mapped_state = PLAYER_STATE_MAP.get(state_value.lower())
                if mapped_state:
                    return mapped_state

                # For unknown states, try to infer from media info
                if self._has_active_media():
                    return MediaPlayerState.PLAYING
                return MediaPlayerState.IDLE

        # Fallback to power entity
        power_entity = self._entity_refs.get(CONF_POWER_ENTITY)
        if power_entity:
            power_state = self._get_entity_state_value(power_entity)
            if power_state == STATE_ON:
                return (
                    MediaPlayerState.PLAYING
                    if self._has_active_media()
                    else MediaPlayerState.ON
                )
            elif power_state == STATE_OFF:
                return MediaPlayerState.OFF

        return None

    def _refresh_media_info(self) -> None:
        """Refresh media information from configured entities."""
        # Text media info
        media_fields = [
            ("_attr_media_title", CONF_MEDIA_TITLE_ENTITY),
            ("_attr_media_artist", CONF_MEDIA_ARTIST_ENTITY),
            ("_attr_media_album_name", CONF_MEDIA_ALBUM_ENTITY),
        ]

        for attr_name, entity_key in media_fields:
            entity_id = self._entity_refs.get(entity_key)
            value = self._get_entity_state_value(entity_id)
            setattr(self, attr_name, value)

        # Media duration (convert from milliseconds)
        duration_ms = self._get_numeric_state_value(
            self._entity_refs.get(CONF_MEDIA_DURATION_ENTITY)
        )
        self._attr_media_duration = (
            duration_ms / 1000 if duration_ms is not None else None
        )

        # Media position with timestamp tracking
        position_ms = self._get_numeric_state_value(
            self._entity_refs.get(CONF_MEDIA_POSITION_ENTITY)
        )
        if position_ms is not None:
            self._attr_media_position = position_ms / 1000
            self._attr_media_position_updated_at = util.dt.utcnow()
        else:
            self._attr_media_position = None
            self._attr_media_position_updated_at = None

        # Media image
        self._refresh_media_image()

    def _refresh_media_image(self) -> None:
        """Refresh media image from image entity."""
        image_entity = self._entity_refs.get(CONF_MEDIA_IMAGE_ENTITY)
        if not image_entity:
            self._attr_media_image_url = None
            return

        state = self.hass.states.get(image_entity)
        if not state:
            self._attr_media_image_url = None
            return

        # Try entity_picture attribute first
        if "entity_picture" in state.attributes:
            self._attr_media_image_url = state.attributes["entity_picture"]
        elif state.state and state.state.startswith(("http://", "https://", "/local/")):
            self._attr_media_image_url = state.state
        else:
            self._attr_media_image_url = None

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

        # Always add PLAY_MEDIA so Home Assistant knows we support play_media
        features |= MediaPlayerEntityFeature.PLAY_MEDIA

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

        # Re-subscribe to MQTT if topic changed
        if self._mqtt_unsub:
            self._mqtt_unsub()
            self._mqtt_unsub = None
        await self._subscribe_playlists_mqtt()
        if hasattr(self, "_mediaqueue_mqtt_unsub") and self._mediaqueue_mqtt_unsub:
            self._mediaqueue_mqtt_unsub()
            self._mediaqueue_mqtt_unsub = None
        await self._subscribe_mediaqueue_mqtt()

    @property
    def device_info(self):
        """Return device information."""
        # Use the selected device's identifier if present, otherwise fallback to our own
        linked_identifier = self._config_entry.data.get("linked_device_identifier")
        if linked_identifier:
            # linked_identifier is a tuple (domain, id)
            return {
                "identifiers": {tuple(linked_identifier)},
                "name": self._attr_name or DEVICE_NAME_DEFAULT,
                "manufacturer": DEVICE_MANUFACTURER,
                "model": DEVICE_MODEL,
                "sw_version": DEVICE_SW_VERSION,
            }
        return {
            "identifiers": {(DOMAIN, self._config_entry.entry_id)},
            "name": self._attr_name or DEVICE_NAME_DEFAULT,
            "manufacturer": DEVICE_MANUFACTURER,
            "model": DEVICE_MODEL,
            "sw_version": DEVICE_SW_VERSION,
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

    async def _set_volume_entity_value(self, value: float) -> None:
        """Set volume entity value with proper service call."""
        volume_entity = self._entity_refs.get(CONF_VOLUME_ENTITY)
        if not volume_entity:
            return

        domain = volume_entity.split(".", 1)[0]
        service = "set_value"

        # Ensure value is within entity's range
        min_val, max_val = self._get_volume_range()
        clamped_value = max(min_val, min(max_val, value))

        try:
            await self.hass.services.async_call(
                domain, service, {ATTR_ENTITY_ID: volume_entity, "value": clamped_value}
            )
        except Exception as ex:
            _LOGGER.error("Failed to set volume for %s: %s", volume_entity, ex)

    async def async_volume_up(self) -> None:
        """Turn volume up."""
        current_value = self._get_numeric_state_value(
            self._entity_refs.get(CONF_VOLUME_ENTITY)
        )
        if current_value is not None:
            min_val, max_val = self._get_volume_range()
            step = self._config_entry.options.get(CONF_VOLUME_STEP, 0.05) * (
                max_val - min_val
            )
            new_value = min(max_val, current_value + step)
            await self._set_volume_entity_value(new_value)

    async def async_volume_down(self) -> None:
        """Turn volume down."""
        current_value = self._get_numeric_state_value(
            self._entity_refs.get(CONF_VOLUME_ENTITY)
        )
        if current_value is not None:
            min_val, max_val = self._get_volume_range()
            step = self._config_entry.options.get(CONF_VOLUME_STEP, 0.05) * (
                max_val - min_val
            )
            new_value = max(min_val, current_value - step)
            await self._set_volume_entity_value(new_value)

    async def async_set_volume_level(self, volume: float) -> None:
        """Set volume level, range 0..1."""
        entity_value = self._normalize_volume_to_entity_range(volume)
        await self._set_volume_entity_value(volume)

    async def async_mute_volume(self, mute: bool) -> None:
        """Mute or unmute media player."""
        if mute_entity := self._entity_refs.get(CONF_MUTE_ENTITY):
            service = SERVICE_TURN_ON if mute else SERVICE_TURN_OFF
            await self.hass.services.async_call(
                "homeassistant", service, {ATTR_ENTITY_ID: mute_entity}
            )

    async def async_select_source(self, source: str) -> None:
        """Select input source."""
        source_entity = self._entity_refs.get(CONF_SOURCE_ENTITY)
        if not source_entity:
            return

        domain = source_entity.split(".", 1)[0]
        service = "select_option"

        try:
            await self.hass.services.async_call(
                domain, service, {ATTR_ENTITY_ID: source_entity, "option": source}
            )
        except Exception as ex:
            _LOGGER.error("Failed to select source %s: %s", source, ex)

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
        if not seek_actions:
            return

        # Calculate position as percentage if duration is available
        seek_position = position
        if self._attr_media_duration and self._attr_media_duration > 0:
            seek_position = (position / self._attr_media_duration) * 100

        template_vars = {
            "position": position,
            "seek_position": seek_position,
            "position_pct": seek_position,
        }
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

        print(f"DEBUG: async_play_media called with media_type={media_type}, media_id={media_id}, enqueue={enqueue}, announce={announce}, kwargs={kwargs}")
        # Handle playlist selection
        if media_id and media_id.startswith("playlist:"):
            playlist_index = media_id.split(":", 1)[1]
            playlist_name = None
            for playlist in self._playlists:
                if str(playlist.get("index")) == str(playlist_index):
                    playlist_name = playlist.get("title")
                    break
            if playlist_name:
                topic = f"yan/{self._device_id}/command/media_load_playlist"
                payload = json.dumps({"playlist": playlist_name})
                _LOGGER.debug("async_play_media: Publishing playlist load: topic=%s payload=%s", topic, payload)
                await mqtt.async_publish(self.hass, topic, payload, 1, False)
            else:
                _LOGGER.warning("async_play_media: Playlist with index %s not found", playlist_index)
            return

        # Handle source selection
        if media_id and media_id.startswith("source:"):
            source = media_id.split(":", 1)[1]
            if source:
                    topic = f"yan/{self._device_id}/command/media_play_from_queue"
                    payload = json.dumps({"title": source})
                    _LOGGER.debug("async_play_media: Publishing playlist load: topic=%s payload=%s", topic, payload)
                    await mqtt.async_publish(self.hass, topic, payload, 1, False)
            else:
                _LOGGER.warning("async_play_media: Playlist with index %s not found", playlist_index)
            return

        # Handle Home Assistant media sources
        if media_source.is_media_source_id(media_id):
            play_item = await media_source.async_resolve_media(
                self.hass, media_id, self.entity_id
            )
            media_id = async_process_play_media_url(self.hass, play_item.url)

        # If play_media action is configured, use it
        if self._actions.get(CONF_PLAY_MEDIA_ACTION):
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
            return

        # Otherwise, publish MQTT for normal URLs
        if media_id and (media_id.startswith("http://") or media_id.startswith("https://")):
            topic = f"yan/{self._device_id}/command/media_load_url"
            payload = json.dumps({"url": media_id})
            _LOGGER.debug("async_play_media: Publishing media_load_url: topic=%s payload=%s", topic, payload)
            await mqtt.async_publish(self.hass, topic, payload, 1, False)
            return

        _LOGGER.warning("async_play_media: No handler for media_id: %s", media_id)

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
        # Check for meaningful media title or artist
        if self._get_entity_state_value(self._entity_refs.get(CONF_MEDIA_TITLE_ENTITY)):
            return True
        if self._get_entity_state_value(
            self._entity_refs.get(CONF_MEDIA_ARTIST_ENTITY)
        ):
            return True

        # Check if position is greater than 0 (indicates active playback)
        position = self._get_numeric_state_value(
            self._entity_refs.get(CONF_MEDIA_POSITION_ENTITY)
        )
        return position is not None and position > 0

    async def async_browse_media(
        self, media_content_type: str | None = None, media_content_id: str | None = None
    ) -> BrowseMedia:
   

        if(self._playlists.count == 0):
            topic = f"yan/{self._device_id}/command/media_get_playlists"
            await mqtt.async_publish(self.hass, topic, "", 1, False)

        _LOGGER.debug("Browsing media: type=%s, id=%s, playlists=%s", media_content_type, media_content_id, self._playlists)
        print(f"DEBUG: Browsing media: type={media_content_type}, id={media_content_id}, playlists={self._playlists}")

        # Root: show three directories: Media Sources, Sources, Playlists
        if not media_content_id or media_content_id == "media_player":
            children = [
                BrowseMedia(
                    title="Media Sources",
                    media_class="directory",
                    media_content_id="ha_media_source",
                    media_content_type="directory",
                    can_play=False,
                    can_expand=True,
                    children=[],
                ),
                BrowseMedia(
                    title="Sources",
                    media_class=MediaClass.DIRECTORY,
                    media_content_id="ccplayer_sources",
                    media_content_type=MediaType.VIDEO,
                    can_play=False,
                    can_expand=True,
                    children=[],
                ),
                BrowseMedia(
                    title="Playlists",
                    media_class="directory",
                    media_content_id="ccplayer_playlists",
                    media_content_type="directory",
                    can_play=False,
                    can_expand=True,
                    children=[],
                ),
            ]
            return BrowseMedia(
                title="Media",
                media_class="directory",
                media_content_id="media_player",
                media_content_type="directory",
                can_play=False,
                can_expand=True,
                children=children,
            )

        # Expand Home Assistant's default media sources
        if media_content_id == "ha_media_source":
            return await media_source.async_browse_media(self.hass, None)

        # Expand your custom sources
        if media_content_id == "ccplayer_sources":
            children = []
            # Use _mediaqueue instead of _attr_source_list
            for item in self._mediaqueue:
                title = item.get("title", f"Item {item.get('index', '')}")
                media_id = item.get("mediaId")
                thumbnail = item.get("thumbnail")

                print(
                    f"DEBUG: Processing mediaqueue item: title={title}, media_id={media_id}, thumbnail={thumbnail}"
                )

                # Ensure thumbnail URL is properly formatted
                if thumbnail and not thumbnail.startswith(("http://", "https://")):
                    if thumbnail.startswith("/"):
                        # Make relative URLs absolute
                        base_url = (
                            self.hass.config.external_url
                            or self.hass.config.internal_url
                            or "http://localhost:8123"
                        )
                        thumbnail = f"{base_url}{thumbnail}"
                    else:
                        # If it doesn't start with / or http, prepend http://
                        thumbnail = f"http://{thumbnail}"

                if thumbnail:
                    print(f"DEBUG: Using thumbnail for {title}: {thumbnail}")

                # Create BrowseMedia object with proper thumbnail
                browse_item = BrowseMedia(
                    title=title,
                    media_class=MediaClass.VIDEO,
                    media_content_id=f"source:{title}",
                    media_content_type=MediaType.VIDEO,
                    can_play=True,
                    can_expand=False,
                    thumbnail=thumbnail,
                    children=[],
                )

                children.append(browse_item)

            _LOGGER.debug("Created %d media items for ccplayer_sources", len(children))
            print(f"DEBUG: Created {len(children)} media items for ccplayer_sources")

            return BrowseMedia(
                title="Sources",
                media_class="directory",
                media_content_id="ccplayer_sources",
                media_content_type="directory",
                can_play=False,
                can_expand=True,
                children=children,
            )

        # Expand your playlists
        if media_content_id == "ccplayer_playlists":
            children = []
            for playlist in self._playlists:
                name = playlist.get("title") or playlist.get("name") or ""
                # Remove trailing .json if present
                if name.endswith(".json"):
                    name = name[:-5]
                # Append description if available
                description = playlist.get("description")
                if description:
                    name = f"{name} ({description})"
                index = playlist.get("index")
                thumbnail = playlist.get("thumbnail", None)
                children.append(
                    BrowseMedia(
                        title=name,
                        media_class="playlist",
                        media_content_id=f"playlist:{index}",
                        media_content_type="playlist",
                        can_play=True,
                        can_expand=False,
                        thumbnail=thumbnail,
                        children=[],
                    )
                )
            return BrowseMedia(
                title="Playlists",
                media_class="directory",
                media_content_id="ccplayer_playlists",
                media_content_type="directory",
                can_play=False,
                can_expand=True,
                children=children,
            )

        # Otherwise, fallback to media_source (for subfolders etc)
        return await media_source.async_browse_media(self.hass, media_content_id)
