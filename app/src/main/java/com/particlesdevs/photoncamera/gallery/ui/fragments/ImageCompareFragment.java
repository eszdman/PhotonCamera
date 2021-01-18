package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageCompareBinding;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.compare.ScaleAndPan;

import java.util.Observable;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.*;

/**
 * Created by Vibhor Srivastava on 09-Jan-2021
 */
public class ImageCompareFragment extends Fragment {
    private static boolean toSync = true;
    private final SSIVListenerImpl ssivListener = new SSIVListenerImpl();
    private final ImageViewerFragment fragment1 = new ImageViewerFragment();
    private final ImageViewerFragment fragment2 = new ImageViewerFragment();
    private FragmentGalleryImageCompareBinding binding;

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        getParentFragmentManager().beginTransaction().remove(fragment1).remove(fragment2).commitAllowingStateLoss();
    }

    private class SSIVListenerImpl extends SSIVListener {
        private final ScaleAndPan scaleAndPan = new ScaleAndPan();
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        public void update(Observable o, Object arg) {
            int syncDelay = 0;
            if (((ScaleAndPan) o).getOrigin() == SubsamplingScaleImageView.ORIGIN_DOUBLE_TAP_ZOOM)
                syncDelay = DOUBLE_TAP_ZOOM_DURATION_MS;

            mainHandler.postDelayed(() -> {
                if (toSync) {
                    copyZoomPan(fragment1.getCurrentSSIV(), fragment2.getCurrentSSIV(), (ScaleAndPan) o);
                }
                fragment1.updateScaleText();
                fragment2.updateScaleText();
            }, syncDelay);

        }

        private void copyZoomPan(SubsamplingScaleImageView v1, SubsamplingScaleImageView v2, ScaleAndPan scaleAndPan) {
            v1.setScaleAndCenter(scaleAndPan.getScale(), scaleAndPan.getCenter());
            v2.setScaleAndCenter(scaleAndPan.getScale(), scaleAndPan.getCenter());
        }
    }
}
