//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "amaze_pad.hpp"
#include "amaze_gradcd.hpp"
#include "amaze_bound.hpp"
#include "amaze_hvwt.hpp"
#include "amaze_nyq2.hpp"
#include "amaze_area.hpp"
#include "amaze_green.hpp"
#include "amaze_nyqref.hpp"
#include "amaze_rbpm.hpp"
#include "amaze_pmrbint.hpp"
#include "amaze_gcorr.hpp"
#include "amaze_chroma.hpp"
#include "amaze_final.hpp"
#include <algorithm>

namespace photon::post {
void Amaze::run(PostState& state) {
    using namespace shaders;
    constexpr int skirt = 48; // 32-pixel algorithm border plus 16-pixel mirror pad.
    const Size interior{std::min(state.settings.tileSize, state.image.x),
                        std::min(state.settings.tileSize, state.image.y)};
    const Size window{interior.x + 2 * skirt, interior.y + 2 * skirt};
    auto cfa = state.scalar(window), weights = state.scalar(window), nyquistTest = state.scalar(window),
         nyquist = state.scalar(window), areaWeights = state.scalar(window), greenWeights = state.scalar(window);
    auto grad = state.rgba(window), cdA = state.rgba(window), cdB = state.rgba(window), cd2 = state.rgba(window),
         greenD = state.rgba(window), refinedGreen = state.rgba(window), diagonal = state.rgba(window),
         rb = state.rgba(window), correctedGreen = state.rgba(window), chromaD = state.rgba(window);

    TilePasses passes(state.gpu);
    passes.dispatch(window, pad::u_in(state.cfa), pad::img_out(cfa));
    passes.dispatch(window, gradcd::u_cfa(cfa), gradcd::img_grad(grad), gradcd::img_a(cdA), gradcd::img_b(cdB));
    passes.dispatch(window, bound::u_cda(cdA), bound::u_grad(grad), bound::img_out(cd2));
    passes.dispatch(window, hvwt::u_cd2(cd2), hvwt::u_cdb(cdB), hvwt::u_grad(grad), hvwt::img_hvwt(weights),
                    hvwt::img_nyq(nyquistTest));
    passes.dispatch(window, nyq2::u_nyqtest(nyquistTest), nyq2::img_out(nyquist));
    passes.dispatch(window, area::u_grad(grad), area::u_nyq2(nyquist), area::u_hvwt(weights),
                    area::img_out(areaWeights));
    passes.dispatch(window, green::u_grad(grad), green::u_hvwt(areaWeights), green::u_cd2(cd2),
                    green::u_nyq2(nyquist), green::img_gd(greenD), green::img_hv(greenWeights));
    passes.dispatch(window, nyqref::u_grad(grad), nyqref::u_gd(greenD), nyqref::u_cd2(cd2),
                    nyqref::u_nyq2(nyquist), nyqref::img_out(refinedGreen));
    passes.dispatch(window, rbpm::u_grad(grad), rbpm::img_out(diagonal));
    passes.dispatch(window, pmrbint::u_grad(grad), pmrbint::u_rbpm(diagonal), pmrbint::img_out(rb));
    passes.dispatch(window, gcorr::u_pmrbint(rb), gcorr::u_gd2(refinedGreen), gcorr::u_hvwt(greenWeights),
                    gcorr::u_grad(grad), gcorr::img_out(correctedGreen));
    passes.dispatch(window, chroma::u_gd3(correctedGreen), chroma::img_out(chromaD));
    passes.dispatch(interior, final::u_chroma(chromaD), final::u_hvwt(greenWeights), final::img_out(state.rgb));

    for (int y = 0; y < state.image.y; y += interior.y) {
        for (int x = 0; x < state.image.x; x += interior.x) {
            Size crop{std::min(interior.x, state.image.x - x), std::min(interior.y, state.image.y - y)};
            passes.record(state.sequence, {window, state.image, {x, y}, crop, {skirt, skirt}});
        }
    }
}
} // namespace photon::post
