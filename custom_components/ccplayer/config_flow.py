"""Config flow for CC Player."""

import asyncio
import logging
import traceback
import json

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.components import mqtt
from homeassistant.config_entries import ConfigEntry, ConfigFlow
from homeassistant.const import CONF_NAME
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import async_get as async_get_device_registry
from homeassistant.helpers.entity_registry import async_get as async_get_entity_registry
from homeassistant.helpers.service_info.mqtt import MqttServiceInfo
from homeassistant.config_entries import ConfigFlow, ConfigFlowResult

from .const import (
    CONF_ACTIONS,
    CONF_MEDIA_ALBUM_ENTITY,
    CONF_MEDIA_ARTIST_ENTITY,
    CONF_MEDIA_DURATION_ENTITY,
    CONF_MEDIA_IMAGE_ENTITY,
    CONF_MEDIA_POSITION_ENTITY,
    CONF_MEDIA_TITLE_ENTITY,
    CONF_MUTE_ENTITY,
    CONF_PLAYER_STATE_ENTITY,
    CONF_POWER_ENTITY,
    CONF_SOURCE_ENTITY,
    CONF_SOURCE_LIST_ENTITY,
    CONF_VOLUME_ENTITY,
    CONF_VOLUME_STEP,
    DEFAULT_NAME,
    DOMAIN,
)
from .options_flow import CCPlayerOptionsFlow

_LOGGER = logging.getLogger(__name__)


class CCPlayerConfigFlow(ConfigFlow, domain=DOMAIN):
    """Handle a config flow for CC Player."""

    def __init__(self) -> None:
        """Initialize flow."""
        self._discovered_device_info: dict | None = None
        self._mqtt_discovered_prefix: str | None = None

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

        self._mqtt_discovered_prefix = data.get("deviceUniqueID")

        # Proceed to confirmation step
        return await self.async_step_confirm()

    async def async_step_confirm(self, user_input=None) -> ConfigFlowResult:
        """Confirm the setup."""
        # This step is reached from MQTT discovery or if user manually initiated.
        # For now, focusing on MQTT path.

        if user_input is not None:
            # User has confirmed, proceed to the user step for configuration.
            # self._discovered_device_info should have been populated if _mqtt_discovered_prefix was set.
            return await self.async_step_user()

        # This is the first time showing the confirm step after MQTT discovery (or manual start)
        description_placeholders = {}
        if self._mqtt_discovered_prefix:
            device_registry = async_get_device_registry(self.hass)
            matching_device = None
            for dev in device_registry.devices.values():
                if dev.identifiers:
                    for ident_tuple in dev.identifiers:
                        if len(ident_tuple) == 2 and self._mqtt_discovered_prefix in ident_tuple[1]:
                            matching_device = dev
                            break
                if matching_device:
                    break

            if matching_device:
                self._discovered_device_info = {
                    "name": matching_device.name or self._mqtt_discovered_prefix,
                    "linked_device_id": matching_device.id,
                }
                description_placeholders["device_name"] = self._discovered_device_info["name"]
            else:
                # No matching HA device, but we have a prefix from MQTT
                self._discovered_device_info = {
                    "name": self._mqtt_discovered_prefix, # Suggest name based on prefix
                    "linked_device_id": None, # No device to link yet
                }
                description_placeholders["device_name"] = self._mqtt_discovered_prefix

        # Show a generic confirmation form. 
        # If a device was identified via prefix, it will be used as a suggestion in the next step.
        return self.async_show_form(
            step_id="confirm",
            description_placeholders=description_placeholders if description_placeholders else None,
            # No data_schema needed for a simple confirmation, unless we want to add options here.
        )

    # async_step_confirm is removed.

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

        # This part is for showing the form for the first time or on validation error
        # Determine default values for the form based on discovery
        default_name = DEFAULT_NAME
        default_linked_device_id = None

        if self._discovered_device_info:
            default_name = self._discovered_device_info["name"]
            if self._discovered_device_info.get("linked_device_id"):
                # Ensure the discovered device ID is a valid choice
                if self._discovered_device_info["linked_device_id"] in device_choices:
                    default_linked_device_id = self._discovered_device_info["linked_device_id"]
                else:
                    _LOGGER.warning(
                        "Discovered device ID %s not in available device choices. Not pre-selecting.",
                        self._discovered_device_info["linked_device_id"]
                    )

        schema_fields = {
            vol.Required("name", default=default_name): str,
        }
        if default_linked_device_id:
            schema_fields[vol.Required("linked_device_id", default=default_linked_device_id)] = vol.In(device_choices)
        else:
            # If no valid default, or no discovery, make it required without a default,
            # so user must select.
            schema_fields[vol.Required("linked_device_id")] = vol.In(device_choices)


        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(schema_fields),
            errors=errors,
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        """Get the options flow for this handler."""
        return CCPlayerOptionsFlow(config_entry)
