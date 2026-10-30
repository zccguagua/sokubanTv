package com.sokbantv.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the board with vector art on a Canvas. Player and boxes ease towards
 * their logical cells, which keeps repeated D-pad presses feeling smooth.
 */
public class BoardView extends View {

    public interface Listener {
        void onSwipe(int dx, int dy);

        void onTap();
    }

    private static final int FLOOR_A = 0xFF141E36;
    private static final int FLOOR_B = 0xFF17223D;
    private static final int GOAL = 0xFF35D0BA;
    private static final int PLAYER_LIGHT = 0xFF6BF0DC;
    private static final int PLAYER_DARK = 0xFF1C9A90;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private GameEngine engine;
    private Listener listener;

    private float tile;
    private float originX;
    private float originY;

    private float playerVX;
    private float playerVY;
    private float[] boxVX = new float[0];
    private float[] boxVY = new float[0];
    private float faceX;
    private float faceY = 1f;

    private long lastFrameNanos;
    private final long startNanos = System.nanoTime();

    private Shader wallShader;
    private Shader boxShader;
    private Shader boxDoneShader;
    private Shader boxStuckShader;
    private Shader goalGlow;
    private Shader playerShader;

    private float downX;
    private float downY;
    private boolean touching;

    public BoardView(Context context) {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setEngine(GameEngine engine) {
        this.engine = engine;
        if (engine == null) {
            return;
        }
        boxVX = new float[engine.boxCount()];
        boxVY = new float[engine.boxCount()];
        snapToModel();
        layoutBoard();
        invalidate();
    }

    /** Jump the visuals to the logical positions (level start / restart). */
    public void snapToModel() {
        if (engine == null) {
            return;
        }
        playerVX = engine.playerX();
        playerVY = engine.playerY();
        if (boxVX.length != engine.boxCount()) {
            boxVX = new float[engine.boxCount()];
            boxVY = new float[engine.boxCount()];
        }
        for (int i = 0; i < engine.boxCount(); i++) {
            boxVX[i] = engine.boxX(i);
            boxVY[i] = engine.boxY(i);
        }
        invalidate();
    }

    public void setFacing(int dx, int dy) {
        faceX = dx;
        faceY = dy;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutBoard();
    }

    private void layoutBoard() {
        if (engine == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        Level level = engine.level();
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        float margin = Math.min(availW, availH) * 0.03f;
        availW -= margin * 2;
        availH -= margin * 2;
        tile = Math.min(availW / level.width, availH / level.height);
        if (tile <= 0) {
            return;
        }
        originX = getPaddingLeft() + margin + (availW - tile * level.width) / 2f;
        originY = getPaddingTop() + margin + (availH - tile * level.height) / 2f;
        buildShaders();
    }

    private void buildShaders() {
        wallShader = new LinearGradient(0, 0, 0, tile,
                new int[]{0xFF3D4E74, 0xFF212E4A}, null, Shader.TileMode.CLAMP);
        boxShader = new LinearGradient(0, 0, tile, tile,
                new int[]{0xFFF8BC72, 0xFFC0743A}, null, Shader.TileMode.CLAMP);
        boxDoneShader = new LinearGradient(0, 0, tile, tile,
                new int[]{0xFF9BEBB2, 0xFF3E9F68}, null, Shader.TileMode.CLAMP);
        boxStuckShader = new LinearGradient(0, 0, tile, tile,
                new int[]{0xFFF19A94, 0xFFAE4A46}, null, Shader.TileMode.CLAMP);
        goalGlow = new RadialGradient(tile / 2f, tile / 2f, tile * 0.58f,
                new int[]{0x5535D0BA, 0x0035D0BA}, null, Shader.TileMode.CLAMP);
        playerShader = new RadialGradient(tile * 0.36f, tile * 0.3f, tile * 0.62f,
                new int[]{PLAYER_LIGHT, PLAYER_DARK}, null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null) {
            return;
        }
        if (tile <= 0) {
            layoutBoard();
            if (tile <= 0) {
                return;
            }
        }

        boolean moving = updateAnimation();

        drawBackdrop(canvas);
        drawTiles(canvas);
        drawBoxes(canvas);
        drawPlayer(canvas);

        if (moving) {
            postInvalidateOnAnimation();
        }
    }

    private boolean updateAnimation() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
        }
        float dt = Math.min((now - lastFrameNanos) / 1e9f, 0.05f);
        lastFrameNanos = now;

        float k = 1f - (float) Math.exp(-dt * 26f);
        boolean moving = false;

        playerVX += (engine.playerX() - playerVX) * k;
        playerVY += (engine.playerY() - playerVY) * k;
        if (Math.abs(engine.playerX() - playerVX) > 0.004f
                || Math.abs(engine.playerY() - playerVY) > 0.004f) {
            moving = true;
        }

        for (int i = 0; i < boxVX.length && i < engine.boxCount(); i++) {
            boxVX[i] += (engine.boxX(i) - boxVX[i]) * k;
            boxVY[i] += (engine.boxY(i) - boxVY[i]) * k;
            if (Math.abs(engine.boxX(i) - boxVX[i]) > 0.004f
                    || Math.abs(engine.boxY(i) - boxVY[i]) > 0.004f) {
                moving = true;
            }
        }
        return moving;
    }

