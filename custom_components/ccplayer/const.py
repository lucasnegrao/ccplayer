"""Constants for the CC Player integration."""

DOMAIN = "ccplayer"
DEFAULT_PREFIX = "ccplayer"
# Configuration and options
CONF_POWER_ENTITY = "power_entity"
CONF_PLAYER_STATE_ENTITY = "player_state_entity"
CONF_SOURCE_ENTITY = "source_entity"
CONF_SOURCE_LIST_ENTITY = "source_list_entity"
CONF_VOLUME_ENTITY = "volume_entity"
CONF_VOLUME_STEP = "volume_step"
CONF_MUTE_ENTITY = "mute_entity"
CONF_MEDIA_TITLE_ENTITY = "media_title_entity"
CONF_MEDIA_ARTIST_ENTITY = "media_artist_entity"
CONF_MEDIA_ALBUM_ENTITY = "media_album_entity"
CONF_MEDIA_IMAGE_ENTITY = "media_image_entity"
CONF_MEDIA_POSITION_ENTITY = "media_position_entity"
CONF_MEDIA_DURATION_ENTITY = "media_duration_entity"

# New Media Info Entities
CONF_MEDIA_ALBUM_ARTIST_ENTITY = "media_album_artist_entity"
CONF_MEDIA_TRACK_ENTITY = "media_track_entity"
CONF_MEDIA_SERIES_TITLE_ENTITY = "media_series_title_entity"
CONF_MEDIA_SEASON_ENTITY = "media_season_entity"
CONF_MEDIA_EPISODE_ENTITY = "media_episode_entity"
CONF_MEDIA_CHANNEL_ENTITY = "media_channel_entity"
CONF_MEDIA_PLAYLIST_ENTITY = "media_playlist_entity"

# Advanced State Entities
CONF_SOUND_MODE_ENTITY = "sound_mode_entity"
CONF_SOUND_MODE_LIST_ENTITY = "sound_mode_list_entity"
CONF_APP_ID_ENTITY = "app_id_entity"
CONF_APP_NAME_ENTITY = "app_name_entity"
CONF_GROUP_MEMBERS_ENTITY = "group_members_entity"
CONF_REPEAT_STATE_ENTITY = "repeat_state_entity"
CONF_SHUFFLE_STATE_ENTITY = "shuffle_state_entity"

# Action configuration
CONF_ACTIONS = "actions"
CONF_PLAY_ACTION = "play_action"
CONF_PAUSE_ACTION = "pause_action"
CONF_STOP_ACTION = "stop_action"
CONF_NEXT_ACTION = "next_action"
CONF_PREVIOUS_ACTION = "previous_action"
CONF_TOGGLE_ACTION = "toggle_action"
CONF_PLAY_PAUSE_ACTION = "play_pause_action"
CONF_SEEK_ACTION = "seek_action"
CONF_PLAY_MEDIA_ACTION = "play_media_action"
CONF_CLEAR_PLAYLIST_ACTION = "clear_playlist_action"
CONF_SHUFFLE_SET_ACTION = "shuffle_set_action"
CONF_REPEAT_SET_ACTION = "repeat_set_action"

# New Actions
CONF_SELECT_SOUND_MODE_ACTION = "select_sound_mode_action"
CONF_OPEN_ACTION = "open_action"
CONF_CLOSE_ACTION = "close_action"

# Default values
DEFAULT_NAME = "CC Player"
DEFAULT_VOLUME_STEP = 0.05

# Device information constants
DEVICE_MANUFACTURER = "Custom Component"
DEVICE_MODEL = "TV"
DEVICE_SW_VERSION = "1.0.0"
DEVICE_NAME_DEFAULT = "CC Player"
