package org.aohp.agentdriver.ui.filebridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.executor.AohpFileBridgeClient;
import org.aohp.agentdriver.executor.file.FileBridgeManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** File-manager-like UI launched by {@code file.show_in_folder}. */
public class FileBridgeActivity extends Activity {
    public static final String ACTION_SHOW_IN_FOLDER = "org.aohp.agentdriver.action.SHOW_IN_FOLDER";
    public static final String ACTION_REVEAL_FILE = "org.aohp.agentdriver.action.REVEAL_FILE";
    public static final String EXTRA_PATH = "path";

    private static final int BG = 0xFFF7F8FC;
    private static final int SURFACE = Color.WHITE;
    private static final int PRIMARY = 0xFF1D5FD1;
    private static final int TEXT = 0xFF191C24;
    private static final int TEXT_MUTED = 0xFF687083;
    private static final int STROKE = 0xFFE1E5EF;

    private AohpFileBridgeClient mClient;
    private TextView mTitle;
    private TextView mSubtitle;
    private TextView mSortLabel;
    private TextView mEmptyView;
    private ImageView mSortListIcon;
    private ImageView mSortGridIcon;
    private ImageView mSortOrderIcon;
    private ImageView mBackButton;
    private ImageView mSearchIcon;
    private ImageView mSearchCloseIcon;
    private ImageView mMoreIcon;
    private LinearLayout mTitleBlock;
    private EditText mSearchInput;
    private boolean mSearchMode;
    private LinearLayout mDetailCardContent;
    private RecyclerView mRecycler;
    private FileEntryAdapter mAdapter;

