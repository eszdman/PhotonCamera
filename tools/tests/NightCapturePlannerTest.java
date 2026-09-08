import com.particlesdevs.photoncamera.processing.parameters.NightCapturePlanner;

public final class NightCapturePlannerTest {
    private static NightCapturePlanner.Input input() {
        NightCapturePlanner.Input p = new NightCapturePlanner.Input();
        p.baseSeconds = .2; p.baseIso = 800; p.minSeconds = .001; p.maxSeconds = 1.0 / 3;
        p.minIso = 100; p.maxIso = 6400; p.maxFrames = 20;
        p.motionReliable = true; p.shake = 25; p.shadowP10 = .1;
        return p;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void validate(NightCapturePlanner.Input p, NightCapturePlanner.Plan plan) {
        require(plan != null, "Expected feasible plan");
        require(plan.shortSeconds >= p.minSeconds && plan.longSeconds <= p.maxSeconds, "Shutter bounds");
        require(plan.iso >= p.minIso && plan.iso <= p.maxIso, "ISO bounds");
        require(plan.size() >= 1 && plan.size() <= p.maxFrames, "Frame bounds");
        require(plan.durationSeconds <= p.budgetSeconds, "Duration budget");
        int longs = 0;
        for (int i = 0; i < plan.size(); i++) if (plan.isLong(i)) { longs++; require(i >= 2, "Short reference frames first"); }
        require(longs == plan.longCount, "Bracket schedule matches count");
        require(Double.isFinite(plan.score), "Finite score");
    }
    public static void main(String[] args) {
        NightCapturePlanner.Input p = input();
        NightCapturePlanner.Plan steady = NightCapturePlanner.select(p);
        validate(p, steady);
        p.cameraPixelsPerSecond = 12; p.subjectFraction = .2;
        NightCapturePlanner.Plan moving = NightCapturePlanner.select(p);
        validate(p, moving);
        require(moving.shortSeconds < steady.shortSeconds, "Movement needs shorter shutter");
        require(moving.longCount == 0, "Moving subject must suppress brackets");
        p = input(); p.tripod = true; p.maxSeconds = 2; p.budgetSeconds = 12;
        NightCapturePlanner.Plan tripod = NightCapturePlanner.select(p);
        validate(p, tripod);
        require(tripod.shortSeconds > steady.shortSeconds && tripod.iso < steady.iso, "Tripod trades readouts for integration");
        p = input(); p.contrast = .9; p.clippedFraction = .02; p.maxBracketRatio = 8;
        NightCapturePlanner.Plan hdr = NightCapturePlanner.select(p);
        validate(p, hdr);
        require(hdr.shortSeconds * hdr.iso < p.baseSeconds * p.baseIso * .76, "Protect preview highlights");
        p.baseSeconds = .02; p.baseIso = 100; p.tripod = true; p.maxSeconds = 2; p.budgetSeconds = 12;
        NightCapturePlanner.Plan brightHdr = NightCapturePlanner.select(p);
        validate(p, brightHdr);
        require(brightHdr.longCount > 0, "Static contrast scene with headroom should admit long frames");
        p.motionReliable = false;
        require(NightCapturePlanner.select(p).longCount == 0, "Unknown motion suppresses brackets");
        p = input(); p.noiseIso = 800; p.noiseS = .004; p.noiseO = .0001;
        require(NightCapturePlanner.select(p).sensorNoiseProfile, "Use valid sensor profile");
        p.noiseO = Double.NaN;
        require(!NightCapturePlanner.select(p).sensorNoiseProfile, "Malformed noise falls back");
        p = input(); p.noiseIso = 800; p.noiseS = .00001; p.noiseO = .00000001;
        NightCapturePlanner.Plan clean = NightCapturePlanner.select(p);
        p.noiseS *= 100; p.noiseO *= 100;
        NightCapturePlanner.Plan noisy = NightCapturePlanner.select(p);
        require(noisy.size() > clean.size(), "Higher absolute noise must buy more temporal support");
        p.minFrameSeconds = .5;
        NightCapturePlanner.Plan slow = NightCapturePlanner.select(p);
        validate(p, slow);
        require(slow.size() * .55 <= p.budgetSeconds, "Account for sensor frame duration");
        p.maxIso = 200; p.maxSeconds = .05;
        require(NightCapturePlanner.select(p) == null, "Infeasible exposure returns fallback");
        p = input(); p.baseSeconds = Double.NaN;
        require(NightCapturePlanner.select(p) == null, "Reject invalid metadata");
        for (int frames : new int[]{1, 2, 3, 8, 20}) for (double shake : new double[]{-1, 25, 400}) {
            p = input(); p.maxFrames = frames; p.shake = shake;
            validate(p, NightCapturePlanner.select(p));
        }
        System.out.println("Night capture planner checks passed; steady=" + steady.shortSeconds
                + " moving=" + moving.shortSeconds + " tripod=" + tripod.shortSeconds
                + " hdrLongs=" + hdr.longCount);
    }
}
