package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.BlurSupport;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Preference for selecting a square PNG file from private app storage.
 * Supports importing new PNGs, selecting from available files, and removing files.
 * Validates imported PNG dimensions against allowed sizes.
 */
public class TunablePngPreference extends Preference {
    private static final String TAG = "TunablePngPref";
    private static final String PNG_DIR_NAME = "tunable_pngs";
    private static WeakReference<TunablePngPreference> sActivePreference = new WeakReference<>(null);
    private static ActivityResultLauncher<String[]> sImportLauncher;

    private int[] mAllowedPngSizes = {};
    private String mDefaultValue = "";

    public TunablePngPreference(Context context) {
        super(context);
        setIconSpaceReserved(false);
    }

    public void setAllowedPngSizes(int[] allowedPngSizes) {
        mAllowedPngSizes = allowedPngSizes != null ? allowedPngSizes : new int[0];
    }

    public void setDefaultValue(String defaultValue) {
        mDefaultValue = defaultValue != null ? defaultValue : "";
    }

    public static void setImportLauncher(ActivityResultLauncher<String[]> launcher) {
        sImportLauncher = launcher;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Avoid calling setSummary() here because it triggers RecyclerView adapter
        // notifications while RecyclerView is computing layout. Instead update the
        // summary TextView directly.
        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setText(getCurrentSummaryText());
        }
    }

    @Override
    protected void onClick() {
        sActivePreference = new WeakReference<>(this);
        showPngDialog();
    }

    private void showPngDialog() {
        Context context = getContext();
        if (context == null) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(getTitle());

        final List<String> availablePngs = getAvailablePngs(context);
        final List<String> items = new ArrayList<>();
        items.add("None");
        items.addAll(availablePngs);

        String currentValue = getPersistedString(mDefaultValue);
        int checkedItem = 0;
        if (currentValue != null && !currentValue.isEmpty()) {
            for (int i = 1; i < items.size(); i++) {
                if (items.get(i).equals(currentValue)) {
                    checkedItem = i;
                    break;
                }
            }
        }

        String[] itemArray = items.toArray(new String[0]);

        builder.setSingleChoiceItems(itemArray, checkedItem, (dialog, which) -> {
            if (which == 0) {
                persistString("");
            } else {
                persistString(items.get(which));
            }
            updateSummary();
            dialog.dismiss();
        });

        builder.setNeutralButton("Import new PNG", (dialog, which) -> {
            if (sImportLauncher != null) {
                sImportLauncher.launch(new String[]{"image/png"});
            } else {
                Log.w(TAG, "No import launcher set");
                PhotonCamera.showToast("Cannot open file picker");
            }
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    if (position == 0) return true; // Cannot remove "None"
                    final String pngName = items.get(position);
                    BlurSupport.show(new MaterialAlertDialogBuilder(context)
                            .setTitle("Remove PNG")
                            .setMessage("Remove " + pngName + "?")
                            .setPositiveButton("Remove", (d2, w2) -> {
                                removePng(context, pngName);
                                String selected = getPersistedString(mDefaultValue);
                                if (pngName.equals(selected)) {
                                    persistString("");
                                    updateSummary();
                                }
                                dialog.dismiss();
                                showPngDialog();
                            })
                            .setNegativeButton("Cancel", null)
                            .create());
                    return true;
                });
            }
        });
        BlurSupport.show(dialog);
    }

    private String getCurrentSummaryText() {
        String currentValue = getPersistedString(mDefaultValue);
        if (currentValue == null || currentValue.isEmpty()) {
            return "None selected";
        } else {
            return currentValue;
        }
    }

    private void updateSummary() {
        setSummary(getCurrentSummaryText());
    }

    /**
     * Handle import result from file picker.
     * @return Error message if failed, null on success
     */
    public static String handleImportResult(Context context, Uri uri) {
        TunablePngPreference pref = sActivePreference.get();
        if (pref == null) {
            Log.w(TAG, "No active preference for import result");
            return "No active preference";
        }
        return pref.importPng(context, uri);
    }

    /**
     * Refresh the active preference UI after import.
     */
    public static void refreshActivePreference() {
        TunablePngPreference pref = sActivePreference.get();
        if (pref != null) {
            pref.updateSummary();
            pref.showPngDialog();
        }
    }

    private String importPng(Context context, Uri uri) {
        if (uri == null) {
            return "No file selected";
        }

        String fileName = getFileName(context, uri);
        if (fileName == null || fileName.isEmpty()) {
            fileName = "image.png";
        }
        if (!fileName.toLowerCase().endsWith(".png")) {
            fileName += ".png";
        }

        // Validate dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read image bounds", e);
            return "Failed to read image: " + e.getMessage();
        }

        int width = options.outWidth;
        int height = options.outHeight;

        if (width <= 0 || height <= 0) {
            return "Invalid image file";
        }

        if (width != height) {
            return "PNG must be square. Current size: " + width + "x" + height;
        }

        // Validate against allowed sizes
        Set<Integer> allowedSizes = new HashSet<>();
        if (mAllowedPngSizes != null && mAllowedPngSizes.length > 0) {
            for (int size : mAllowedPngSizes) {
                allowedSizes.add(size);
            }
        } else {
            // Default allowed sizes if none specified
            allowedSizes.addAll(Arrays.asList(512, 1000, 1728, 2744, 4096));
        }

        if (!allowedSizes.contains(width)) {
            StringBuilder sizesStr = new StringBuilder();
            List<Integer> sorted = new ArrayList<>(allowedSizes);
            Collections.sort(sorted);
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) sizesStr.append(", ");
                sizesStr.append(sorted.get(i)).append("x").append(sorted.get(i));
            }
            return "PNG size " + width + "x" + height + " is not supported. Allowed sizes: " + sizesStr;
        }

        // Copy to private storage
        File pngsDir = getPngsDir(context);
        if (!pngsDir.exists()) {
            pngsDir.mkdirs();
        }

        File destFile = new File(pngsDir, fileName);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            if (is == null) {
                return "Failed to open input stream";
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy PNG file", e);
            return "Failed to copy file: " + e.getMessage();
        }

        // Auto-select the imported PNG
        persistString(fileName);
        updateSummary();

        Log.d(TAG, "Imported PNG: " + fileName + " (" + width + "x" + height + ")");
        return null; // Success
    }

    private void removePng(Context context, String pngName) {
        File pngsDir = getPngsDir(context);
        File pngFile = new File(pngsDir, pngName);
        if (pngFile.exists()) {
            boolean deleted = pngFile.delete();
            Log.d(TAG, "Removed PNG: " + pngName + " (deleted=" + deleted + ")");
        }
    }

    public static File getPngsDir(Context context) {
        return new File(context.getFilesDir(), PNG_DIR_NAME);
    }

    public static List<String> getAvailablePngs(Context context) {
        File pngsDir = getPngsDir(context);
        List<String> pngs = new ArrayList<>();
        if (pngsDir.exists() && pngsDir.isDirectory()) {
            File[] files = pngsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    pngs.add(file.getName());
                }
            }
        }
        Collections.sort(pngs);
        return pngs;
    }

    public static File getSelectedPngFile(Context context, String prefKey, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String fileName = prefs.getString(prefKey, defaultValue);
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        File pngFile = new File(getPngsDir(context), fileName);
        if (pngFile.exists()) {
            return pngFile;
        }
        return null;
    }

    private String getFileName(Context context, Uri uri) {
        String result = null;

        // 1. Try DISPLAY_NAME via content resolver (most reliable for content URIs)
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }

        // 2. Try DocumentsContract for document URIs (e.g. Downloads provider)
        if (result == null && android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            String docId = android.provider.DocumentsContract.getDocumentId(uri);
            if (docId != null) {
                // docId often looks like "primary:Download/filename.png" or "raw:/path/file.png"
                int lastColon = docId.lastIndexOf(':');
                if (lastColon >= 0 && lastColon + 1 < docId.length()) {
                    result = docId.substring(lastColon + 1);
                } else {
                    result = docId;
                }
            }
        }

        // 3. Fall back to last path segment
        if (result == null) {
            result = uri.getLastPathSegment();
        }

        // 4. URL-decode and extract basename only
        if (result != null) {
            try {
                result = java.net.URLDecoder.decode(result, "UTF-8");
            } catch (java.io.UnsupportedEncodingException ignored) {
            }
            // Ensure we only keep the filename, not any path
            int lastSlash = result.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash + 1 < result.length()) {
                result = result.substring(lastSlash + 1);
            }
        }

        // 5. MediaStore fallback: if the name is just a numeric ID (e.g. "1000060931.png"),
        // try to query MediaStore for the real display name.
        if (result != null && isNumericFileName(result)) {
            String mediaName = queryMediaStoreDisplayName(context, uri);
            if (mediaName != null && !mediaName.isEmpty()) {
                result = mediaName;
            }
        }

        return result;
    }

    /**
     * Check if the filename looks like a numeric media store ID, e.g. "1000060931.png"
     */
    private boolean isNumericFileName(String fileName) {
        if (fileName == null) return false;
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        // Allow a single trailing character like "a" or "v" that some providers append
        base = base.replaceAll("^[av]?", "").replaceAll("[av]?$", "");
        return base.matches("\\d+");
    }

    /**
     * Query MediaStore for the real display name when the URI is a media document.
     */
    private String queryMediaStoreDisplayName(Context context, Uri uri) {
        if (!android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            return null;
        }
        String docId = android.provider.DocumentsContract.getDocumentId(uri);
        if (docId == null) return null;

        // MediaStore document IDs look like "image:12345", "video:12345", etc.
        String[] split = docId.split(":");
        if (split.length < 2) return null;

        String type = split[0];
        String id = split[1];

        android.net.Uri contentUri = null;
        if ("image".equals(type)) {
            contentUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if ("video".equals(type)) {
            contentUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            return null;
        }

        String[] projection = {android.provider.MediaStore.MediaColumns.DISPLAY_NAME};
        String selection = android.provider.MediaStore.MediaColumns._ID + "=?";
        String[] selectionArgs = new String[]{id};

        try (android.database.Cursor cursor = context.getContentResolver().query(
                contentUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore query failed", e);
        }
        return null;
    }

    @Override
    protected String getPersistedString(String defaultValue) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null && prefs.contains(getKey())) {
            String val = prefs.getString(getKey(), defaultValue);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    @Override
    protected boolean persistString(String value) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            if (value == null || value.isEmpty()) {
                prefs.edit().remove(getKey()).apply();
            } else {
                prefs.edit().putString(getKey(), value).apply();
            }
        }
        return true;
    }
}
