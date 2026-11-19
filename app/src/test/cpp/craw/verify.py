"""Check integration files against the upstream independent Python reference.
Usage: python verify.py /path/to/CinematicRAWStudio-Spec/reference /tmp/craw-cases
"""
import sys
import json
import struct
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import craw
import numpy as np

files = sorted(Path(sys.argv[2]).glob('*.craw'))
assert len(files) == 32
for path in files:
    codec, cfa, raw10, binning = map(int, path.stem.split('_'))
    expected = np.fromfile(path.with_suffix('.raw'), dtype='<u2').reshape((6, 10) if binning else (12, 20))
    chunks = list(craw.read_chunks(path))
    assert all(valid for _, _, _, valid in chunks), path
    assert [c[0] for c in chunks] == [b'META', b'LSHD', b'FRAM', b'FRAM', b'AUDI', b'IMU_', b'IMU_', b'INDX']
    for i, (_, _, frame, _) in enumerate(chunks[2:4]):
        index, ts, size = struct.unpack_from('<QqI', frame)
        assert (index, ts) == (i, i * 33333333)
        meta = json.loads(frame[20:20 + size])
        assert meta['lshd_ref'] == 7
        payload = frame[20 + size:]
        assert payload[:3] == bytes((codec, 10 if raw10 else 16, cfa))
        decoded, _ = craw.decode_frame(payload)
        np.testing.assert_array_equal(decoded, expected)
    shading = chunks[1][2]
    assert struct.unpack_from('<qIHH', shading) == (0, 7, 2, 2)
    assert struct.unpack_from('<16f', shading, 16) == tuple(1 + i / 16 for i in range(16))
    audio = chunks[4][2]
    assert struct.unpack_from('<qBBHII', audio) == (1234, 1, 2, 0, 48000, 2)
    assert struct.unpack_from('<4h', audio, 20) == (-32768, 32767, -2, 3)
    for chunk, sensor, timestamps in [(chunks[5][2], 1, [1000, 2000]), (chunks[6][2], 2, [1500])]:
        assert struct.unpack_from('<BBHI', chunk) == (sensor, 0, 0, len(timestamps))
        assert [struct.unpack_from('<q', chunk, 8 + i * 20)[0] for i in range(len(timestamps))] == timestamps
    data = path.read_bytes()
    assert data[-8:] == b'CRAWEND\0'
    assert struct.unpack_from('<Q', data, len(data) - 16)[0] == chunks[-1][1]
print('32 containers decoded losslessly; CRCs, audio, independent IMU timestamps, shading and trailer verified')
