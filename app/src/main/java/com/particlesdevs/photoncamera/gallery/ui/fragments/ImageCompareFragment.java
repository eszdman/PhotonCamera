package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Process;
import android.os.*;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageCompareBinding;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.compare.ScaleAndPan;
import com.particlesdevs.photoncamera.gallery.views.CustomSSIV;
import com.particlesdevs.photoncamera.ui.widget.FabPressSpring;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Files;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.*;

/**
 * Created by Vibhor Srivastava on 09-Jan-2021
 */
public class ImageCompareFragment extends Fragment {
    private static boolean toSync = true;
    private final SSIVListenerImpl ssivListener = new SSIVListenerImpl();
    private final ImageViewerFragment fragment1 = new ImageViewerFragment();
    private final ImageViewerFragment fragment2 = new ImageViewerFragment();
    private File imagesDir;
    private FragmentGalleryImageCompareBinding binding;
    private Context mContext;
    private final Handler shareHandler = new Handler(Looper.getMainLooper(), msg -> {
        if (msg.obj instanceof Uri) shareUri((Uri) msg.obj);
        hideButtons(false);
        return true;
    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        this.mContext = getContext();
        createDir();
    }

    private void createDir() {
        this.imagesDir = new File(mContext.getCacheDir(), "images");
        this.imagesDir.mkdirs();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_compare, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FabPressSpring.attach(binding.screenShareButton);
        Bundle b = getArguments();
        if (b != null) {
            toSync = true;
            binding.setOnSyncClick(this::onSyncClick);
            binding.setOnShare(this::onShareClick);
            FragmentTransaction trans = getChildFragmentManager().beginTransaction();

            Bundle b1 = new Bundle();
            b1.putString(MODE_KEY, COMPARE);
            b1.putInt(IMAGE_POSITION_KEY, b.getInt(IMAGE1_KEY));
            fragment1.setArguments(b1);
            fragment1.setSsivListener(ssivListener);
            trans.add(R.id.image_container1, fragment1, "image_container1");

            Bundle b2 = new Bundle();
            b2.putString(MODE_KEY, COMPARE);
            b2.putInt(IMAGE_POSITION_KEY, b.getInt(IMAGE2_KEY));
            fragment2.setArguments(b2);
            fragment2.setSsivListener(ssivListener);
            trans.add(R.id.image_container2, fragment2, "image_container2");

            trans.commit();
        }
    }

    private void onSyncClick(View view) {
        toSync = ((ToggleButton) view).isChecked();
        if (toSync) ssivListener.syncNow();
    }

    private void onShareClick(View view) {
        hideButtons(true);
        HandlerThread bmpThread = new HandlerThread("ScreenshotThread", Process.THREAD_PRIORITY_BACKGROUND);
        bmpThread.start();
        new Handler(bmpThread.getLooper()).post(() -> shareHandler.obtainMessage(0, saveBitmap(screenShot(binding.getRoot()))).sendToTarget());
        bmpThread.quitSafely();
    }

    private void shareUri(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(URLConnection.guessContentTypeFromName(uri.toString()));
        intent.setClipData(ClipData.newUri(mContext.getContentResolver(), "", uri));
        startActivity(Intent.createChooser(intent, null));
    }

    private void hideButtons(boolean toHide) {
        if (binding != null) binding.setHideButtons(toHide);
    }

    private Uri saveBitmap(Bitmap bitmap) {
        Uri uri = null;
        try {
            File file = new File(imagesDir, "compare_screenshot.jpg");
            OutputStream stream = Files.newOutputStream(file.toPath());
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            uri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".provider", file);
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            Toast.makeText(mContext, "Failed!", Toast.LENGTH_SHORT).show();
        }
        return uri;
    }

    private Bitmap screenShot(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getParentFragmentManager().beginTransaction().remove(fragment1).remove(fragment2).commitAllowingStateLoss();
        binding = null;
    }

