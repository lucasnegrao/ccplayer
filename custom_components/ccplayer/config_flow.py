"""Config flow for CC Player."""

import asyncio
import logging
import traceback
import json

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.components import mqtt
from homeassistant.components.media_player import MediaPlayerEntityFeature
from homeassistant.config_entries import ConfigEntry, ConfigFlow, OptionsFlow
from homeassistant.const import CONF_NAME
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import entity_registry as er, selector
from homeassistant.helpers.device_registry import async_get as async_get_device_registry
from homeassistant.helpers.entity_registry import async_get as async_get_entity_registry
from homeassistant.helpers.service_info.mqtt import MqttServiceInfo
from homeassistant.config_entries import ConfigFlow, ConfigFlowResult

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
    DEFAULT_VOLUME_STEP,
    DEFAULT_PREFIX,
    DOMAIN,
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


class CCPlayerConfigFlow(ConfigFlow, domain=DOMAIN):
    """Handle a config flow for CC Player."""

    def __init__(self) -> None:
        """Initialize flow."""
        self._prefix = DEFAULT_PREFIX

    VERSION = 1

    async def async_step_mqtt(
        self, discovery_info: MqttServiceInfo
    ) -> ConfigFlowResult:
        """Handle a flow initialized by MQTT discovery."""
        if self._async_in_progress() or self._async_current_entries():
            return self.async_abort(reason="single_instance_allowed")

        await self.async_set_unique_id(DOMAIN)

        payload_holder = discovery_info.payload
        data = json.loads(payload_holder)
        if "deviceUniqueID" in data:
            discovered_id = data["deviceUniqueID"]
            self._prefix = discovered_id
            return await self.async_step_confirm()

        return await self.async_step_confirm()

    async def async_step_confirm(self, user_input=None):
        """Handle the confirm step."""
        errors = {}
        if user_input is not None:
            if self._prefix:
                # Try to find a matching device in the registry
                device_registry = async_get_device_registry(self.hass)
                matching_device = None
                for dev in device_registry.devices.values():
                    for ident in dev.identifiers:
                        if self._prefix in ident:
                            matching_device = dev
                            break

                if matching_device:
                    # Pre-fill config flow with this device
                    return await self.async_step_user(
                        {
                            "name": matching_device.name or self._prefix,
                            "linked_device_id": matching_device.id,
                        }
                    )

        return self.async_show_form(step_id="confirm")

    async def async_step_user(self, user_input=None):
        """Handle the initial step."""
        errors = {}
        device_registry = async_get_device_registry(self.hass)
        entity_registry = async_get_entity_registry(self.hass)
        # Build a mapping of device_id -> (name, identifiers)
        devices = {
            dev.id: {
                "name": dev.name or dev.id,
                "identifiers": list(dev.identifiers)[0] if dev.identifiers else None,
            }
            for dev in device_registry.devices.values()
            if dev.identifiers
        }
        device_choices = {dev_id: info["name"] for dev_id, info in devices.items()}

        if user_input is not None:
            selected_device_id = user_input["linked_device_id"]
            selected_ident = devices[selected_device_id]["identifiers"]
            entities = [
                ent
                for ent in entity_registry.entities.values()
                if ent.device_id == selected_device_id
            ]

            # Extract the device fragment from the first entity_id
            def get_device_fragment(entities):
                for ent in entities:
                    parts = ent.entity_id.split(".")
                    if len(parts) == 2 and "_" in parts[1]:
                        # e.g. sensor.ccast_player_ent_96950033_playback_state
                        frag = "_".join(parts[1].split("_")[:4])
                        if frag.startswith("ccast_player_ent_"):
                            return frag
                return None

            dev_fragment = get_device_fragment(entities)
            # Fallback: try from device name
            if not dev_fragment:
                dev_name = devices[selected_device_id]["name"]
                dev_fragment = dev_name.lower().replace(" ", "_") if dev_name else ""

            # Helper to find entity_id by domain and suffix
            def find_entity(domain: str, suffix: str) -> str | None:
                for ent in entities:
                    if ent.domain == domain and ent.entity_id.endswith(suffix):
                        return ent.entity_id
                return None

            # Fill entity fields
            default_entities = {
                CONF_PLAYER_STATE_ENTITY: find_entity(
                    "sensor", f"{dev_fragment}_playback_state"
                )
                or "",
                CONF_MEDIA_TITLE_ENTITY: find_entity(
                    "sensor", f"{dev_fragment}_media_title"
                )
                or "",
                CONF_MEDIA_IMAGE_ENTITY: find_entity(
                    "image", f"{dev_fragment}_media_thumbnail"
                )
                or "",
                CONF_MEDIA_POSITION_ENTITY: find_entity(
                    "sensor", f"{dev_fragment}_media_position"
                )
                or "",
                CONF_MEDIA_DURATION_ENTITY: find_entity(
                    "sensor", f"{dev_fragment}_media_duration"
                )
                or "",
                CONF_MUTE_ENTITY: find_entity("switch", f"{dev_fragment}_mute")
                or find_entity("switch", f"{dev_fragment}_mute_control")
                or "",
                CONF_VOLUME_ENTITY: find_entity("number", f"{dev_fragment}_volume")
                or "",
            }

            # Fill actions (hardcoded except for device fragment)
            actions = {
                "play_action": [
                    {
                        "action": "button.press",
                        "data": {},
                        "metadata": {},
                        "target": {"entity_id": f"button.{dev_fragment}_play"},
                    }
                ],
                "pause_action": [
                    {
                        "action": "button.press",
                        "data": {},
                        "metadata": {},
                        "target": {"entity_id": f"button.{dev_fragment}_pause"},
                    }
                ],
                "stop_action": [
                    {
                        "action": "button.press",
                        "data": {},
                        "metadata": {},
                        "target": {"entity_id": f"button.{dev_fragment}_stop"},
                    }
                ],
                "seek_action": [
                    {
                        "action": "number.set_value",
                        "data": {"value": "{{seek_position}}"},
                        "metadata": {},
                        "target": {"entity_id": f"number.{dev_fragment}_media_seek"},
                    }
                ],
                "play_media_action": [
                    {
                        "action": "text.set_value",
                        "data": {"value": "{{media_id}}"},
                        "metadata": {},
                        "target": {"entity_id": f"text.{dev_fragment}_load_media_url"},
                    }
                ],
            }

            # Save device, identifiers, guessed entities, and actions as options
            options = {**default_entities, "actions": actions}
            return self.async_create_entry(
                title=user_input["name"],
                data={
                    "name": user_input["name"],
                    "linked_device_id": selected_device_id,
                    "linked_device_identifier": selected_ident,
                },
                options=options,
            )

        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Required("name", default=DEFAULT_NAME): str,
                    vol.Required("linked_device_id"): vol.In(device_choices),
                }
            ),
            errors=errors,
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        """Get the options flow for this handler."""
        return CCPlayerOptionsFlow(config_entry)


