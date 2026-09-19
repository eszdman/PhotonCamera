package com.particlesdevs.photoncamera.circularbarlib.ui.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Background of the manual palette: the translucent bubble alone while no
 * control is selected, or one continuous shape — the bubble with the wheel's
 * dome grown out of its top — while a knob is open. The dome inflates from the
 * bar's top edge ({@link #setDomeProgress(float)}) so the wheel reads as part
 * of the same bubble instead of a separate disc.
 * <p>
 * The silhouette is a single path: rounded bottom corners, straight sides,
 * then shoulder arcs tangent to both the sides and the dome's arc, so every
 * junction is tangent-continuous. The shoulder radius is solved so the
 * junction's rounding footprint along the edge matches the bubble's bottom
 * corners — a corner's perceived size is its tangent extent (r·tan(θ/2)), and
 * the shoulder turns only a shallow angle into the dome, so it needs a much
 * larger radius than the corner to look the same size. The same construction
 * is mirrored in {@code shaders/preview/panel_blur_fs.glsl} for the preview's
 * frosted blur — keep the two in sync.
 */
public class ManualPaletteBackground extends Drawable {
    /** Below this cap height the dome is skipped and only the bubble remains. */
    private static final float MIN_DOME_PX = 0.5f;
    /** Keeps the dome circle numerically sane as the cap height nears zero. */
    private static final float MAX_DOME_RADIUS = 60000f;
    /** Damped fixed-point iterations for the shoulder solve; converges to <1px. */
    private static final int SHOULDER_ITERATIONS = 8;

    private final Paint m_Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path m_Path = new Path();
    private final RectF m_Oval = new RectF();
    private final float m_CornerRadius;
    private final float m_DomeHeight;
    /**
     * Shoulder radius whose rounding footprint equals a bubble corner's at the
     * fully grown dome; solved per bounds, clamped per frame while animating.
     */
    private float m_DesiredShoulder;
    private float m_EffectiveShoulder;
    private float m_DomeProgress;

    /**
     * @param color          translucent scrim colour of the whole shape
     * @param cornerRadiusPx the bubble's corner radius; also the visual size
     *                       target the dome junction's rounding is matched to
     * @param domeHeightPx   reserved height of the wheel dome above the bubble
     */
    public ManualPaletteBackground(int color, float cornerRadiusPx, float domeHeightPx) {
        m_Paint.setStyle(Paint.Style.FILL);
        m_Paint.setColor(color);
        m_CornerRadius = cornerRadiusPx;
        m_DomeHeight = domeHeightPx;
    }

    /** Inflation of the wheel dome: 0 = bubble only, 1 = fully grown. */
    public void setDomeProgress(float progress) {
        float clamped = Math.min(1f, Math.max(0f, progress));
        if (m_DomeProgress != clamped) {
            m_DomeProgress = clamped;
            rebuildPath();
            invalidateSelf();
        }
    }

    public float getDomeProgress() {
        return m_DomeProgress;
    }

    public float getDomeHeightPx() {
        return m_DomeHeight;
    }

    /**
     * The currently drawn dome cap height: zero while collapsed below the
     * visibility threshold, so blur consumers switch to the plain bubble mask
     * exactly when the path does.
     */
    public float getEffectiveDomeHeightPx() {
        float hd = m_DomeProgress * m_DomeHeight;
        return hd < MIN_DOME_PX ? 0f : hd;
    }

    /** The shoulder radius the current frame's silhouette is built with. */
    public float getEffectiveShoulderRadiusPx() {
        return m_EffectiveShoulder;
    }

    public float getCornerRadiusPx() {
        return m_CornerRadius;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        solveDesiredShoulder();
        rebuildPath();
    }

    @Override
    public void draw(Canvas canvas) {
        if (!m_Path.isEmpty()) {
            canvas.drawPath(m_Path, m_Paint);
        }
    }

    /**
     * Solves the shoulder radius whose tangent extent (the rounding's
     * footprint along the side and the dome arc) equals the bubble corner's,
     * {@code r·tan(45°) = cornerRadius}, at the fully grown dome. The turn
     * angle at the tangency point itself depends on the radius, so the solve
     * is a damped fixed-point iteration.
     */
    private void solveDesiredShoulder() {
        float a = getBounds().width() / 2f;
        m_DesiredShoulder = m_CornerRadius;
        if (a <= 0f || m_DomeHeight <= 0f) {
            return;
        }
        float rD = (a * a + m_DomeHeight * m_DomeHeight) / (2f * m_DomeHeight);
        float rb = m_CornerRadius * 3f;
        for (int i = 0; i < SHOULDER_ITERATIONS; i++) {
            if (rD + a - 2f * rb <= 0f) {
                break;
            }
            float delta = (float) Math.sqrt(Math.max((rD - a) * (rD + a - 2f * rb), 0f));
            if (delta <= 0f) {
                break;
            }
            // Radius angle from vertical at the tangency point; the outline's
            // turn into the dome is its complement.
            double theta = Math.PI / 2.0 - Math.atan2(a - rb, delta);
            if (theta <= 0.02) {
                break;
            }
            float next = (float) (m_CornerRadius / Math.tan(theta / 2.0));
            rb = 0.5f * (rb + next);
        }
        m_DesiredShoulder = Math.min(Math.max(rb, m_CornerRadius), a * 0.95f);
    }

