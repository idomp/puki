"""Voice packs for the fork's big-kids games, kept apart from generate_voices.py's own list.

Each module defines VOICE_LINES (same (name, english, hebrew) tuples as generate_voices.voice_lines)
and PHONEMES (output stem -> IPA, as generate_voices.PHONEMES). generate_voices.py adds them all.
"""

from . import letters, math

VOICE_LINES = letters.VOICE_LINES + math.VOICE_LINES
PHONEMES = {**letters.PHONEMES, **math.PHONEMES}
