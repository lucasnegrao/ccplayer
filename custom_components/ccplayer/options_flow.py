"""Options flow for CC Player."""

import logging
import traceback
import voluptuous as vol

from homeassistant.config_entries import OptionsFlow, ConfigEntry
from homeassistant.core import callback
from homeassistant.helpers import selector

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
    DEFAULT_VOLUME_STEP,
)

_LOGGER = logging.getLogger(__name__)

# Define action keys at the module level for reuse
_PLAYER_ACTION_KEYS = [
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
]

# Define lists of entity keys for options flow steps
_ENTITY_KEYS_BASIC = [
    CONF_POWER_ENTITY,
    CONF_PLAYER_STATE_ENTITY,
    CONF_VOLUME_ENTITY,
    CONF_MUTE_ENTITY,
    CONF_SOURCE_ENTITY,
    CONF_SOURCE_LIST_ENTITY,
]

_ENTITY_KEYS_MEDIA_INFO = [
    CONF_MEDIA_TITLE_ENTITY,
    CONF_MEDIA_ARTIST_ENTITY,
    CONF_MEDIA_ALBUM_ENTITY,
    CONF_MEDIA_IMAGE_ENTITY,
    CONF_MEDIA_POSITION_ENTITY,
    CONF_MEDIA_DURATION_ENTITY,
]


