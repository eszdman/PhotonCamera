#
# Created by eszdman on 21.11.2026.
#

"""Confirm that a shader interface change is caught at its C++ call site."""
import subprocess
import sys
import tempfile
from pathlib import Path

compiler, includes = sys.argv[1:]
flags = ['-I' + item for item in Path(includes).read_text().splitlines() if item]
prefix = '''
#include "EsdShaders.hpp"
#include "amaze_pad.hpp"
#include "amaze_bound.hpp"
#include "esd3d2_steered.hpp"
#include "esd3d2_steered_prod.hpp"
using namespace photon::post;
using namespace photon::post::shaders;
using Filter = esd3d2_steered;
using namespace photon::esd4d;
using WarpShader = photon::esd4d::shaders::warp;
void record(FramePasses& passes, Plane<Format::r32f> cfa, Plane<Format::rgba16f> color, Plane<Format::rg16f> gradient,
            kp::Manager& gpu, kp::Sequence& queue, TensorPass& slot,
            std::shared_ptr<kp::TensorT<uint32_t>> raw, std::shared_ptr<kp::TensorT<float>> motion) {
    auto warp = slot.useProgram(gpu, queue, assets::warp_floor, 8, 8);
    auto program = passes.useProgram<Filter>();
'''
cases = {
    'valid': 'bindImages(pad::u_in(cfa), pad::img_out(cfa));',
    'missing': 'bindImages(pad::u_in(cfa));',
    'duplicate': 'bindImages(pad::u_in(cfa), pad::u_in(cfa));',
    'format': 'bindImages(pad::u_in(color), pad::img_out(cfa));',
    'foreign': 'bindImages(pad::u_in(cfa), bound::img_out(color));',
    'valid_program': '''program.setVar(Filter::MOIRE, 1.5f);
                        program.bindInputs(Filter::InputBuffer(color), Filter::GradBuffer(gradient)).writeTo(color);''',
    'foreign_var': 'program.setVar(esd3d2_steered_prod::MOIRE, 1.5f);',
    'wrong_var_type': 'program.setVar(Filter::kernelRadius, 3.5f);',
    'missing_input': 'program.bindInputs(Filter::InputBuffer(color)).writeTo(color);',
    'foreign_input': 'program.bindInputs(esd3d2_steered_prod::InputBuffer(color), Filter::GradBuffer(gradient)).writeTo(color);',
    'wrong_output': 'program.bindInputs(Filter::InputBuffer(color), Filter::GradBuffer(gradient)).writeTo(cfa);',
    'stale': 'bindImages(pad::old_input(cfa), pad::img_out(cfa));',
}
warp_call = 'warp.bindInputs(WarpShader::Source(raw), WarpShader::Accumulator(raw), WarpShader::Motion(motion)).writeTo(WarpShader::Destination(raw));'
cases.update({
    'valid_warp': 'WarpShader::Parameters params{}; params.exposure = 1.f; warp.setVars(params);' + warp_call,
    'foreign_tensor_params': 'warp.setVars(photon::esd4d::shaders::combine::Parameters{});',
    'readonly_tensor_output': warp_call.replace('Source(raw)', 'Destination(raw)').replace('writeTo(WarpShader::Destination', 'writeTo(WarpShader::Source'),
    'duplicate_tensor': warp_call.replace('Accumulator(raw)', 'Source(raw)'),
    'wrong_tensor_type': warp_call.replace('Motion(motion)', 'Motion(raw)'),
    'foreign_tensor_var': 'warp.setVar(photon::esd4d::shaders::combine::exposure, 1.f);',
})
with tempfile.TemporaryDirectory() as temp:
    path = Path(temp) / 'post_contract.cpp'
    for name, code in cases.items():
        path.write_text(prefix + code + '\n}\n')
        result = subprocess.run([compiler, '-std=c++17', '-fsyntax-only', *flags, path],
                                capture_output=True, text=True)
        if (result.returncode == 0) != (name in ('valid', 'valid_program', 'valid_warp')):
            raise SystemExit(f"Unexpected compile result for {name}: {result.stderr}")