class CCPlayerOptionsFlow(OptionsFlow):
    """Handle options."""

    def __init__(self, config_entry: ConfigEntry) -> None:
        """Initialize options flow."""
        self.options = dict(config_entry.options)
        # Ensure CONF_ACTIONS is initialized if not present, and is a dict.
        # This helps ensure that self.options[CONF_ACTIONS] is always a dictionary.
        if not isinstance(self.options.get(CONF_ACTIONS), dict):
            self.options[CONF_ACTIONS] = {}
        self.current_action = None
        self.action_data = {}

    async def async_step_init(self, user_input=None):
        """Manage the options - initial menu."""
        errors = {}
        step_options = [
            {"label": "Basic Controls", "value": "basic"},
            {"label": "Media Information", "value": "media_info"},
            {"label": "Media Actions", "value": "actions"},
            {"label": "Save Configuration", "value": "finish"},
        ]

        _LOGGER.debug(
            "Options flow: entering async_step_init, user_input=%s", user_input
        )
        try:
            if user_input is not None:
                _LOGGER.debug(
                    "Options flow: submit clicked on step selection, user_input=%s",
                    user_input,
                )
                step = user_input.get("step")
                _LOGGER.debug("Options flow: extracted step=%s", step)
                if not step:
                    errors["step"] = "required"
                    _LOGGER.debug("Options flow: step missing, errors=%s", errors)
                else:
                    _LOGGER.debug("Options flow: proceeding to step=%s", step)
                    if step == "basic":
                        return await self.async_step_basic()
                    if step == "media_info":
                        return await self.async_step_media_info()
                    if step == "actions":
                        return await self.async_step_actions()
                    if step == "finish":
                        _LOGGER.debug(
                            "Options flow: finishing, options=%s", self.options
                        )
                        return self.async_create_entry(title="", data=self.options)

            _LOGGER.debug(
                "Options flow: showing step selection form, errors=%s", errors
            )
            return self.async_show_form(
                step_id="init",
                data_schema=vol.Schema(
                    {
                        vol.Optional("step"): selector.SelectSelector(
                            selector.SelectSelectorConfig(
                                options=step_options,
                                mode=selector.SelectSelectorMode.DROPDOWN,
                            )
                        ),
                    }
                ),
                errors=errors,
            )
        except Exception as exc:
            _LOGGER.error(
                "Options flow: Exception in async_step_init: %s\n%s",
                exc,
                traceback.format_exc(),
            )
            raise

    async def async_step_basic(self, user_input=None):
        """Configure basic controls."""
        if user_input is not None:
            # Remove empty entity fields before processing to avoid validation errors
            cleaned_input = {
                key: value
                for key, value in user_input.items()
                if key == CONF_VOLUME_STEP
                or (isinstance(value, str) and value.strip())
                or not isinstance(value, str)
            }

            # Update options with cleaned input
            self.options.update(cleaned_input)
            return await self.async_step_init()

        # Create schema with conditional fields based on existing values
        schema_fields = {}

        # Power entity
        if self.options.get(CONF_POWER_ENTITY):
            schema_fields[
                vol.Optional(CONF_POWER_ENTITY, default=self.options[CONF_POWER_ENTITY])
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["switch", "input_boolean"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_POWER_ENTITY)] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["switch", "input_boolean"], multiple=False
                )
            )

        # Player state entity
        if self.options.get(CONF_PLAYER_STATE_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_PLAYER_STATE_ENTITY,
                    default=self.options[CONF_PLAYER_STATE_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_select"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_PLAYER_STATE_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_select"], multiple=False
                    )
                )
            )

        if self.options.get(CONF_VOLUME_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_VOLUME_ENTITY, default=self.options[CONF_VOLUME_ENTITY]
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["input_number", "number"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_VOLUME_ENTITY)] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["input_number", "number"], multiple=False
                )
            )

        # Always include volume step
        schema_fields[
            vol.Optional(
                CONF_VOLUME_STEP,
                default=self.options.get(CONF_VOLUME_STEP, DEFAULT_VOLUME_STEP),
            )
        ] = vol.Coerce(float)

        if self.options.get(CONF_MUTE_ENTITY):
            schema_fields[
                vol.Optional(CONF_MUTE_ENTITY, default=self.options[CONF_MUTE_ENTITY])
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["switch", "input_boolean"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MUTE_ENTITY)] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["switch", "input_boolean"], multiple=False
                )
            )

        if self.options.get(CONF_SOURCE_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_SOURCE_ENTITY, default=self.options[CONF_SOURCE_ENTITY]
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["input_select", "select"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_SOURCE_ENTITY)] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["input_select", "select"], multiple=False
                )
            )

        if self.options.get(CONF_SOURCE_LIST_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_SOURCE_LIST_ENTITY,
                    default=self.options[CONF_SOURCE_LIST_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_text"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_SOURCE_LIST_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_text"], multiple=False
                    )
                )
            )

        schema = vol.Schema(schema_fields)
        return self.async_show_form(step_id="basic", data_schema=schema)

    async def async_step_media_info(self, user_input=None):
        """Configure media information entities."""
        if user_input is not None:
            # Remove empty entity fields before processing
            cleaned_input = {
                key: value
                for key, value in user_input.items()
                if (isinstance(value, str) and value.strip())
                or not isinstance(value, str)
            }

            self.options.update(cleaned_input)
            return await self.async_step_init()

        # Create schema with conditional fields
        schema_fields = {}

        if self.options.get(CONF_MEDIA_TITLE_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_TITLE_ENTITY,
                    default=self.options[CONF_MEDIA_TITLE_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_text"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_TITLE_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_text"], multiple=False
                    )
                )
            )

        if self.options.get(CONF_MEDIA_ARTIST_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_ARTIST_ENTITY,
                    default=self.options[CONF_MEDIA_ARTIST_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_text"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_ARTIST_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_text"], multiple=False
                    )
                )
            )

        if self.options.get(CONF_MEDIA_ALBUM_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_ALBUM_ENTITY,
                    default=self.options[CONF_MEDIA_ALBUM_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_text"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_ALBUM_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_text"], multiple=False
                    )
                )
            )

        if self.options.get(CONF_MEDIA_IMAGE_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_IMAGE_ENTITY,
                    default=self.options[CONF_MEDIA_IMAGE_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(domain=["image"], multiple=False)
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_IMAGE_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(domain=["image"], multiple=False)
                )
            )

        if self.options.get(CONF_MEDIA_POSITION_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_POSITION_ENTITY,
                    default=self.options[CONF_MEDIA_POSITION_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_number"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_POSITION_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_number"], multiple=False
                    )
                )
            )

        if self.options.get(CONF_MEDIA_DURATION_ENTITY):
            schema_fields[
                vol.Optional(
                    CONF_MEDIA_DURATION_ENTITY,
                    default=self.options[CONF_MEDIA_DURATION_ENTITY],
                )
            ] = selector.EntitySelector(
                selector.EntitySelectorConfig(
                    domain=["sensor", "input_number"], multiple=False
                )
            )
        else:
            schema_fields[vol.Optional(CONF_MEDIA_DURATION_ENTITY)] = (
                selector.EntitySelector(
                    selector.EntitySelectorConfig(
                        domain=["sensor", "input_number"], multiple=False
                    )
                )
            )

        schema = vol.Schema(schema_fields)
        return self.async_show_form(step_id="media_info", data_schema=schema)

    async def async_step_actions(self, user_input=None):
        """Configure media player actions."""
        if user_input is not None:
            _LOGGER.debug("Raw user input received for actions: %s", user_input)
            _LOGGER.debug("Current options before processing actions: %s", self.options)

            # Get current actions dictionary, making a copy to modify.
            # Ensures current_actions_dict is a dict, even if CONF_ACTIONS was missing or not a dict.
            current_actions_dict = self.options.get(CONF_ACTIONS, {}).copy()
            if not isinstance(
                current_actions_dict, dict
            ):  # Should be caught by __init__ check too
                _LOGGER.warning(
                    "%s in options was not a dict, resetting. Value: %s",
                    CONF_ACTIONS,
                    current_actions_dict,
                )
                current_actions_dict = {}

            for action_key in _PLAYER_ACTION_KEYS:
                if action_key in user_input:
                    action_data_list = user_input[action_key]
                    _LOGGER.debug(
                        "Processing action %s from user_input: %s (type: %s)",
                        action_key,
                        action_data_list,
                        type(action_data_list),
                    )

                    if isinstance(action_data_list, list):
                        # Store the list directly. ActionSelector returns a list (possibly empty).
                        current_actions_dict[action_key] = action_data_list
                        _LOGGER.debug(
                            "Stored action %s as list: %s",
                            action_key,
                            current_actions_dict[action_key],
                        )
                    else:
                        # This case should ideally not happen if ActionSelector behaves as expected.
                        _LOGGER.warning(
                            "Action data for %s is not a list: %s. Clearing to empty list.",
                            action_key,
                            action_data_list,
                        )
                        current_actions_dict[action_key] = []
                # If action_key is not in user_input, its value in current_actions_dict remains unchanged.

            self.options[CONF_ACTIONS] = current_actions_dict
            _LOGGER.debug(
                "Final actions configuration: %s", self.options.get(CONF_ACTIONS)
            )
            _LOGGER.debug("Updated options after actions step: %s", self.options)

            return await self.async_step_init()

        # Schema generation part
        schema_fields = {}
        # Get the dictionary of all player actions from options.
        # Defaults to an empty dict if CONF_ACTIONS is not set or not a dict.
        all_player_actions = self.options.get(CONF_ACTIONS)
        if not isinstance(all_player_actions, dict):
            all_player_actions = {}

        for action_key in _PLAYER_ACTION_KEYS:
            # Get the specific list of action configurations for this action_key.
            # Defaults to None if the action_key is not in all_player_actions.
            default_value_for_selector = all_player_actions.get(action_key)

            # default_value_for_selector is expected to be list[dict[str, Any]] or None.
            # An empty list [] is a valid default for ActionSelector (means no action configured).
            if default_value_for_selector is not None and isinstance(
                default_value_for_selector, list
            ):
                schema_fields[
                    vol.Optional(action_key, default=default_value_for_selector)
                ] = selector.ActionSelector()
            else:
                if (
                    default_value_for_selector is not None
                ):  # It's not None but not a list
                    _LOGGER.warning(
                        "Invalid stored action for %s (expected list, got %s): %s. Will show as unset.",
                        action_key,
                        type(default_value_for_selector).__name__,
                        default_value_for_selector,
                    )
                # Action was never set, explicitly removed, or stored in an invalid format.
                schema_fields[vol.Optional(action_key)] = selector.ActionSelector()

        schema = vol.Schema(schema_fields)

        return self.async_show_form(step_id="actions", data_schema=schema)