    private void drawBackdrop(Canvas canvas) {
        float inset = tile * 0.34f;
        rect.set(originX - inset, originY - inset,
                originX + tile * engine.level().width + inset,
                originY + tile * engine.level().height + inset);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF0C1424);
        canvas.drawRoundRect(rect, tile * 0.45f, tile * 0.45f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tile * 0.03f));
        paint.setColor(0x332F4468);
        canvas.drawRoundRect(rect, tile * 0.45f, tile * 0.45f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTiles(Canvas canvas) {
        Level level = engine.level();
        float time = (System.nanoTime() - startNanos) / 1e9f;
        for (int y = 0; y < level.height; y++) {
            for (int x = 0; x < level.width; x++) {
                byte kind = level.tile(x, y);
                if (kind == Level.VOID) {
                    continue;
                }
                float px = originX + x * tile;
                float py = originY + y * tile;
                if (kind == Level.WALL) {
                    drawWall(canvas, px, py);
                } else {
                    drawFloor(canvas, px, py, ((x + y) & 1) == 1);
                    if (kind == Level.GOAL) {
                        drawGoal(canvas, px, py, time, x, y);
                    }
                }
            }
        }
    }

    private void drawFloor(Canvas canvas, float px, float py, boolean alternate) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(alternate ? FLOOR_B : FLOOR_A);
        float gap = Math.max(1f, tile * 0.02f);
        rect.set(px + gap, py + gap, px + tile - gap, py + tile - gap);
        canvas.drawRoundRect(rect, tile * 0.14f, tile * 0.14f, paint);
    }

    private void drawWall(Canvas canvas, float px, float py) {
        canvas.save();
        canvas.translate(px, py);
        float inset = tile * 0.03f;
        rect.set(inset, inset, tile - inset, tile - inset);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(wallShader);
        canvas.drawRoundRect(rect, tile * 0.2f, tile * 0.2f, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tile * 0.028f));
        paint.setColor(0xFF4E638F);
        canvas.drawRoundRect(rect, tile * 0.2f, tile * 0.2f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x16FFFFFF);
        rect.set(tile * 0.24f, tile * 0.16f, tile * 0.76f, tile * 0.26f);
        canvas.drawRoundRect(rect, tile * 0.05f, tile * 0.05f, paint);
        canvas.restore();
    }

    private void drawGoal(Canvas canvas, float px, float py, float time, int x, int y) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.1f + (x + y) * 0.7f);
        canvas.save();
        canvas.translate(px, py);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(goalGlow);
        canvas.drawCircle(tile / 2f, tile / 2f, tile * 0.55f, paint);
        paint.setShader(null);

        float cx = tile / 2f;
        float cy = tile / 2f;
        float r = tile * 0.2f;
        path.reset();
        path.moveTo(cx, cy - r);
        path.lineTo(cx + r, cy);
        path.lineTo(cx, cy + r);
        path.lineTo(cx - r, cy);
        path.close();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tile * 0.04f));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(withAlpha(GOAL, 0.45f + 0.35f * pulse));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(GOAL, 0.25f + 0.35f * pulse));
        canvas.drawCircle(cx, cy, r * 0.3f, paint);
        canvas.restore();
    }

    private void drawBoxes(Canvas canvas) {
        for (int i = 0; i < engine.boxCount() && i < boxVX.length; i++) {
            boolean onGoal = engine.level().isGoal(engine.boxX(i), engine.boxY(i));
            boolean stuck = !onGoal && engine.isBoxStuck(i);
            Shader body = onGoal ? boxDoneShader : (stuck ? boxStuckShader : boxShader);
            drawBox(canvas, originX + boxVX[i] * tile, originY + boxVY[i] * tile, body);
        }
    }

    private void drawBox(Canvas canvas, float px, float py, Shader body) {
        canvas.save();
        canvas.translate(px, py);

        float inset = tile * 0.1f;
        float radius = tile * 0.17f;

        // ground shadow
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        rect.set(inset, inset + tile * 0.06f, tile - inset, tile - inset + tile * 0.06f);
        canvas.drawRoundRect(rect, radius, radius, paint);

        rect.set(inset, inset, tile - inset, tile - inset);
        paint.setShader(body);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // bevel
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tile * 0.035f));
        paint.setColor(0x66FFF4E2);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // cross braces
        paint.setColor(0x7A5A3418);
        paint.setStrokeWidth(Math.max(1f, tile * 0.055f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float a = inset + tile * 0.1f;
        float b = tile - inset - tile * 0.1f;
        canvas.drawLine(a, a, b, b, paint);
        canvas.drawLine(b, a, a, b, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);

        // glossy top-left corner
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x30FFFFFF);
        rect.set(inset + tile * 0.07f, inset + tile * 0.07f,
                inset + tile * 0.42f, inset + tile * 0.2f);
        canvas.drawRoundRect(rect, tile * 0.06f, tile * 0.06f, paint);
        canvas.restore();
    }

    private void drawPlayer(Canvas canvas) {
        float px = originX + playerVX * tile;
        float py = originY + playerVY * tile;
        canvas.save();
        canvas.translate(px, py);

        float cx = tile / 2f;
        float cy = tile / 2f;

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        rect.set(cx - tile * 0.3f, cy + tile * 0.26f, cx + tile * 0.3f, cy + tile * 0.4f);
        canvas.drawOval(rect, paint);

        paint.setShader(playerShader);
        canvas.drawCircle(cx, cy, tile * 0.33f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, tile * 0.03f));
        paint.setColor(0x55021C22);
        canvas.drawCircle(cx, cy, tile * 0.33f, paint);

        // eyes, looking where the player is heading
        float eyeR = tile * 0.055f;
        float offsetX = tile * 0.11f;
        float lookX = faceX * tile * 0.035f;
        float lookY = faceY * tile * 0.035f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF10222B);
        canvas.drawCircle(cx - offsetX + lookX, cy - tile * 0.02f + lookY, eyeR, paint);
        canvas.drawCircle(cx + offsetX + lookX, cy - tile * 0.02f + lookY, eyeR, paint);

        paint.setColor(0x55FFFFFF);
        canvas.drawCircle(cx - tile * 0.19f, cy - tile * 0.18f, tile * 0.09f, paint);
        canvas.restore();
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    // ----------------------------------------------------------------------
    // touch input so the game can also be tried on a phone
    // ----------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                touching = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (!touching) {
                    return true;
                }
                touching = false;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                float threshold = Math.max(tile * 0.4f, 48f);
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > threshold) {
                    listener.onSwipe(dx > 0 ? 1 : -1, 0);
                } else if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) > threshold) {
                    listener.onSwipe(0, dy > 0 ? 1 : -1);
                } else {
                    listener.onTap();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                return true;
            default:
                return true;
        }
    }
}
