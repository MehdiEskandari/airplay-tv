Generated 440 Hz stereo 44.1 kHz test tones, containing no recordings or user data.
Each file is a sequence of little-endian uint32 packet lengths and codec payloads.
AAC LC uses 1024 samples per frame; ALAC uses a 4096-sample frame.
These fixtures test the actual portable decoder and PCM output, not AirPlay pairing
or a TV speaker. The short source is generated with FFmpeg's `sine` filter.
