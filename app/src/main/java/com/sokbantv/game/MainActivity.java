package com.sokbantv.game;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Level picker: a D-pad navigable grid of levels. */
public class MainActivity extends Activity {

    /** Everything that has to be refreshed when progress changes. */
    private static final class Tile {
        View root;
        TextView number;
        TextView info;
        Level level;
    }

    private LevelRepository repository;
    private Progress progress;

    private final List<Tile> tiles = new ArrayList<>();
    private GridLayout grid;
    private ScrollView scroll;
    private TextView counterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        repository = LevelRepository.get(this);
        progress = new Progress(this);

        setContentView(buildContent());
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.screenBackground());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 48);
        column.setPadding(padH, Ui.dp(this, 30), padH, Ui.dp(this, 18));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildHeader());

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = Ui.dp(this, 22);
        column.addView(scroll, scrollParams);

        grid = new GridLayout(this);
        grid.setColumnCount(columnsForScreen());
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scroll.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        List<Level> levels = repository.levels();
        for (Level level : levels) {
            grid.addView(createTile(level), tileParams());
        }

        TextView hint = Ui.label(this, getString(R.string.hint_select),
                13, Ui.TEXT_DIM, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Ui.dp(this, 14);
        column.addView(hint, hintParams);

        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        titles.addView(Ui.label(this, getString(R.string.app_title), 34, Ui.TEXT, true));

        TextView subtitle = Ui.label(this, getString(R.string.app_subtitle), 13,
                Ui.ACCENT, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(this, 6);
        titles.addView(subtitle, subtitleParams);

        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        counterView = Ui.label(this, "", 16, Ui.TEXT_DIM, false);
        counterView.setGravity(Gravity.END);
        header.addView(counterView);

        return header;
    }

    private int columnsForScreen() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 900) {
            return 7;
        }
        return widthDp >= 600 ? 6 : 4;
    }

    private GridLayout.LayoutParams tileParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = Ui.dp(this, 96);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        int margin = Ui.dp(this, 6);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private View createTile(final Level level) {
        final Tile tile = new Tile();
        tile.level = level;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(Ui.focusableCard(this));
        Ui.makeFocusable(root);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLevel(level.index);
            }
        });

        tile.number = Ui.label(this, String.valueOf(level.index + 1), 26, Ui.TEXT, true);
        tile.number.setGravity(Gravity.CENTER);
        root.addView(tile.number);

        tile.info = Ui.label(this, "", 11, Ui.TEXT_DIM, false);
        tile.info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = Ui.dp(this, 6);
        root.addView(tile.info, infoParams);

        tile.root = root;
        tiles.add(tile);
        bindTile(tile);
        return root;
    }

    private void bindTile(Tile tile) {
        int best = progress.bestMoves(tile.level.index);
        if (best > 0) {
            tile.info.setText("★ " + best + " 步");
            tile.info.setTextColor(Ui.ACCENT);
            tile.number.setTextColor(Ui.TEXT);
        } else if (tile.level.par > 0) {
            tile.info.setText("最少 " + tile.level.par + " 推");
            tile.info.setTextColor(Ui.TEXT_DIM);
            tile.number.setTextColor(Ui.TEXT);
        } else {
            tile.info.setText("");
        }
    }

    private void openLevel(int index) {
        progress.setLastLevel(index);
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_LEVEL, index);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        focusLastPlayed();
    }

    private void refresh() {
        for (Tile tile : tiles) {
            bindTile(tile);
        }
        counterView.setText(getString(R.string.completed_fmt,
                progress.completedCount(repository.count()), repository.count()));
    }

    private void focusLastPlayed() {
        if (tiles.isEmpty()) {
            return;
        }
        int index = Math.max(0, Math.min(progress.lastLevel(), tiles.size() - 1));
        tiles.get(index).root.requestFocus();
    }
}