    private final ArrayList<FileEntry> mAllEntries = new ArrayList<>();
    private final ArrayList<FileEntry> mEntries = new ArrayList<>();
    private final Deque<String> mBackStack = new ArrayDeque<>();
    private String mCurrentDir = "/sdcard";
    private String mSelectedPath;
    private final HashSet<String> mSelectedPaths = new HashSet<>();
    private boolean mSelectionMode;
    private boolean mGridMode;
    private String mSearchQuery = "";
    private String mSortBy = "name";
    private boolean mSortAsc = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClient = new AohpFileBridgeClient(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        setContentView(buildContent());

        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.trim().isEmpty()) {
            path = "/sdcard";
        }
        mBackStack.clear();
        revealPath(path);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (hasSelection()) {
            clearSelection();
            return;
        }
        if (mSearchMode) {
            exitSearchMode();
            return;
        }
        if (!mBackStack.isEmpty()) {
            navigateBackInStack();
            return;
        }
        super.onBackPressed();
    }

    private void onToolbarBack() {
        if (hasSelection()) {
            clearSelection();
            return;
        }
        if (mSearchMode) {
            exitSearchMode();
            return;
        }
        if (!mBackStack.isEmpty()) {
            navigateBackInStack();
        } else {
            finish();
        }
    }

    private void navigateBackInStack() {
        if (mBackStack.isEmpty()) {
            return;
        }
        String parent = mBackStack.removeLast();
        loadDirectory(parent, parent);
    }

    private void enterSearchMode() {
        mSearchMode = true;
        mSearchCloseIcon.setVisibility(View.VISIBLE);
        mSearchIcon.setVisibility(View.GONE);
        mTitleBlock.setVisibility(View.GONE);
        mSearchInput.setVisibility(View.VISIBLE);
        mSearchInput.setText(mSearchQuery);
        mSearchInput.setSelection(mSearchInput.getText().length());
        mSearchInput.requestFocus();
    }

    private void exitSearchMode() {
        mSearchMode = false;
        mSearchQuery = "";
        mSearchInput.setText("");
        mSearchCloseIcon.setVisibility(View.GONE);
        mSearchIcon.setVisibility(View.VISIBLE);
        mTitleBlock.setVisibility(View.VISIBLE);
        mSearchInput.setVisibility(View.GONE);
        applyEntriesState();
    }

    private void applySizeFallbackIfNeeded(FileEntry e) {
        if (e.directory) {
            return;
        }
        if (e.size == 0) {
            File f = toFile(e.path);
            if (f != null && f.isFile()) {
                long len = f.length();
                if (len > 0) {
                    e.size = len;
                }
            }
        }
    }

    private View buildContent() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(10));
        frame.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(buildTopBar());
        page.addView(buildSortBar());

        mRecycler = new RecyclerView(this);
        mRecycler.setClipToPadding(false);
        mRecycler.setPadding(0, dp(4), 0, dp(16));
        mAdapter = new FileEntryAdapter();
        mRecycler.setAdapter(mAdapter);
        applyLayoutManager();
        page.addView(mRecycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mEmptyView = new TextView(this);
        mEmptyView.setTextColor(TEXT_MUTED);
        mEmptyView.setTextSize(15);
        mEmptyView.setGravity(Gravity.CENTER);
        mEmptyView.setVisibility(View.GONE);
        frame.addView(mEmptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return frame;
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(0, dp(6), 0, dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(row, matchWrap());

        mBackButton = createToolbarIcon(R.drawable.ic_arrow_back_24, v -> onToolbarBack());
        row.addView(mBackButton);

        FrameLayout middle = new FrameLayout(this);
        middle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mTitleBlock = new LinearLayout(this);
        mTitleBlock.setOrientation(LinearLayout.VERTICAL);
        middle.addView(mTitleBlock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mTitle = new TextView(this);
        mTitle.setTextSize(22);
        mTitle.setTextColor(TEXT);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setText("AOHP");
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitleBlock.addView(mTitle);

        mSubtitle = new TextView(this);
        mSubtitle.setTextSize(13);
        mSubtitle.setTextColor(TEXT_MUTED);
        mSubtitle.setSingleLine(true);
        mSubtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        mTitleBlock.addView(mSubtitle);

        mSearchInput = new EditText(this);
        mSearchInput.setVisibility(View.GONE);
        mSearchInput.setSingleLine(true);
        mSearchInput.setHint("Search in folder");
        mSearchInput.setTextColor(TEXT);
        mSearchInput.setHintTextColor(TEXT_MUTED);
        mSearchInput.setBackground(null);
        mSearchInput.setPadding(0, dp(2), 0, dp(2));
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mSearchQuery = s != null ? s.toString() : "";
                applyEntriesState();
            }
        });
        middle.addView(mSearchInput, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(middle);

        mSearchIcon = createToolbarIcon(R.drawable.ic_search_24, v -> enterSearchMode());
        mSearchCloseIcon = createToolbarIcon(R.drawable.ic_close_24, v -> exitSearchMode());
        mSearchCloseIcon.setVisibility(View.GONE);
        row.addView(mSearchIcon);
        row.addView(mSearchCloseIcon);

        row.addView(createToolbarIcon(R.drawable.ic_grid_view_24, v -> {
            mGridMode = !mGridMode;
            applyLayoutManager();
        }));
        mMoreIcon = createToolbarIcon(R.drawable.ic_more_vert_24, v -> showMainMenu(mMoreIcon));
        row.addView(mMoreIcon);
        return top;
    }

    private void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        FileEntry sel = findEntryByPath(mAllEntries, mSelectedPath);
        int selectedCount = selectedCount();
        if (selectedCount > 0) {
            pm.getMenu().add(0, 6, 0, mSelectionMode && selectedCount == mEntries.size()
                    ? "Clear selection" : "Select all");
            pm.getMenu().add(0, 7, 1, selectedCount == 1 ? "Delete selected item" : "Delete selected items");
        } else {
            pm.getMenu().add(0, 6, 0, "Select all");
        }
        if (selectedCount == 1 && sel != null && !sel.directory) {
            pm.getMenu().add(0, 1, 0, "Open");
            pm.getMenu().add(0, 2, 0, "Share");
            pm.getMenu().add(0, 3, 0, "Copy path");
        } else if (selectedCount == 0) {
            pm.getMenu().add(0, 4, 0, "Copy current folder path");
        }
        pm.getMenu().add(0, 5, 0, "Refresh");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1 && sel != null) {
                openFile(sel.path);
            } else if (item.getItemId() == 2 && sel != null) {
                shareFile(sel.path);
            } else if (item.getItemId() == 3 && sel != null) {
                copyPath(sel.path);
            } else if (item.getItemId() == 4) {
                copyPath(mCurrentDir);
            } else if (item.getItemId() == 5) {
                loadDirectory(mCurrentDir, mSelectedPath);
            } else if (item.getItemId() == 6) {
                if (mSelectionMode && selectedCount > 0 && selectedCount == mEntries.size()) {
                    clearSelection();
                } else {
                    selectAllVisible();
                }
            } else if (item.getItemId() == 7) {
                confirmDeleteSelected();
            }
            return true;
        });
        pm.show();
    }

    private void showEntryMenu(View anchor, FileEntry entry) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (entry.directory) {
            pm.getMenu().add(0, 1, 0, "Open");
            pm.getMenu().add(0, 3, 0, "Copy path");
            pm.getMenu().add(0, 4, 0, "Delete");
        } else {
            pm.getMenu().add(0, 1, 0, "Open");
            pm.getMenu().add(0, 2, 0, "Share");
            pm.getMenu().add(0, 3, 0, "Copy path");
            pm.getMenu().add(0, 4, 0, "Delete");
        }
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (entry.directory) {
                    mBackStack.addLast(mCurrentDir);
                    loadDirectory(entry.path, null);
                } else {
                    openFile(entry.path);
                }
            } else if (item.getItemId() == 2) {
                shareFile(entry.path);
            } else if (item.getItemId() == 3) {
                copyPath(entry.path);
            } else if (item.getItemId() == 4) {
                confirmDeleteEntry(entry);
            }
            return true;
        });
        pm.show();
    }

    private static FileEntry findEntryByPath(List<FileEntry> list, String path) {
        if (path == null) {
            return null;
        }
        for (FileEntry e : list) {
            if (samePath(e.path, path)) {
                return e;
            }
        }
        return null;
    }

    private View buildSortBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(8));

        mSortLabel = new TextView(this);
        mSortLabel.setTextColor(PRIMARY);
        mSortLabel.setTextSize(14);
        mSortLabel.setTypeface(Typeface.DEFAULT_BOLD);
        updateSortLabelText();
        mSortLabel.setOnClickListener(v -> showSortFieldMenu(mSortLabel));
        row.addView(mSortLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mSortOrderIcon = createSortBarIcon(R.drawable.ic_expand_more_24, v -> {
            mSortAsc = !mSortAsc;
            updateSortLabelText();
            applyEntriesState();
        });
        row.addView(mSortOrderIcon);

        mSortListIcon = createSortBarIcon(R.drawable.ic_view_list_24, v -> {
            mGridMode = false;
            applyLayoutManager();
        });
        row.addView(mSortListIcon);

        mSortGridIcon = createSortBarIcon(R.drawable.ic_grid_view_24, v -> {
            mGridMode = true;
            applyLayoutManager();
        });
        row.addView(mSortGridIcon);
        return row;
    }

    private void showSortFieldMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Name");
        pm.getMenu().add(0, 2, 0, "Size");
        pm.getMenu().add(0, 3, 0, "Date modified");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                mSortBy = "name";
            } else if (item.getItemId() == 2) {
                mSortBy = "size";
            } else {
                mSortBy = "date";
            }
            updateSortLabelText();
            applyEntriesState();
            return true;
        });
        pm.show();
    }

    private void updateSortLabelText() {
        if (mSortLabel == null) {
            return;
        }
        String field;
        switch (mSortBy) {
            case "size":
                field = "Size";
                break;
            case "date":
                field = "Date";
                break;
            default:
                field = "Name";
                break;
        }
        mSortLabel.setText(field + "  " + (mSortAsc ? "\u2191" : "\u2193"));
    }

    private void applyEntriesState() {
        mEntries.clear();
        String q = mSearchQuery.trim().toLowerCase(Locale.US);
        for (FileEntry e : mAllEntries) {
            if (q.isEmpty() || e.name.toLowerCase(Locale.US).contains(q)) {
                mEntries.add(e);
            }
        }
        sortEntriesInPlace();
        mAdapter.notifyDataSetChanged();
    }

    private void sortEntriesInPlace() {
        Collections.sort(mEntries, (a, b) -> {
            if (a.directory != b.directory) {
                return a.directory ? -1 : 1;
            }
            int c;
            switch (mSortBy) {
                case "size":
                    c = Long.compare(a.size, b.size);
                    break;
                case "date":
                    c = Long.compare(a.lastModified, b.lastModified);
                    break;
                default:
                    c = a.name.compareToIgnoreCase(b.name);
                    break;
            }
            if (c == 0) {
                c = a.name.compareToIgnoreCase(b.name);
            }
            return mSortAsc ? c : -c;
        });
    }

    private void revealPath(String path) {
        File target = toFile(path);
        boolean targetIsDirectory = target != null && target.isDirectory();
        File dir = targetIsDirectory ? target : (target != null ? target.getParentFile() : null);
        if (dir == null || !isAllowed(dir)) {
            dir = toFile("/sdcard");
        }
        mBackStack.clear();
        loadDirectory(toDevicePath(dir), targetIsDirectory ? null : normalizeDevicePath(path));
    }

    private void loadDirectory(String dirPath, String selectedPath) {
        mCurrentDir = normalizeDevicePath(dirPath);
        mSelectedPath = selectedPath != null ? normalizeDevicePath(selectedPath) : null;
        if (samePath(mSelectedPath, mCurrentDir)) {
            mSelectedPath = null;
        }
        mSelectionMode = false;
        mSelectedPaths.clear();
        if (mSelectedPath != null) {
            mSelectedPaths.add(mSelectedPath);
        }
        updateTitleForCurrentDir();
        mAllEntries.clear();

        JSONObject options = new JSONObject();
        try {
            options.put("listMode", "children");
            options.put("includeDirectories", true);
            options.put("maxDepth", 1);
            options.put("maxFiles", 1000);
            options.put("sort", "name");
        } catch (JSONException ignored) {
        }

        JSONObject listed = mClient.list(mCurrentDir, options);
        JSONArray files = listed.optJSONArray("files");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject obj = files.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                FileEntry entry = FileEntry.from(obj);
                entry.selected = samePath(entry.path, mSelectedPath);
                mAllEntries.add(entry);
            }
        }
        mergeLocalDirectoryEntries(mCurrentDir);

        for (FileEntry e : mAllEntries) {
            applySizeFallbackIfNeeded(e);
        }

        FileEntry selected = findSelectedInList(mAllEntries);
        if (selected == null) {
            selected = loadSelectedEntryFallback(mSelectedPath);
        }
        FileEntry revealedEntry = selected;

        reapplySelectionFlags();
        applyEntriesState();

        boolean empty = mEntries.isEmpty();
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            String listStatus = listed.optString("listStatus", "");
            if (listStatus.isEmpty()) {
                JSONArray rs = listed.optJSONArray("rootStats");
                if (rs != null && rs.length() > 0) {
                    listStatus = rs.optJSONObject(0).optString("status", "ok");
                }
            }
            if (listed.optBoolean("ok", false)
                    && ("unreadable".equals(listStatus) || "missing".equals(listStatus))) {
                mEmptyView.setText("Unable to list this folder (" + listStatus + ").\n"
                        + mCurrentDir);
            } else if (listed.optBoolean("ok", false)) {
                mEmptyView.setText("This folder is empty.");
            } else {
                mEmptyView.setText("Unable to read folder:\n" + listed.optString("message"));
            }
        }
        if (selected != null) {
            mSelectedPath = selected.path;
            mSelectedPaths.clear();
            mSelectedPaths.add(selected.path);
            reapplySelectionFlags();
            showDetails(selected);
            scrollToEntry(selected);
        } else {
            mSelectedPath = null;
            mSelectedPaths.clear();
            if (mDetailCardContent != null) {
                mDetailCardContent.removeAllViews();
            }
            removeDetailSheet();
        }
    }

    private void mergeLocalDirectoryEntries(String dirPath) {
        File dir = toFile(dirPath);
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        HashSet<String> seen = new HashSet<>();
        for (FileEntry entry : mAllEntries) {
            seen.add(normalizeDevicePath(entry.path));
        }
        for (File child : children) {
            if (!isAllowed(child)) {
                continue;
            }
            String childPath = toDevicePath(child);
            if (seen.contains(childPath)) {
                continue;
            }
            FileEntry entry = FileEntry.fromFile(child);
            entry.selected = samePath(entry.path, mSelectedPath);
            mAllEntries.add(entry);
            seen.add(childPath);
        }
    }

    private void reapplySelectionFlags() {
        if (mSelectedPaths.isEmpty() && mSelectedPath == null) {
            for (FileEntry e : mAllEntries) {
                e.selected = false;
            }
            return;
        }
        for (FileEntry e : mAllEntries) {
            e.selected = mSelectedPaths.contains(normalizeDevicePath(e.path))
                    || samePath(e.path, mSelectedPath);
        }
    }

    private FileEntry findSelectedInList(List<FileEntry> list) {
        for (FileEntry e : list) {
            if (e.selected) {
                return e;
            }
        }
        return null;
    }

    private FileEntry loadSelectedEntryFallback(String selectedPath) {
        if (selectedPath == null || selectedPath.isEmpty() || samePath(selectedPath, mCurrentDir)) {
            return null;
        }
        JSONObject stat = mClient.stat(selectedPath);
        JSONObject file = stat.optJSONObject("file");
        if (!stat.optBoolean("ok", false) || file == null) {
            return null;
        }
        FileEntry entry = FileEntry.from(file);
        if (entry.path == null || entry.path.isEmpty()) {
            return null;
        }
        applySizeFallbackIfNeeded(entry);
        entry.selected = true;
        mAllEntries.add(0, entry);
        return entry;
    }

    private void applyLayoutManager() {
        if (mRecycler == null) {
            return;
        }
        if (mGridMode) {
            mRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            mRecycler.setLayoutManager(new LinearLayoutManager(this));
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        refreshSortBarIcons();
    }

    private void refreshSortBarIcons() {
        if (mSortListIcon == null || mSortGridIcon == null) {
            return;
        }
        ImageViewCompat.setImageTintList(mSortListIcon,
                ColorStateList.valueOf(!mGridMode ? PRIMARY : TEXT_MUTED));
        ImageViewCompat.setImageTintList(mSortGridIcon,
                ColorStateList.valueOf(mGridMode ? PRIMARY : TEXT_MUTED));
    }

    private void selectEntry(FileEntry entry) {
        if (entry == null) {
            return;
        }
        mSelectedPath = normalizeDevicePath(entry.path);
        mSelectionMode = false;
        mSelectedPaths.clear();
        mSelectedPaths.add(mSelectedPath);
        reapplySelectionFlags();
        mAdapter.notifyDataSetChanged();
        updateTitleForCurrentDir();
        showDetails(entry);
    }

    private boolean hasSelection() {
        return mSelectionMode && selectedCount() > 0;
    }

    private int selectedCount() {
        return mSelectedPaths.size();
    }

    private void selectAllVisible() {
        if (mEntries.isEmpty()) {
            toast("No items to select");
            return;
        }
        mSelectedPaths.clear();
        for (FileEntry entry : mEntries) {
            mSelectedPaths.add(normalizeDevicePath(entry.path));
        }
        mSelectionMode = true;
        mSelectedPath = normalizeDevicePath(mEntries.get(0).path);
        reapplySelectionFlags();
        mAdapter.notifyDataSetChanged();
        removeDetailSheet();
        updateSelectionTitle();
        toast(mSelectedPaths.size() + " item(s) selected");
    }

    private void clearSelection() {
        mSelectedPath = null;
        mSelectionMode = false;
        mSelectedPaths.clear();
        reapplySelectionFlags();
        mAdapter.notifyDataSetChanged();
        removeDetailSheet();
        updateTitleForCurrentDir();
    }

    private void updateTitleForCurrentDir() {
        mTitle.setText(displayNameForPath(mCurrentDir));
        mSubtitle.setText(subtitleForPath(mCurrentDir));
    }

    private void updateSelectionTitle() {
        if (!mSelectionMode) {
            updateTitleForCurrentDir();
            return;
        }
        mTitle.setText(selectedCount() + " selected");
        mSubtitle.setText("Tap to select or deselect · " + displayNameForPath(mCurrentDir));
    }

    /** Long-press / tap-in-selection-mode: toggle path in multi-select set. */
    private void toggleSelectionForEntry(FileEntry entry) {
        if (entry == null) {
            return;
        }
        String p = normalizeDevicePath(entry.path);
        if (!mSelectionMode) {
            mSelectedPaths.clear();
            mSelectedPaths.add(p);
            mSelectionMode = true;
        } else {
            if (mSelectedPaths.contains(p)) {
                mSelectedPaths.remove(p);
            } else {
                mSelectedPaths.add(p);
            }
            mSelectionMode = !mSelectedPaths.isEmpty();
        }
        if (mSelectionMode) {
            if (mSelectedPaths.size() == 1) {
                mSelectedPath = mSelectedPaths.iterator().next();
            } else {
                mSelectedPath = p;
            }
        } else {
            mSelectedPath = null;
        }
        reapplySelectionFlags();
        mAdapter.notifyDataSetChanged();
        removeDetailSheet();
        if (mSelectionMode) {
            updateSelectionTitle();
        } else {
            updateTitleForCurrentDir();
        }
    }

    private View buildIconCell(FileEntry entry, int iconSize) {
        View icon = entryIcon(entry, iconSize);
        if (!mSelectionMode) {
            return icon;
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize));
        ImageView badge = new ImageView(this);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        badge.setImageResource(entry.selected
                ? R.drawable.ic_check_circle_fill_24
                : R.drawable.ic_circle_outline_24);
        int tint = entry.selected ? PRIMARY : TEXT_MUTED;
        ImageViewCompat.setImageTintList(badge, ColorStateList.valueOf(tint));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(22), dp(22),
                Gravity.END | Gravity.BOTTOM);
        blp.setMargins(0, 0, dp(2), dp(2));
        wrap.addView(badge, blp);
        return wrap;
    }

    private ArrayList<FileEntry> selectedEntries() {
        ArrayList<FileEntry> selected = new ArrayList<>();
        for (FileEntry entry : mAllEntries) {
            if (mSelectedPaths.contains(normalizeDevicePath(entry.path))) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private void confirmDeleteEntry(FileEntry entry) {
        if (entry == null) {
            return;
        }
        ArrayList<FileEntry> entries = new ArrayList<>();
        entries.add(entry);
        confirmDelete(entries);
    }

    private void confirmDeleteSelected() {
        ArrayList<FileEntry> entries = selectedEntries();
        if (entries.isEmpty()) {
            toast("No items selected");
            return;
        }
        confirmDelete(entries);
    }

    private void confirmDelete(ArrayList<FileEntry> entries) {
        String title = entries.size() == 1 ? "Delete item?" : "Delete " + entries.size() + " items?";
        String message = entries.size() == 1
                ? entries.get(0).name
                : "Selected files and folders will be permanently deleted.";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteEntries(entries))
                .show();
    }

    private void deleteEntries(ArrayList<FileEntry> entries) {
        int deletedRoots = 0;
        int failed = 0;
        for (FileEntry entry : entries) {
            FileBridgeFileOperations.DeleteResult result =
                    FileBridgeFileOperations.delete(toFile(entry.path));
            if (result.deleted > 0 && !result.hasFailures()) {
                deletedRoots++;
            }
            failed += result.failed;
        }
        clearSelection();
        loadDirectory(mCurrentDir, null);
        if (failed > 0) {
            toast("Deleted " + deletedRoots + " item(s), " + failed + " failed");
        } else {
            toast("Deleted " + deletedRoots + " item(s)");
        }
    }

    private void showDetails(FileEntry entry) {
        if (mDetailCardContent != null) {
            mDetailCardContent.removeAllViews();
        }
        if (entry == null) {
            return;
        }
        if (mDetailCardContent == null) {
            mDetailCardContent = new LinearLayout(this);
            mDetailCardContent.setOrientation(LinearLayout.VERTICAL);
        }
    }

    private void showDetailSheet(FileEntry entry) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(14), dp(18), dp(14));
        sheet.setBackground(roundRect(SURFACE, dp(22), STROKE));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(row, matchWrap());

        row.addView(entryIcon(entry, dp(56)), new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(TEXT);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(name);

        TextView meta = new TextView(this);
        String metaText = entry.directory
                ? entry.childCount + " items"
                : (formatTime(entry.lastModified) + " · " + formatBytes(entry.size));
        meta.setText(metaText);
        meta.setTextColor(TEXT_MUTED);
        meta.setTextSize(12);
        text.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        sheet.addView(actions);

        actions.addView(smallAction("Open", v -> {
            if (entry.directory) {
                mBackStack.addLast(mCurrentDir);
                loadDirectory(entry.path, null);
            } else {
                openFile(entry.path);
            }
        }), actionParams());
        if (!entry.directory) {
            actions.addView(smallAction("Share", v -> shareFile(entry.path)), actionParams());
        }
        actions.addView(smallAction("Copy", v -> copyPath(entry.path)), actionParams());
        actions.addView(smallAction("Delete", v -> confirmDeleteEntry(entry)), actionParams());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.setMargins(dp(18), 0, dp(18), dp(12));

        ViewGroup root = (ViewGroup) ((ViewGroup) getWindow().getDecorView()).getChildAt(0);
        removeDetailSheet();
        sheet.setTag("detail_sheet");
        root.addView(sheet, lp);
    }

    private void removeDetailSheet() {
        ViewGroup root = (ViewGroup) ((ViewGroup) getWindow().getDecorView()).getChildAt(0);
        View old = root.findViewWithTag("detail_sheet");
        if (old != null) {
            root.removeView(old);
        }
    }

    private void scrollToEntry(FileEntry entry) {
        for (int i = 0; i < mEntries.size(); i++) {
            if (samePath(mEntries.get(i).path, entry.path)) {
                final int pos = i;
                mRecycler.post(() -> mRecycler.smoothScrollToPosition(pos));
                return;
            }
        }
    }

    private ImageView createToolbarIcon(@DrawableRes int drawableRes, View.OnClickListener listener) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(10);
        iv.setPadding(pad, pad, pad, pad);
        iv.setImageResource(drawableRes);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(TEXT));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        iv.setBackgroundResource(tv.resourceId);
        iv.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(48));
        if (drawableRes == R.drawable.ic_arrow_back_24) {
            lp.setMargins(dp(-8), 0, dp(2), 0);
        }
        iv.setLayoutParams(lp);
        return iv;
    }

    private ImageView createSortBarIcon(@DrawableRes int drawableRes, View.OnClickListener listener) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(8), dp(6), dp(8), dp(6));
        iv.setImageResource(drawableRes);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(TEXT_MUTED));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        iv.setBackgroundResource(tv.resourceId);
        iv.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(36));
        iv.setLayoutParams(lp);
        return iv;
    }

    private ImageView rowOverflowIcon() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setImageResource(R.drawable.ic_more_vert_24);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(TEXT));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        iv.setBackgroundResource(tv.resourceId);
        iv.setPadding(dp(8), dp(12), dp(8), dp(12));
        return iv;
    }

    private MaterialButton smallAction(String text, View.OnClickListener listener) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setTextSize(12);
        b.setCornerRadius(dp(14));
        b.setMinHeight(dp(38));
        b.setMinimumHeight(dp(38));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setOnClickListener(listener);
        return b;
    }

    private View entryIcon(FileEntry entry, int size) {
        FrameLayout box = new FrameLayout(this);
        int radius = Math.max(dp(10), size / 10);
        box.setBackground(roundRect(entry.directory ? 0xFFDAE8FF : typeColor(entry), radius, 0));

        if (!entry.directory && entry.image) {
            Bitmap bmp = decodeThumbnail(entry.path, size);
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                box.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return box;
            }
        }

        ImageView glyph = new ImageView(this);
        glyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.max(dp(6), size / 6);
        glyph.setPadding(pad, pad, pad, pad);
        glyph.setImageResource(typeIconDrawable(entry));
        int fg = entry.directory ? PRIMARY : Color.WHITE;
        ImageViewCompat.setImageTintList(glyph, ColorStateList.valueOf(fg));
        box.addView(glyph, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return box;
    }

    @DrawableRes
    private static int typeIconDrawable(FileEntry entry) {
        if (entry.directory) {
            return R.drawable.ic_folder_24;
        }
        String mt = entry.mimeType != null ? entry.mimeType : "";
        String n = entry.name.toLowerCase(Locale.US);
        if (mt.startsWith("application/pdf")) {
            return R.drawable.ic_picture_as_pdf_24;
        }
        if (n.endsWith(".xls") || n.endsWith(".xlsx")) {
            return R.drawable.ic_table_chart_24;
        }
        if (mt.startsWith("text/")) {
            return R.drawable.ic_article_24;
        }
        if (mt.startsWith("video/")) {
            return R.drawable.ic_movie_24;
        }
        if (mt.startsWith("audio/")) {
            return R.drawable.ic_audiotrack_24;
        }
        if (mt.startsWith("application/zip") || n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z")) {
            return R.drawable.ic_archive_24;
        }
        if (entry.image) {
            return R.drawable.ic_image_24;
        }
        return R.drawable.ic_insert_drive_file_24;
    }

    private void openFile(String path) {
        try {
            File file = toFile(path);
            if (file == null || !file.isFile()) {
                return;
            }
            Uri uri = FileProvider.getUriForFile(this,
                    FileBridgeManager.FILE_PROVIDER_AUTHORITY, file);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeFor(file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri));
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            toast("Open failed: " + e.getMessage());
        }
    }

    private void shareFile(String path) {
        try {
            File file = toFile(path);
            if (file == null || !file.isFile()) {
                return;
            }
            Uri uri = FileProvider.getUriForFile(this,
                    FileBridgeManager.FILE_PROVIDER_AUTHORITY, file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(mimeFor(file))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(getContentResolver(), "AOHP shared file", uri));
            startActivity(Intent.createChooser(send, "Share file"));
        } catch (Exception e) {
            toast("Share failed: " + e.getMessage());
        }
    }

    private void copyPath(String path) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AOHP file path", path));
            toast("Path copied");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private GradientDrawable roundRect(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke != 0) {
            d.setStroke(dp(1), stroke);
        }
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static File toFile(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.startsWith("/sdcard")) {
            return new File("/storage/emulated/0" + path.substring("/sdcard".length()));
        }
        return new File(path);
    }

    private static String toDevicePath(File file) {
        if (file == null) {
            return "/sdcard";
        }
        return normalizeDevicePath(file.getAbsolutePath());
    }

    private static String normalizeDevicePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/sdcard";
        }
        if (path.equals("/storage/emulated/0")) {
            return "/sdcard";
        }
        if (path.startsWith("/storage/emulated/0/")) {
            return "/sdcard" + path.substring("/storage/emulated/0".length());
        }
        return path;
    }

    private static boolean isAllowed(File file) {
        String path = normalizeDevicePath(file.getAbsolutePath());
        return path.equals("/sdcard") || path.startsWith("/sdcard/");
    }

    private static boolean samePath(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizeDevicePath(a).equals(normalizeDevicePath(b));
    }

    private static String subtitleForPath(String path) {
        String normalized = normalizeDevicePath(path);
        if (normalized.equals("/sdcard")) {
            return "Internal storage";
        }
        String rest = normalized.startsWith("/sdcard/") ? normalized.substring("/sdcard/".length()) : normalized;
        StringBuilder sb = new StringBuilder("Internal storage");
        for (String part : rest.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            String display = "Download".equals(part) ? "Downloads" : part;
            sb.append(" \u003e ").append(display);
        }
        return sb.toString();
    }

    private static String displayNameForPath(String path) {
        String normalized = normalizeDevicePath(path);
        if (normalized.equals("/sdcard")) {
            return "Internal storage";
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash < normalized.length() - 1) {
            return normalized.substring(slash + 1);
        }
        return normalized;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, "%.2f %s", value, units[unit]);
    }

    private static String formatTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(millis));
    }

    private String listMetaLine(FileEntry entry) {
        if (entry.directory) {
            return entry.childCount + " items";
        }
        String time = formatTime(entry.lastModified);
        String size = formatBytes(entry.size);
        if (time.isEmpty()) {
            return size;
        }
        return time + "  \u00b7  " + size;
    }

    private String gridSecondaryLine(FileEntry entry) {
        if (entry.directory) {
            return entry.childCount + " items";
        }
        return formatBytes(entry.size);
    }

    private static String mimeFor(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) : null;
        return mime != null ? mime : "application/octet-stream";
    }

    private static Bitmap decodeThumbnail(String path, int size) {
        File file = toFile(path);
        if (file == null || !file.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > size * 2 || bounds.outHeight / sample > size * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif");
    }

    private static int typeColor(FileEntry entry) {
        String n = entry.name.toLowerCase(Locale.US);
        if (entry.mimeType.startsWith("application/pdf")) {
            return 0xFFE64848;
        }
        if (entry.mimeType.startsWith("video/")) {
            return 0xFF8E5CF7;
        }
        if (entry.mimeType.startsWith("audio/")) {
            return 0xFFFFA726;
        }
        if (n.endsWith(".xls") || n.endsWith(".xlsx")) {
            return 0xFF33A061;
        }
        if (entry.mimeType.startsWith("text/")) {
            return 0xFF4B80E8;
        }
        if (entry.mimeType.startsWith("application/zip") || n.endsWith(".zip") || n.endsWith(".rar")
                || n.endsWith(".7z")) {
            return 0xFF8D6E63;
        }
        return 0xFF7D8797;
    }

    private final class FileEntryAdapter extends RecyclerView.Adapter<FileEntryAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setRadius(dp(16));
            card.setCardElevation(0);
            card.setStrokeWidth(0);
            card.setUseCompatPadding(false);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            card.setLayoutParams(lp);

            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(8), dp(6), dp(8));
            card.addView(row);
            return new Holder(card, row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FileEntry entry = mEntries.get(position);
            holder.bind(entry);
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            private final MaterialCardView card;
            private final LinearLayout row;

            Holder(MaterialCardView card, LinearLayout row) {
                super(card);
                this.card = card;
                this.row = row;
            }

            void bind(FileEntry entry) {
                row.removeAllViews();
                card.setCardBackgroundColor(entry.selected ? 0xFFEAF2FF : BG);
                card.setStrokeWidth(entry.selected ? dp(1) : 0);
                card.setStrokeColor(ColorStateList.valueOf(entry.selected ? PRIMARY : STROKE));
                card.setCardElevation(dp(mSelectionMode ? 0 : 1));

                int iconSize;
                if (mGridMode) {
                    iconSize = dp(80);
                } else {
                    iconSize = dp(48);
                }
                row.setOrientation(mGridMode ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                row.setGravity(mGridMode ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams iconParams = mGridMode
                        ? new LinearLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER_HORIZONTAL)
                        : new LinearLayout.LayoutParams(iconSize, iconSize);
                row.addView(buildIconCell(entry, iconSize), iconParams);

                LinearLayout text = new LinearLayout(FileBridgeActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setGravity(mGridMode ? Gravity.CENTER_HORIZONTAL : Gravity.NO_GRAVITY);
                text.setPadding(
                        mGridMode ? 0 : dp(16),
                        mGridMode ? dp(8) : 0,
                        mGridMode ? 0 : dp(4),
                        0);
                LinearLayout.LayoutParams textParams = mGridMode
                        ? new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        : new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                row.addView(text, textParams);

                TextView name = new TextView(FileBridgeActivity.this);
                name.setText(entry.name);
                name.setTextColor(TEXT);
                name.setTextSize(16);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setMaxLines(2);
                name.setGravity(mGridMode ? Gravity.CENTER_HORIZONTAL : Gravity.START);
                text.addView(name);

                if (!mGridMode) {
                    TextView line = new TextView(FileBridgeActivity.this);
                    line.setText(listMetaLine(entry));
                    line.setTextColor(TEXT_MUTED);
                    line.setTextSize(13);
                    line.setPadding(0, dp(4), 0, 0);
                    text.addView(line);
                } else {
                    TextView size = new TextView(FileBridgeActivity.this);
                    size.setText(gridSecondaryLine(entry));
                    size.setTextColor(TEXT_MUTED);
                    size.setTextSize(12);
                    size.setGravity(Gravity.CENTER_HORIZONTAL);
                    size.setPadding(0, dp(4), 0, 0);
                    text.addView(size);
                }

                if (!mGridMode) {
                    ImageView more = rowOverflowIcon();
                    more.setOnClickListener(v -> {
                        if (!mSelectionMode) {
                            selectEntry(entry);
                        }
                        showEntryMenu(more, entry);
                    });
                    row.addView(more, new LinearLayout.LayoutParams(dp(42), dp(64)));
                    card.setOnLongClickListener(v -> {
                        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        toggleSelectionForEntry(entry);
                        return true;
                    });
                } else {
                    if (!mSelectionMode) {
                        LinearLayout menuRow = new LinearLayout(FileBridgeActivity.this);
                        menuRow.setGravity(Gravity.END);
                        menuRow.setPadding(0, dp(2), 0, 0);
                        ImageView moreGrid = rowOverflowIcon();
                        moreGrid.setOnClickListener(v -> {
                            selectEntry(entry);
                            showEntryMenu(moreGrid, entry);
                        });
                        menuRow.addView(moreGrid);
                        row.addView(menuRow, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    card.setOnLongClickListener(v -> {
                        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        toggleSelectionForEntry(entry);
                        return true;
                    });
                }

                card.setOnClickListener(v -> {
                    if (mSelectionMode) {
                        toggleSelectionForEntry(entry);
                        return;
                    }
                    if (entry.directory) {
                        mBackStack.addLast(mCurrentDir);
                        loadDirectory(entry.path, null);
                    } else {
                        selectEntry(entry);
                        openFile(entry.path);
                    }
                });
            }
        }
    }

    private static final class FileEntry {
        String name;
        String path;
        String mimeType;
        long size;
        long lastModified;
        int childCount;
        boolean directory;
        boolean selected;
        boolean image;

        static FileEntry from(JSONObject obj) {
            FileEntry e = new FileEntry();
            e.name = obj.optString("displayName", "(unknown)");
            e.path = obj.optString("devicePath", obj.optString("path", ""));
            e.mimeType = obj.optString("mimeType", "application/octet-stream");
            e.size = obj.optLong("size", 0L);
            e.lastModified = obj.optLong("lastModified", 0L);
            e.childCount = obj.optInt("childCount", 0);
            e.directory = obj.optBoolean("isDirectory", false);
            e.image = !e.directory && (e.mimeType.startsWith("image/") || isImageName(e.name));
            return e;
        }

        static FileEntry fromFile(File file) {
            FileEntry e = new FileEntry();
            e.name = file.getName();
            if (e.name == null || e.name.isEmpty()) {
                e.name = displayNameForPath(file.getAbsolutePath());
            }
            e.path = toDevicePath(file);
            e.directory = file.isDirectory();
            e.mimeType = e.directory ? "inode/directory" : mimeFor(file);
            e.size = e.directory ? 0L : file.length();
            e.lastModified = file.lastModified();
            if (e.directory) {
                File[] nested = file.listFiles();
                e.childCount = nested != null ? nested.length : 0;
            }
            e.image = !e.directory && (e.mimeType.startsWith("image/") || isImageName(e.name));
            return e;
        }
    }
}
