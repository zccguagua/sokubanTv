package com.sokbantv.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The actual game screen: one board, remote control input, menus. */
public class GameActivity extends Activity implements BoardView.Listener {

    public static final String EXTRA_LEVEL = "level";

    private LevelRepository repository;
    private Progress progress;
    private GameEngine engine;
    private BoardView board;

    private TextView titleView;
    private TextView statsView;
    private TextView stuckView;

    private boolean winShown;
    private AlertDialog menuDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        repository = LevelRepository.get(this);
        progress = new Progress(this);

        int index = getIntent().getIntExtra(EXTRA_LEVEL, 0);
        Level level = repository.level(index);
        if (level == null) {
            finish();
            return;
        }
        progress.setLastLevel(level.index);

        engine = new GameEngine(level);
        setContentView(buildContent());
        updateHud();
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.screenBackground());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 40);
        column.setPadding(padH, Ui.dp(this, 18), padH, Ui.dp(this, 14));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleView = Ui.label(this, "", 24, Ui.TEXT, true);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statsView = Ui.label(this, "", 15, Ui.TEXT_DIM, false);
        statsView.setGravity(Gravity.END);
        header.addView(statsView);
        column.addView(header);

        board = new BoardView(this);
        board.setListener(this);
        board.setEngine(engine);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        boardParams.topMargin = Ui.dp(this, 8);
        column.addView(board, boardParams);

        stuckView = Ui.label(this,
                "有箱子已经卡死在角落 · 按 OK 打开菜单撤销",
                13, Ui.WARN, false);
        stuckView.setGravity(Gravity.CENTER);
        stuckView.setVisibility(View.GONE);
        column.addView(stuckView);

        TextView hint = Ui.label(this, getString(R.string.hint_game),
                13, Ui.TEXT_DIM, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Ui.dp(this, 6);
        column.addView(hint, hintParams);

        return root;
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                tryMove(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                tryMove(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                tryMove(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                tryMove(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_MENU:
                if (event.getRepeatCount() == 0) {
                    showMenu();
                }
                return true;
            case KeyEvent.KEYCODE_Z:
            case KeyEvent.KEYCODE_DEL:
                undo();
                return true;
            case KeyEvent.KEYCODE_R:
                if (event.getRepeatCount() == 0) {
                    restart();
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void tryMove(int dx, int dy) {
        if (winShown || menuDialog != null && menuDialog.isShowing()) {
            return;
        }
        board.setFacing(dx, dy);
        if (engine.move(dx, dy)) {
            board.invalidate();
            updateHud();
            if (engine.isSolved()) {
                onSolved();
            }
        } else {
            board.invalidate();
        }
    }

    private void undo() {
        if (winShown || !engine.canUndo()) {
            return;
        }
        if (engine.undo()) {
            board.invalidate();
            updateHud();
        }
    }

    private void restart() {
        engine = new GameEngine(engine.level());
        board.setEngine(engine);
        board.snapToModel();
        winShown = false;
        updateHud();
    }

    private void updateHud() {
        Level level = engine.level();
        titleView.setText(getString(R.string.level_fmt, level.index + 1));
        StringBuilder stats = new StringBuilder();
        stats.append("步数 ").append(engine.moves())
                .append(" · 推动 ").append(engine.pushes());
        if (level.par > 0) {
            stats.append(" · 最少 ").append(level.par).append(" 推");
        }
        int best = progress.bestMoves(level.index);
        if (best > 0) {
            stats.append(" · 最好 ").append(best).append(" 步");
        }
        statsView.setText(stats.toString());

        boolean stuck = !winShown && engine.anyBoxStuck();
        stuckView.setVisibility(stuck ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // menus
    // ------------------------------------------------------------------
    private void showMenu() {
        if (winShown || menuDialog != null && menuDialog.isShowing()) {
            return;
        }
        final String[] items = {
                "继续游戏",
                "撤销一步",
                "重新开始",
                "返回关卡列表",
        };
        menuDialog = new AlertDialog.Builder(this, R.style.Dialog_SokobanTV)
                .setTitle(getString(R.string.level_fmt, engine.level().index + 1))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (which) {
                            case 1:
                                undo();
                                break;
                            case 2:
                                restart();
                                break;
                            case 3:
                                finish();
                                break;
                            default:
                                break;
                        }
                    }
                })
                .create();
        menuDialog.show();
    }

    private void onSolved() {
        winShown = true;
        progress.recordWin(engine.level().index, engine.moves(), engine.pushes());
        updateHud();
        board.postDelayed(new Runnable() {
            @Override
            public void run() {
                showWinDialog();
            }
        }, 380);
    }

    private void showWinDialog() {
        if (isFinishing()) {
            return;
        }
        final Level level = engine.level();
        boolean lastLevel = level.index >= repository.count() - 1;

        StringBuilder message = new StringBuilder();
        message.append("步数 ").append(engine.moves())
                .append(" · 推动 ").append(engine.pushes());
        if (level.par > 0) {
            message.append('\n').append("最少 ").append(level.par).append(" 推即可完成");
        }
        int best = progress.bestMoves(level.index);
        if (best > 0) {
            message.append('\n').append("最好成绩 ").append(best).append(" 步");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Dialog_SokobanTV)
                .setTitle(lastLevel ? "全部通关！" : "过关！")
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton(lastLevel ? "返回关卡列表" : "下一关",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (lastLevel) {
                                    finish();
                                } else {
                                    openLevel(level.index + 1);
                                }
                            }
                        })
                .setNeutralButton("重玩", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restart();
                    }
                })
                .setNegativeButton("关卡列表", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        builder.show();
    }

    private void openLevel(int index) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(EXTRA_LEVEL, index);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------------------
    // touch (phone) support
    // ------------------------------------------------------------------
    @Override
    public void onSwipe(int dx, int dy) {
        tryMove(dx, dy);
    }

    @Override
    public void onTap() {
        showMenu();
    }
}
