//
// Created by eszdman on 21.11.2026.
//

// Shared ABI for the tile window, full image, tile origin and final store.
layout(push_constant) uniform Tile {
    ivec2 windowSize;
    ivec2 imageSize;
    ivec2 tileOrigin;
    ivec2 tileSize;
    ivec2 interior;
} tile;
#define u_size tile.windowSize
#define u_insize tile.imageSize
#define u_off (tile.tileOrigin - tile.interior)
#define u_outoff tile.tileOrigin
#define u_outsize tile.tileSize
#define u_inner tile.interior
#define u_fc ivec4(0, 1, 1, 2)
#define u_clip 1.0
#define texelFetch(resource, coords, level) read_ ## resource(coords)
#define imageStore(resource, coords, value) write_ ## resource(coords, value)
