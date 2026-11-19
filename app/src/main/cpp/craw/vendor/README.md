# Cinematic RAW Studio — Encoder

C++20 lossless encoder and streaming container writer for `.CRAW`
(spec: `../CinematicRAWStudio-Spec/SPEC.md`). Apache-2.0. Used by the RawLens
Android app through JNI (vendored with `tools/sync_craw.sh` in RawLens).

## Easy install: Mac, Linux and Windows

Install the complete toolset once with the
[shared installer](https://github.com/matthew777777/CinematicRAWStudio-Spec/blob/main/GETTING_STARTED.md).
It fetches the sibling repos and dependencies automatically.

Mac/Linux:

```sh
curl -fsSL https://raw.githubusercontent.com/matthew777777/CinematicRAWStudio-Spec/main/scripts/install.sh | bash
```

Windows PowerShell:

```powershell
& ([scriptblock]::Create((irm https://raw.githubusercontent.com/matthew777777/CinematicRAWStudio-Spec/main/scripts/install.ps1)))
```

Open a new terminal, then use `craw` for the editor,
`craw mount "take.craw"` for Resolve, `craw unmount` to close mounts,
or `craw export "take.craw"` to save CinemaDNG and sound.


## Build

```
cmake -S . -B build -G Ninja && ninja -C build
./build/craw_enc_conformance          # byte-exact against the Python reference vectors
./build/craw_bench --bits 12          # 4080x3060 throughput, JSON
scripts/build_android.sh              # Release arm64-v8a with the newest NDK
```
End users can use the shared installer above; the build commands are for developers.

## API

```cpp
#include <craw/Writer.h>

craw::Writer w("take.craw", metaJson);               // META chunk (SPEC.md §2)
craw::FrameDesc d{4080, 3060, 12, craw::Cfa::RGGB};  // width, height, bit depth, CFA
w.addFrame(raw16, strideSamples, d, timestampNs, frameJson);
w.addAudio(ts, craw::AudioFormat::F32, 2, 48000, samples, frames);
w.addImu(craw::ImuSensor::Gyroscope, samples, count);
w.addShading(ts, id, columns, rows, gains);          // frames reference it with "lshd_ref"
w.close();                                           // INDX + trailer
```

Encode on worker threads and commit in order (what RawLens does):
```cpp
size_t n = craw::encodeFrame(raw16, stride, d, dst, capacity);  // any thread, into your buffer
w.addEncodedFrame(dst, n, timestampNs, frameJson);              // committer thread, no copy
```
For current RawLens Live capture, `d.codec = 3` selects CRAW Live (formerly CRAW-Live v2).
Codec 2 is CRAW Live v1 — Legacy; codec 1 (CRAW Balanced, formerly CRAW-Fast) trades CPU for potentially
smaller payloads. The size ordering depends on the scene; “Fast” is a
historical name, not a claim that it outpaces Live. All preserve every RAW sample.
With any mode,
`d.frameCrc = false` avoids a raw-frame checksum pass when the payload is
written into a CRC32C-protected CRAW container. Readers must verify that
chunk CRC; standalone payloads then have no checksum.

The caller-buffer overload with one thread streams tiles into the supplied
buffer and retains only one tile of workspace. This limits memory pressure
when several recorder workers encode full-resolution frames concurrently.

`encodeFrame(..., threads)` also splits one frame over tiles. A sample above
`2^bitDepth` throws `std::invalid_argument` (RawLens retries at 16 bits).

## Tools

| tool | usage |
|------|-------|
| `craw_enc` | `craw_enc out.craw --width W --height H --bits B [--cfa RGGB] [--fps 30] [--meta meta.json] f0.raw f1.raw …` (uint16 LE frames; `fN.json` sidecars become frame JSON) |
| `craw_bench` | `craw_bench [--input frame.raw --width W --height H] [--bits 12] [--frames 30] [--threads N] [--tile 128] [--codec 1|2|3|4] [--frame-crc 0|1] [--direct 0|1]` |

## Performance

ARM NEON for plane extraction, MED residuals and block sums; four Rice code
words per 32-bit write; parallel frame CRC (CRC32C combine); zero-copy writes.
Historical MT6878 reports quote ~650 MPix/s with 8 threads and 653–700 MPix/s
through the JNI path. Exact original run output for those claims is not attached;
they do not establish sustained camera-to-storage fps.

The shared [benchmark guide](https://github.com/matthew777777/CinematicRAWStudio-Spec/blob/main/BENCHMARKS.md)
defines codec IDs, evidence limits and the reproducible CRAW/JPEG XL comparison.
The [comparison driver](https://github.com/matthew777777/CinematicRAWStudio-Spec/tree/main/bench/comparison)
measures identical RAW samples, size, encode/decode wall and CPU time, process
peak RSS and sustained isolated phone loops. Results are separate from capture
acceptance. [2026-10-30 results](https://github.com/matthew777777/CinematicRAWStudio-Spec/tree/main/results/comparison-20261030).

## License

Apache-2.0. See LICENSE and NOTICE.

## CRAW Compact — Offline

Set `FrameDesc::codec = 4` (or `craw_enc --codec 4`) for CRAW Compact — Offline.
It searches four predictors per tile and explicit per-block Rice parameters;
output is deterministic across thread counts. Codecs 1–3 retain their exact
wire output. Codec 4 uses no external entropy library. This trades CPU for
smaller archives and is substantially slower than Live v2. To re-encode an
existing take while retaining its metadata and ancillary chunks, build the
sibling Decoder tools and use `craw compact in.craw out.craw`.


## Codec display names

| ID | Display name | Historical names / aliases |
|---:|---|---|
| 1 | **CRAW Balanced** | CRAW-Fast, Fast |
| 2 | **CRAW Live v1 — Legacy** | CRAW-Live, CRAW-Live v1, Live v1 |
| 3 | **CRAW Live** | CRAW-Live v2, Live v2 |
| 4 | **CRAW Compact — Offline** | CRAW-Compact, Desktop Compact |

These are display names. Codec IDs, encoded bytes, saved numeric preferences
and legacy metadata strings retain their existing meanings. Existing recordings
are not rewritten. Historical benchmark reports keep their original labels;
use the IDs to match them to this table. Balanced describes the intended
size/CPU tradeoff, not a guarantee of the smallest files. Live is the current
recording choice; Live v1 is retained for compatibility and Compact is offline.


### DCG/DSG RAW14 input

Set `FrameDesc::bitDepth = 14` for a verified 14-bit capture stream. The encoder
already supports 1–16 bits and preserves the original sample codes; it must not
infer a 10-bit payload from stale static camera white-level metadata. Values
outside the declared range are rejected rather than silently truncated. Black
and white calibration remain metadata in the same code domain. The shared
`dcg14_gbrg_codec*` golden vectors exercise all four codecs, including low bits.
This describes storage support, not a claim about native ADC precision.
