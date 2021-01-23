package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.content.ClipData;
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
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageCompareBinding;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.compare.ScaleAndPan;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Observable;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.*;

/**
 * Created by Vibhor Srivastava on 09-Jan-2021
 */
public class ImageCompareFragment extends Fragment {
    private static final int SEND_URI = 1000;
    private static boolean toSync = true;
    private final SSIVListenerImpl ssivListener = new SSIVListenerImpl();
    private final ImageViewerFragment fragment1 = new ImageViewerFragment();
    private final ImageViewerFragment fragment2 = new ImageViewerFragment();
    private FragmentGalleryImageCompareBinding binding;
    private final Handler shareHandler = new Handler(Looper.getMainLooper(), msg -> {
        if (msg.what == SEND_URI) {
            shareUri((Uri) msg.obj);
        }
        hideButtons(false);
        return true;
    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_compare, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
    }

    private void onShareClick(View view) {
        hideButtons(true);
        HandlerThread bmpThread = new HandlerThread(ImageCompareFragment.class.getName(), Process.THREAD_PRIORITY_BACKGROUND);
        bmpThread.start();
        Handler bitmapHandler = new Handler(bmpThread.getLooper());
        bitmapHandler.post(() -> {
            Uri uri = saveBitmap(screenShot(binding.getRoot()));
            shareHandler.obtainMessage(SEND_URI, uri).sendToTarget();
        });
        bmpThread.quitSafely();
    }

    private void shareUri(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(URLConnection.guessContentTypeFromName(uri.toString()));
        intent.setClipData(ClipData.newUri(getContext().getContentResolver(), "", uri));
        Intent chooser = Intent.createChooser(intent, null);
        startActivity(chooser);
    }

    private void hideButtons(boolean toHide) {
        if (binding != null) {
            binding.setHideButtons(toHide);
        }
    }

    private Uri saveBitmap(Bitmap bitmap) {
        File imagesFolder = new File(getContext().getCacheDir(), "images");
        Uri uri = null;
        try {
            boolean result = imagesFolder.mkdirs();
            if (!result){
                Toast.makeText(getContext(), "Failed!", Toast.LENGTH_SHORT).show();
                return uri;
            }
            File file = new File(imagesFolder, "compare_screenshot.jpg");
            OutputStream stream = Files.newOutputStream(file.toPath());
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed!", Toast.LENGTH_SHORT).show();
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
    }

    private class SSIVListenerImpl extends SSIVListener {
        private final ScaleAndPan scaleAndPan = new ScaleAndPan();
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private int idTouched = 0;

        SSIVListenerImpl() {
            scaleAndPan.addObserver(this::update);
        }

        @Override
        public void onScaleChanged(float newScale, int origin) {
            scaleAndPan.setOrigin(origin);
            scaleAndPan.setScale(newScale);
        }

        @Override
        public void onCenterChanged(PointF newCenter, int origin) {
            scaleAndPan.setOrigin(origin);
            scaleAndPan.setCenter(newCenter);
        }

        @Override
        public void onTouched(int id) {
            idTouched = id;
        }

        public void update(Observable o, Object arg) {
            mainHandler.post(() -> {
                if (toSync) {
                    copyZoomPan(fragment1.getCurrentSSIV(), fragment2.getCurrentSSIV(), (ScaleAndPan) o);
                }
                fragment1.updateScaleText();
                fragment2.updateScaleText();
            });
        }

        private void copyZoomPan(SubsamplingScaleImageView v1, SubsamplingScaleImageView v2, ScaleAndPan scaleAndPan) {
            if (v1.getId() == idTouched) v2.setScaleAndCenter(scaleAndPan.getScale(), scaleAndPan.getCenter());
            if (v2.getId() == idTouched) v1.setScaleAndCenter(scaleAndPan.getScale(), scaleAndPan.getCenter());
        }
    }
}