    /**
     * Walks the outline counter-clockwise on screen: bottom edge with rounded
     * corners, straight sides up to the shoulder tangent points, shoulder arcs
     * out into the dome arc, and back down the other side. Every arc meets its
     * neighbours tangentially, which is what makes the shape read as one bubble.
     */
    private void rebuildPath() {
        m_Path.rewind();
        Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        if (w <= 0f || h <= 0f || m_DomeHeight <= 0f || m_DomeHeight >= h) {
            return;
        }
        float left = b.left;
        float right = b.right;
        float bottom = b.bottom;
        float cx = b.exactCenterX();
        float a = w / 2f;
        float pillTop = b.top + m_DomeHeight;
        float rc = Math.min(m_CornerRadius, Math.min(a, (h - m_DomeHeight) / 2f));
        float hd = m_DomeProgress * m_DomeHeight;
        if (hd < MIN_DOME_PX) {
            m_Oval.set(left, pillTop, right, bottom);
            m_Path.addRoundRect(m_Oval, rc, rc, Path.Direction.CW);
            return;
        }
        // Dome arc through the bubble's top corners ((±a, pillTop)) with cap
        // height hd — the same arc KnobView places its items on.
        float rD = Math.min((a * a + hd * hd) / (2f * hd), MAX_DOME_RADIUS);
        float domeCy = pillTop - hd + rD;
        float rb = clampShoulderRadius(a, rD, domeCy, bottom - rc);
        m_EffectiveShoulder = rb;
        // Shoulder circle tangent to the side line x = ±a and internally
        // tangent to the dome circle; (rD - rb) ≥ (a - rb) always holds, so
        // the square root's argument cannot go negative.
        float sy = domeCy - (float) Math.sqrt(Math.max(
                (rD - rb) * (rD - rb) - (a - rb) * (a - rb), 0f));
        sy = Math.min(sy, bottom - rc);
        // Dome tangency points sit on the line joining the two arc centres.
        float k = rD / Math.max(rD - rb, 1e-3f);
        float dxS = a - rb;
        float dyS = domeCy - sy;
        float trX = cx + k * dxS;
        float trY = domeCy - k * dyS;
        float tlX = cx - k * dxS;
        float tlY = trY;
        float angTr = (float) Math.toDegrees(Math.atan2(trY - sy, trX - (cx + dxS)));
        float angTlDome = (float) Math.toDegrees(Math.atan2(tlY - domeCy, tlX - cx));

        m_Path.moveTo(left + rc, bottom);
        m_Path.lineTo(right - rc, bottom);
        m_Oval.set(right - 2f * rc, bottom - 2f * rc, right, bottom);
        m_Path.arcTo(m_Oval, 90f, -90f, false);
        m_Path.lineTo(right, sy);
        m_Oval.set(cx + dxS - rb, sy - rb, cx + dxS + rb, sy + rb);
        m_Path.arcTo(m_Oval, 0f, angTr, false);
        m_Oval.set(cx - rD, domeCy - rD, cx + rD, domeCy + rD);
        m_Path.arcTo(m_Oval, angTr, angTlDome - angTr, false);
        m_Oval.set(cx - dxS - rb, sy - rb, cx - dxS + rb, sy + rb);
        float sweepLeft = 180f - angTlDome;
        if (sweepLeft > 180f) {
            sweepLeft -= 360f;
        }
        m_Path.arcTo(m_Oval, angTlDome, sweepLeft, false);
        m_Path.lineTo(left, bottom - rc);
        m_Oval.set(left, bottom - 2f * rc, left + 2f * rc, bottom);
        m_Path.arcTo(m_Oval, 180f, -90f, false);
        m_Path.close();
    }

    /**
     * A larger shoulder touches the side line lower; mid-animation (flat dome)
     * the desired radius would slide that tangency past where the side meets
     * the bottom corner arc and kink the outline. Cap the radius to the
     * largest value that keeps the tangency point on the side — the resting,
     * fully grown dome always gets the full desired radius.
     */
    private float clampShoulderRadius(float a, float rD, float domeCy, float smax) {
        float floor = Math.min(m_CornerRadius, a * 0.25f);
        float over = Math.max(domeCy - smax, 0f);
        float limit = (rD + a - over * over / Math.max(rD - a, 1e-3f)) / 2f;
        return Math.max(Math.min(m_DesiredShoulder, limit), floor);
    }

    @Override
    public void setAlpha(int alpha) {
        if (m_Paint.getAlpha() != alpha) {
            m_Paint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return m_Paint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