    /**
     * Mirrors zoom and pan gestures from the pane touched last onto the other
     * pane. Only the gesture's *change* is copied: the zoom factor and the
     * normalized center shift since the last processed event, measured on the
     * master and converted to the other pane's own fit scale and dimensions so
     * images with different resolutions move by the same relative amount
     * (see {@link ScaleAndPan}). Because changes — not absolute states — are
     * copied, enabling sync never moves a pane: an alignment set up with sync
     * off is remembered and preserved while both panes move in lockstep.
     */
    private class SSIVListenerImpl extends SSIVListener {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Runnable syncRunnable = () -> {
            if (toSync) syncDeltas();
            fragment1.updateScaleText();
            fragment2.updateScaleText();
        };
        /** Master state the current gesture's deltas are measured against. */
        private final ScaleAndPan baseline = new ScaleAndPan();
        private final ScaleAndPan current = new ScaleAndPan();
        private int idTouched = 0;
        private boolean firstIsMaster = true;
        /** Pane (0/1) the baseline belongs to; -1 = re-baseline on next event without syncing. */
        private int baselinePane = -1;

        @Override
        public void onScaleChanged(float newScale, int origin) {
            scheduleSync();
        }

        @Override
        public void onCenterChanged(PointF newCenter, int origin) {
            scheduleSync();
        }

        @Override
        public void onTouched(int id) {
            idTouched = id;
            scheduleSync();
        }

        @Override
        public void onImageReady() {
            // Image swap can jump a pane's state discontinuously — restart deltas.
            baselinePane = -1;
        }

        @Override
        public void onPageSwitched() {
            // Freshly bound page starts at fit — restart deltas.
            baselinePane = -1;
        }

        /**
         * Called when sync is enabled: adopts the current states as the
         * baseline and moves nothing.
         */
        public void syncNow() {
            baselinePane = -1;
            fragment1.updateScaleText();
            fragment2.updateScaleText();
        }

        private void scheduleSync() {
            resolveMaster(idTouched);
            mainHandler.removeCallbacks(syncRunnable);
            mainHandler.post(syncRunnable);
        }

        private void syncDeltas() {
            CustomSSIV master = masterView();
            if (!capture(master, current)) return;
            if (baselinePane == (firstIsMaster ? 0 : 1) && baseline.getZoomRatio() > 0f) {
                float zoomFactor = current.getZoomRatio() / baseline.getZoomRatio();
                float dcx = current.getCenter().x - baseline.getCenter().x;
                float dcy = current.getCenter().y - baseline.getCenter().y;
                applyDelta(slaveView(), zoomFactor, dcx, dcy);
            }
            baseline.set(current.getZoomRatio(), current.getCenter());
            baselinePane = firstIsMaster ? 0 : 1;
        }

        private void applyDelta(CustomSSIV view, float zoomFactor, float dcx, float dcy) {
            if (view == null || !view.isReady()) return;
            int sWidth = view.getSWidth(), sHeight = view.getSHeight();
            float minScale = view.getMinScale();
            PointF center = view.getCenter();
            if (sWidth <= 0 || sHeight <= 0 || minScale <= 0f || center == null) return;
            float newRatio = (view.getScale() / minScale) * zoomFactor;
            float newCx = center.x / sWidth + dcx;
            float newCy = center.y / sHeight + dcy;
            view.setScaleAndCenter(newRatio * minScale, new PointF(newCx * sWidth, newCy * sHeight));
        }

        private CustomSSIV masterView() {
            return (firstIsMaster ? fragment1 : fragment2).getCurrentSSIV();
        }

        private CustomSSIV slaveView() {
            return (firstIsMaster ? fragment2 : fragment1).getCurrentSSIV();
        }

        private void resolveMaster(int touchedId) {
            CustomSSIV v1 = fragment1.getCurrentSSIV();
            CustomSSIV v2 = fragment2.getCurrentSSIV();
            // Touched ids go stale after pager swipes (views are re-bound with new ids),
            // so an unmatched id keeps the previous master.
            if (v1 != null && v1.getId() == touchedId) firstIsMaster = true;
            else if (v2 != null && v2.getId() == touchedId) firstIsMaster = false;
        }

        private boolean capture(CustomSSIV view, ScaleAndPan out) {
            if (view == null || !view.isReady()) return false;
            int sWidth = view.getSWidth(), sHeight = view.getSHeight();
            float minScale = view.getMinScale();
            PointF center = view.getCenter();
            if (sWidth <= 0 || sHeight <= 0 || minScale <= 0f || center == null) return false;
            out.set(view.getScale() / minScale, new PointF(center.x / sWidth, center.y / sHeight));
            return true;
        }
    }
}
