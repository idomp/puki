"""Voice packs for the fork's big-kids games, kept apart from generate_voices.py's own list.

Each module defines VOICE_LINES (same (name, english, hebrew) tuples as generate_voices.voice_lines)
and PHONEMES (output stem -> IPA, as generate_voices.PHONEMES). generate_voices.py adds them all.
A module may also define SPEEDS (output stem -> BlueTTS speed) for lines that need their own pace.
"""

from . import letters, math

VOICE_LINES = letters.VOICE_LINES + math.VOICE_LINES
PHONEMES = {**letters.PHONEMES, **math.PHONEMES}

# Slower than the toddler lines (0.9): the parent's listening test (2026-11-06) found 0.7 best for
# sentences and letter names. Single syllables were clearest at 0.8, and very short clips can come
# out unclear or silent at some speeds, so each of those is set by ear in its pack's SPEEDS.
PACK_SPEED = 0.7
SPEEDS = {**getattr(letters, "SPEEDS", {}), **getattr(math, "SPEEDS", {})}
_PACK_KEYS = {name for name, _, _ in VOICE_LINES}


def speed_for(stem, default):
    """The speed for one output file: its own, else the packs' pace for pack lines, else default."""
    if stem in SPEEDS:
        return SPEEDS[stem]
    name = stem.rsplit("_", 2)[0] if stem.endswith(("_he_f", "_he_m")) else stem.rsplit("_", 1)[0]
    return PACK_SPEED if name in _PACK_KEYS else default