class CCPlayerOptionsFlow(OptionsFlow):
    """Handle options."""

    def __init__(self, config_entry: ConfigEntry) -> None:
        """Initialize options flow."""
        self.options = dict(config_entry.options)
        if not isinstance(self.options.get(CONF_ACTIONS), dict):
            self.options[CONF_ACTIONS] = {}

    def _add_entity_selector_to_schema(
        self,
        schema_fields: dict,
        key: str,
        domains: list[str] | str,
    ):
        """Adds an entity selector field to the schema dictionary."""
        current_value = self.options.get(key)
        selector_config = selector.EntitySelectorConfig(domain=domains, multiple=False)
        if current_value:
            schema_fields[
                vol.Optional(key, default=current_value)
            ] = selector.EntitySelector(selector_config)
        else:
            schema_fields[vol.Optional(key)] = selector.EntitySelector(selector_config)

    def _get_basic_options_schema(self) -> vol.Schema:
        """Return schema for basic options."""
        schema_fields = {}
        self._add_entity_selector_to_schema(schema_fields, CONF_POWER_ENTITY, ["switch", "input_boolean"])
        self._add_entity_selector_to_schema(schema_fields, CONF_PLAYER_STATE_ENTITY, ["sensor", "input_select"])
        self._add_entity_selector_to_schema(schema_fields, CONF_VOLUME_ENTITY, ["input_number", "number"])
        self._add_entity_selector_to_schema(schema_fields, CONF_MUTE_ENTITY, ["switch", "input_boolean"])
        self._add_entity_selector_to_schema(schema_fields, CONF_SOURCE_ENTITY, ["input_select", "select"])
        self._add_entity_selector_to_schema(schema_fields, CONF_SOURCE_LIST_ENTITY, ["sensor", "input_text"])
        
        schema_fields[
            vol.Optional(
                CONF_VOLUME_STEP,
                default=self.options.get(CONF_VOLUME_STEP, DEFAULT_VOLUME_STEP),
            )
        ] = vol.Coerce(float)
        return vol.Schema(schema_fields)

    def _get_media_info_options_schema(self) -> vol.Schema:
        """Return schema for media info options."""
        schema_fields = {}
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_TITLE_ENTITY, ["sensor", "input_text"])
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_ARTIST_ENTITY, ["sensor", "input_text"])
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_ALBUM_ENTITY, ["sensor", "input_text"])
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_IMAGE_ENTITY, "image")
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_POSITION_ENTITY, ["sensor", "input_number"])
        self._add_entity_selector_to_schema(schema_fields, CONF_MEDIA_DURATION_ENTITY, ["sensor", "input_number"])
        return vol.Schema(schema_fields)

    def _get_actions_options_schema(self) -> vol.Schema:
        """Return schema for actions options."""
        schema_fields = {}
        all_player_actions = self.options.get(CONF_ACTIONS)
        if not isinstance(all_player_actions, dict):
            all_player_actions = {}

        for action_key in _PLAYER_ACTION_KEYS:
            default_value_for_selector = all_player_actions.get(action_key)
            if default_value_for_selector is not None and isinstance(default_value_for_selector, list):
                schema_fields[
                    vol.Optional(action_key, default=default_value_for_selector)
                ] = selector.ActionSelector()
            else:
                if default_value_for_selector is not None:
                    _LOGGER.warning(
                        "Invalid stored action for %s (expected list, got %s): %s. Will show as unset.",
                        action_key,
                        type(default_value_for_selector).__name__,
                        default_value_for_selector,
                    )
                schema_fields[vol.Optional(action_key)] = selector.ActionSelector()
        return vol.Schema(schema_fields)

    async def async_step_init(self, user_input=None):
        """Manage the options - initial step."""
        # Directly start with the first configuration step (basic controls)
        return await self.async_step_basic()

    async def async_step_basic(self, user_input=None):
        """Configure basic controls."""
        if user_input is not None:
            # Process the form data regardless of navigation
            for key in _ENTITY_KEYS_BASIC:
                if key in user_input:
                    value = user_input[key]
                    if isinstance(value, str) and value.strip():
                        self.options[key] = value.strip()
                    else:
                        self.options.pop(key, None)
            if CONF_VOLUME_STEP in user_input:
                self.options[CONF_VOLUME_STEP] = user_input[CONF_VOLUME_STEP]
            # Proceed to the next step: media_info
            return await self.async_step_media_info()

        schema = self._get_basic_options_schema()
        
        return self.async_show_form(
            step_id="basic", 
            data_schema=schema,
            last_step=False  # This enables Next button instead of Submit
        )

    async def async_step_media_info(self, user_input=None):
        """Configure media information entities."""
        if user_input is not None:
            # Process the form data regardless of navigation
            for key in _ENTITY_KEYS_MEDIA_INFO:
                if key in user_input:
                    value = user_input[key]
                    if isinstance(value, str) and value.strip():
                        self.options[key] = value.strip()
                    else:
                        self.options.pop(key, None)
            # Proceed to the next step: actions
            return await self.async_step_actions()

        return self.async_show_form(
            step_id="media_info", 
            data_schema=self._get_media_info_options_schema(),
            last_step=False  # This enables Next/Back buttons
        )

    async def async_step_actions(self, user_input=None):
        """Configure media player actions."""
        if user_input is not None:
            _LOGGER.debug("Raw user input received for actions: %s", user_input)
            current_actions_dict = self.options.get(CONF_ACTIONS, {}).copy()
            if not isinstance(current_actions_dict, dict):
                _LOGGER.warning("%s in options was not a dict, resetting. Value: %s", CONF_ACTIONS, current_actions_dict)
                current_actions_dict = {}

            for action_key in _PLAYER_ACTION_KEYS:
                if action_key in user_input:
                    action_data_list = user_input[action_key]
                    if isinstance(action_data_list, list):
                        current_actions_dict[action_key] = action_data_list
                    else:
                        _LOGGER.warning("Action data for %s is not a list: %s. Clearing to empty list.", action_key, action_data_list)
                        current_actions_dict[action_key] = []
            
            self.options[CONF_ACTIONS] = current_actions_dict
            _LOGGER.debug("Final actions configuration: %s", self.options.get(CONF_ACTIONS))
            # This is the last step, save the options and finish.
            return self.async_create_entry(title="", data=self.options)

        return self.async_show_form(
            step_id="actions",
            data_schema=self._get_actions_options_schema(),
            last_step=True  # This shows Back and Submit buttons
        )
