package org.adaway.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.adaway.R;

/**
 * Bottom bar background drawn as a single continuous shape: a broad, gentle wave that
 * rises across most of the width to a wide rounded crest under the floating action
 * button, then settles back down, i.e. one long {@code ___/‾‾‾‾‾\___} swell rather than
 * a tight dome.
 * <p>
 * The silhouette is one closed {@link Path}. The swell is built from two cubic Béziers
 * with horizontal tangents at both the ledge and the crest, so the whole top edge is a
 * smooth, broad ondulation that spans the bar almost edge to edge. It is filled in a
 * single pass, so it never reads as a separate block or a card sitting on a bar: the bar
 * and the swell are the same piece.
 * <p>
 * A soft shadow follows the whole outline via {@link Paint#setShadowLayer}, and a fine
 * stroke is drawn only along the top-visible edge to separate the bar from the screen
 * background in both light and dark themes. Because the shadow is cast by an arbitrary
 * (non-convex) path, the view is rendered on a software layer for reliable results on
 * every Android version.
 * <p>
 * The FAB is hosted as a child view and laid out by this container so its centre sits on
 * the crest of the swell, the lower half nestled into the bar and the upper half emerging
 * above it.
 */
public final class BumpBottomBar extends FrameLayout {
    // All geometry is expressed in dp and converted to px at construction time.
    private static final float BAR_HEIGHT_DP = 26f;     // flat ledge thickness at the sides
    private static final float BUMP_HEIGHT_DP = 10f;    // how high the central crest rises above the ledge
    private static final float EDGE_FLAT_DP = 12f;      // small flat margin before the swell starts
    private static final float FAB_RADIUS_DP = 28f;     // half of a normal 56dp FAB
    private static final float CURVE = 0.5f;            // S-curve softness of the swell (0..1, higher = broader/gentler)
    private static final float SHADOW_RADIUS_DP = 8f;   // shadow spread
    private static final float SHADOW_DY_DP = 4f;       // shadow downward offset
    private static final float TOP_PAD_DP = 6f;         // breathing room above the crest for the shadow
    private static final int SHADOW_COLOR = 0x44000000; // 27% black, soft but visible
    private static final float STROKE_WIDTH_DP = 1f;    // top-edge border thickness

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path outlinePath = new Path();

    private final float barHeightPx;
    private final float bumpHeightPx;
    private final float edgeFlatPx;
    private final float fabRadiusPx;
    private final float topPadPx;
    private final float shadowRadiusPx;
    private final float shadowDyPx;

    public BumpBottomBar(Context context) {
        this(context, null);
    }

    public BumpBottomBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BumpBottomBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        this.barHeightPx = BAR_HEIGHT_DP * density;
        this.bumpHeightPx = BUMP_HEIGHT_DP * density;
        this.edgeFlatPx = EDGE_FLAT_DP * density;
        this.fabRadiusPx = FAB_RADIUS_DP * density;
        this.topPadPx = TOP_PAD_DP * density;
        this.shadowRadiusPx = SHADOW_RADIUS_DP * density;
        this.shadowDyPx = SHADOW_DY_DP * density;
        float strokeWidthPx = STROKE_WIDTH_DP * density;

        int barColor = context.getColor(R.color.bumpBarBackground);
        int strokeColor = context.getColor(R.color.bumpBarStroke);

        this.fillPaint.setStyle(Paint.Style.FILL);
        this.fillPaint.setColor(barColor);
        this.fillPaint.setShadowLayer(this.shadowRadiusPx, 0f, this.shadowDyPx, SHADOW_COLOR);

        this.strokePaint.setStyle(Paint.Style.STROKE);
        this.strokePaint.setStrokeWidth(strokeWidthPx);
        this.strokePaint.setColor(strokeColor);

        // A shadow cast by an arbitrary path needs a software layer to render everywhere.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        // ViewGroups skip onDraw by default; we need it to paint the background shape.
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Height is driven by the geometry: ledge + crest + the FAB emerging above the crest + shadow.
        int height = Math.round(this.barHeightPx + this.bumpHeightPx + this.fabRadiusPx
                + this.topPadPx + this.shadowRadiusPx);
        measureChildren(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float centerX = getWidth() / 2f;
        float crestY = getHeight() - this.barHeightPx - this.bumpHeightPx;
        // Centre every child (the FAB) on the crest so the button rests on the swell.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int childLeft = Math.round(centerX - childWidth / 2f);
            int childTop = Math.round(crestY - childHeight / 2f);
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }

    /**
     * Traces the visible top edge of the bar into the given path: flat left margin, a long
     * gentle swell up to a wide crest and back down, then flat right margin. Used both for
     * the filled shape and for the top-edge stroke.
     */
    private void traceTopEdge(Path target, float width, float ledgeY, float crestY) {
        float centerX = width / 2f;
        float leftStart = this.edgeFlatPx;
        float rightEnd = width - this.edgeFlatPx;
        float halfSpan = centerX - leftStart;
        float control = halfSpan * CURVE;

        target.moveTo(0f, ledgeY);
        target.lineTo(leftStart, ledgeY);
        // Rise: horizontal tangent on the ledge, horizontal tangent at the crest → smooth, broad swell.
        target.cubicTo(leftStart + control, ledgeY, centerX - control, crestY, centerX, crestY);
        // Symmetric fall back down to the ledge.
        target.cubicTo(centerX + control, crestY, rightEnd - control, ledgeY, rightEnd, ledgeY);
        target.lineTo(width, ledgeY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float ledgeY = height - this.barHeightPx;
        float crestY = ledgeY - this.bumpHeightPx;

        // ── Fill: complete closed shape (with shadow) ──────────────────────────
        this.path.reset();
        traceTopEdge(this.path, width, ledgeY, crestY);
        this.path.lineTo(width, height);
        this.path.lineTo(0f, height);
        this.path.close();
        canvas.drawPath(this.path, this.fillPaint);

        // ── Stroke: top-edge only (the hidden bottom edge is excluded) ─────────
        this.outlinePath.reset();
        traceTopEdge(this.outlinePath, width, ledgeY, crestY);
        canvas.drawPath(this.outlinePath, this.strokePaint);
    }
}
