# CC Player - Home Assistant Integration

[![hacs_badge](https://img.shields.io/badge/HACS-Default-orange.svg)](https://github.com/custom-components/hacs)
[![GitHub release](https://img.shields.io/github/release/yourusername/ccplayer.svg)](https://github.com/yourusername/ccplayer/releases)

A custom Home Assistant integration that creates configurable media player entities by linking existing entities and actions. Perfect for creating unified media player controls from separate switches, sensors, and automations.

## Features

- **Flexible Entity Mapping**: Link any Home Assistant entities to media player functions
- **Custom Actions**: Configure Home Assistant actions for all media player controls
- **Dynamic Volume Control**: Automatically adapts to your volume entity's min/max range
- **Media Information**: Display title, artist, album, artwork, position, and duration
- **Source Selection**: Control input sources from any select entity
- **Sequential Configuration**: Easy step-by-step setup through the UI
- **Real-time Updates**: Automatically reflects changes from linked entities

## Installation

### HACS (Recommended)

1. Open HACS in your Home Assistant instance
2. Go to "Integrations"
3. Click the "+" button
4. Search for "CC Player"
5. Install the integration
6. Restart Home Assistant

### Manual Installation

1. Download the latest release from the [releases page](https://github.com/yourusername/ccplayer/releases)
2. Extract the `ccplayer` folder to your `custom_components` directory
3. Restart Home Assistant

## Configuration

### Adding the Integration

1. Go to **Settings** → **Devices & Services**
2. Click **Add Integration**
3. Search for "CC Player"
4. Follow the configuration steps:

#### Step 1: Basic Controls
- **Power Entity**: Switch or input_boolean for on/off control
- **Player State Entity**: Sensor or input_select indicating player state
- **Volume Entity**: input_number or number entity for volume control
- **Mute Entity**: Switch or input_boolean for mute control
- **Source Entity**: input_select or select for input source selection
- **Source List Entity**: Sensor or input_text containing available sources
- **Volume Step**: Percentage step for volume up/down (default: 5%)

#### Step 2: Media Information
- **Media Title Entity**: Sensor or input_text for current title
- **Media Artist Entity**: Sensor or input_text for current artist
- **Media Album Entity**: Sensor or input_text for current album
- **Media Image Entity**: Image entity for artwork
- **Media Position Entity**: Sensor or input_number for playback position (in ms)
- **Media Duration Entity**: Sensor or input_number for media duration (in ms)

#### Step 3: Actions
Configure Home Assistant actions for each media player function:
- **Play Action**: Action to start playback
- **Pause Action**: Action to pause playback
- **Stop Action**: Action to stop playback
- **Next Track Action**: Action to skip to next
- **Previous Track Action**: Action to go to previous
- **Seek Action**: Action to seek to position (templates available: `{{ position }}`, `{{ seek_position }}`)
- **Play Media Action**: Action to play specific media (templates: `{{ media_id }}`, `{{ media_type }}`)
- And more...

## Example Configuration

### Basic Media Player Setup

```yaml
# Example entities you might link to CC Player
input_boolean:
  media_player_power:
    name: "Media Player Power"

input_select:
  media_player_state:
    name: "Media Player State"
    options:
      - "off"
      - "playing"
      - "paused"
      - "idle"

input_number:
  media_player_volume:
    name: "Media Player Volume"
    min: 0
    max: 100
    step: 1

sensor:
  - platform: template
    sensors:
      current_media_title:
        value_template: "{{ states('sensor.kodi_title') }}"
```

### Action Examples

When configuring actions in the CC Player setup, you can use any Home Assistant action:

**Simple Service Call:**
```yaml
service: media_player.media_play
target:
  entity_id: media_player.kodi
```

**Script Execution:**
```yaml
service: script.turn_on
target:
  entity_id: script.play_music
```

**Template-based Seek:**
```yaml
service: kodi.call_method
data:
  entity_id: media_player.kodi
  method: Player.Seek
  playerid: 1
  value:
    percentage: "{{ position_pct }}"
```

## Templates in Actions

CC Player provides several template variables for actions:

### Seek Action Templates:
- `{{ position }}`: Seek position in seconds
- `{{ seek_position }}`: Calculated seek position
- `{{ position_pct }}`: Position as percentage of duration

### Play Media Templates:
- `{{ media_id }}`: Media content ID
- `{{ media_type }}`: Media content type
- `{{ media_content_id }}`: Alias for media_id
- `{{ media_content_type }}`: Alias for media_type

## Supported Entity Types

### Input Entities:
- `input_boolean` - For power, mute controls
- `input_select` - For player state, source selection
- `input_number` - For volume, position, duration
- `input_text` - For media information

### Other Entities:
- `switch` - For power, mute controls
- `sensor` - For state monitoring, media info
- `select` - For source selection
- `number` - For volume control
- `image` - For media artwork

## State Mapping

CC Player automatically maps these states from your player state entity:

| Entity State | Media Player State |
|--------------|-------------------|
| `playing`, `play` | Playing |
| `paused`, `pause` | Paused |
| `stopped`, `stop` | Idle |
| `idle` | Idle |
| `buffering` | Buffering |
| `on`, `true` | On |
| `off`, `false` | Off |

## Troubleshooting

### Integration Not Loading
- Check Home Assistant logs for errors
- Ensure all required files are in `custom_components/ccplayer/`
- Restart Home Assistant after installation

### Entities Not Updating
- Verify linked entities exist and have valid states
- Check entity IDs are spelled correctly
- Ensure linked entities are not in "unknown" or "unavailable" states

### Actions Not Working
- Test actions manually in Developer Tools
- Check action syntax in YAML mode
- Verify service calls and entity references

### Volume Control Issues
- Ensure volume entity has `min` and `max` attributes
- Check volume entity accepts `set_value` service
- Verify volume step configuration

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- [GitHub Issues](https://github.com/yourusername/ccplayer/issues)
- [Home Assistant Community Forum](https://community.home-assistant.io/)

---

**Note**: Replace `yourusername` with your actual GitHub username in all URLs.
