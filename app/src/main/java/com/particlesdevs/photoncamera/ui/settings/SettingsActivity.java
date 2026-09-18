package com.particlesdevs.photoncamera.ui.settings;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialSharedAxis;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.app.base.BaseActivity;
import com.particlesdevs.photoncamera.pro.SupportedDevice;
import com.particlesdevs.photoncamera.settings.BackupRestoreUtil;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.settings.TunablePreferenceGenerator;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.ResetPreferences;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunablePngPreference;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.util.SecureCameraHelper;
import com.particlesdevs.photoncamera.util.log.FragmentLifeCycleMonitor;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_DEVICES_NAMES_KEY;
import static com.particlesdevs.photoncamera.settings.PreferenceKeys.SCOPE_GLOBAL;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    public static boolean toRestartApp;
    private static int sCameraMode = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(PreferenceKeys.getThemeValue());
        super.onCreate(savedInstanceState);
        // Defense-in-depth: settings are blocked while the device is locked. The
        // camera UI already hides these entry points in a secure session.
        if (SecureCameraHelper.isDeviceLocked(this)) {
            Toast.makeText(this, getString(R.string.secure_camera_settings_locked),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        // Predictive-back compatible handling: applies the restart side effect,
        // then disables and re-dispatches so FragmentManager pops its back stack.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (toRestartApp) {
                    PhotonCamera.restartApp(SettingsActivity.this);
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // Get camera mode from intent
        sCameraMode = getIntent().getIntExtra("camera_mode", -1);
        
        // Setup window insets to handle navigation bar
        setupWindowInsets();
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentLifeCycleMonitor(), true);

    }
    
    private void setupWindowInsets() {
        View toolbar = findViewById(R.id.settings_toolbar);
        if (toolbar != null) {
            com.particlesdevs.photoncamera.util.SystemBarsHelper.padTopForStatusBar(toolbar);
            ViewCompat.requestApplyInsets(toolbar);
        }
        View settingsContainer = findViewById(R.id.settings_container);
        if (settingsContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(settingsContainer, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.navigationBars()
                                | WindowInsetsCompat.Type.displayCutout());
                int navbarBottom = insets.bottom;

                // Apply margin bottom if navigation bar is present
                MarginLayoutParams layoutParams = (MarginLayoutParams) v.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.bottomMargin = navbarBottom;
                    v.setLayoutParams(layoutParams);
                }

                // Return consumed insets to prevent default behavior
                return windowInsets;
            });

            // Request insets to be applied
            ViewCompat.requestApplyInsets(settingsContainer);
        }
    }

    @Override
    public boolean onPreferenceStartScreen(@NonNull PreferenceFragmentCompat preferenceFragmentCompat,
                                           PreferenceScreen preferenceScreen) {
        Log.d("SettingsActivity", "onPreferenceStartScreen called for key: " + preferenceScreen.getKey());
        
        // Note: Tunable preferences are already generated in onPreferenceTreeClick before reaching here
        
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);
        // M3 shared-axis transitions for sub-screen navigation (predictive-back compatible).
        fragment.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
        fragment.setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        fragment.setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
        fragment.setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true);
        ft.replace(R.id.settings_container, fragment, preferenceScreen.getKey());
        ft.addToBackStack(preferenceScreen.getKey());
        ft.commit();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceManager.OnPreferenceTreeClickListener {
        private static final String KEY_MAIN_PARENT_SCREEN = "prefscreen";
        private Activity activity;
        private SettingsManager mSettingsManager;
        private Context mContext;
        private View mRootView;
        private SupportedDevice supportedDevice;
        private boolean tunablePreferencesGenerated = false;
        private boolean sensorConfigPreferencesGenerated = false;
        private boolean videoTunablePreferencesGenerated = false;
        private ActivityResultLauncher<String[]> lutImportLauncher;
        /** Viewfinder background mode before the current settings change. */
        private String viewfinderBackgroundBefore;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
            mContext = getContext();
            mSettingsManager = Objects.requireNonNull(PhotonCamera.getInstance(activity)).getSettingsManager();
            supportedDevice = Objects.requireNonNull(PhotonCamera.getInstance(activity)).getSupportedDevice();
            Objects.requireNonNull(getPreferenceScreen().getSharedPreferences())
                    .registerOnSharedPreferenceChangeListener(this);

            // Register PNG import launcher for TunablePngPreference
            // Uses OpenDocument to show the system file picker instead of gallery
            lutImportLauncher = registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            String error = TunablePngPreference.handleImportResult(mContext, uri);
                            if (error != null) {
                                PhotonCamera.showToast("PNG import failed: " + error);
                            } else {
                                PhotonCamera.showToast("PNG imported successfully");
                            }
                            TunablePngPreference.refreshActivePreference();
                        }
                    }
            );
            TunablePngPreference.setImportLauncher(lutImportLauncher);
            
            // Check if we're opening the tunable submenu specifically
            String rootKey = getArguments() != null ? getArguments().getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT) : null;
            Log.d("SettingsFragment", "onCreate with rootKey: " + rootKey);
            
            if ("pref_tunable_submenu".equals(rootKey)) {
                Log.d("SettingsFragment", "This is the tunable submenu fragment, generating preferences now");
                generateTunablePreferences();
            }

            if ("pref_sensor_config_submenu".equals(rootKey)) {
                Log.d("SettingsFragment", "This is the sensor config submenu fragment, generating preferences now");
                generateSensorConfigPreferences();
            }

            if ("pref_video_tunable_submenu".equals(rootKey)) {
                Log.d("SettingsFragment", "This is the video tunable submenu fragment, generating preferences now");
                generateVideoTunablePreferences(
                        com.particlesdevs.photoncamera.settings.VideoTunablePreferenceGenerator.SDR);
            }

            if ("pref_video_hdr_tunable_submenu".equals(rootKey)) {
                Log.d("SettingsFragment", "This is the HDR video tunable submenu fragment, generating preferences now");
                generateVideoTunablePreferences(
                        com.particlesdevs.photoncamera.settings.VideoTunablePreferenceGenerator.HDR);
            }
            
            filterPreferencesByMode();
            applySaveHeicUi();
            applyVideoUi();
            showHideHdrxSettings();
            setFramesSummary();
            setVersionDetails();
            setHdrxTitle();
            viewfinderBackgroundBefore = PreferenceKeys.getViewfinderBackground();
            setTelegramPref();
            setGithubPref();
            setBackupPref();
            setRestorePref();
            setSupportedDevices();
            setProTitle();
            setThisDevice();
            setFetchConfigurationsPref();
        }
        
        private void generateTunablePreferences() {
            // Only generate once per fragment instance
            if (tunablePreferencesGenerated) {
                Log.d("SettingsActivity", "Tunable preferences already generated, skipping");
                return;
            }
            tunablePreferencesGenerated = true;
            Log.d("SettingsActivity", "=== generateTunablePreferences called ===");
            Log.d("SettingsActivity", "Context: " + (mContext != null ? "OK" : "NULL"));
            Log.d("SettingsActivity", "PreferenceScreen: " + (getPreferenceScreen() != null ? "OK" : "NULL"));
            
            try {
                // Ensure tunable classes are registered
                com.particlesdevs.photoncamera.settings.TunableSettingsManager.ensureTunableClassesRegistered();
                
                // Register with TunablePreferenceGenerator for UI generation
                for (Class<?> clazz : com.particlesdevs.photoncamera.settings.TunableRegistry.TUNABLE_CLASSES) {
                    TunablePreferenceGenerator.registerTunableClass(clazz);
                }
                
                Log.d("SettingsActivity", "Registered classes, now generating preferences...");
                
                PreferenceScreen screen = getPreferenceScreen();
                Log.d("SettingsActivity", "Target PreferenceScreen: " + screen.getKey() + " (count before: " + screen.getPreferenceCount() + ")");
                
                // Generate preferences and add to screen
                TunablePreferenceGenerator.generatePreferences(mContext, screen);
                
                Log.d("SettingsActivity", "Generated preferences (count after: " + screen.getPreferenceCount() + ")");
                
                // Add reset button for tunable preferences
                addTunableResetButton();
                
                Log.d("SettingsActivity", "=== generateTunablePreferences completed (final count: " + screen.getPreferenceCount() + ") ===");
            } catch (Exception e) {
                Log.e("SettingsActivity", "ERROR in generateTunablePreferences", e);
                e.printStackTrace();
            }
        }
        
        private void generateSensorConfigPreferences() {
            // Only generate once per fragment instance
            if (sensorConfigPreferencesGenerated) {
                Log.d("SettingsActivity", "Sensor config preferences already generated, skipping");
                return;
            }
            sensorConfigPreferencesGenerated = true;
            Log.d("SettingsActivity", "=== generateSensorConfigPreferences called ===");
            Log.d("SettingsActivity", "Context: " + (mContext != null ? "OK" : "NULL"));
            Log.d("SettingsActivity", "PreferenceScreen: " + (getPreferenceScreen() != null ? "OK" : "NULL"));

            try {
                PreferenceScreen screen = getPreferenceScreen();
                if (screen == null) {
                    Log.w("SettingsActivity", "PreferenceScreen is null, cannot generate sensor config preferences");
                    return;
                }
                Log.d("SettingsActivity", "Target PreferenceScreen: " + screen.getKey() + " (count before: " + screen.getPreferenceCount() + ")");

                com.particlesdevs.photoncamera.settings.SensorConfigPreferenceGenerator.generatePreferences(mContext, screen);

                Log.d("SettingsActivity", "Generated sensor config preferences (count after: " + screen.getPreferenceCount() + ")");
                addSensorConfigResetButton();
                Log.d("SettingsActivity", "=== generateSensorConfigPreferences completed (final count: " + screen.getPreferenceCount() + ") ===");
            } catch (Exception e) {
                Log.e("SettingsActivity", "ERROR in generateSensorConfigPreferences", e);
                e.printStackTrace();
            }
        }

        private void generateVideoTunablePreferences(
                com.particlesdevs.photoncamera.settings.VideoTunablePreferenceGenerator.Config config) {
            // Only generate once per fragment instance
            if (videoTunablePreferencesGenerated) {
                Log.d("SettingsActivity", "Video tunable preferences already generated, skipping");
                return;
            }
            videoTunablePreferencesGenerated = true;
            try {
                PreferenceScreen screen = getPreferenceScreen();
                if (screen == null) {
                    Log.w("SettingsActivity", "PreferenceScreen is null, cannot generate video tunable preferences");
                    return;
                }
                com.particlesdevs.photoncamera.settings.VideoTunablePreferenceGenerator.generatePreferences(mContext, screen, config);
            } catch (Exception e) {
                Log.e("SettingsActivity", "ERROR in generateVideoTunablePreferences", e);
                e.printStackTrace();
            }
        }

        private void addSensorConfigResetButton() {
            try {
                PreferenceScreen submenu = getPreferenceScreen();
                if (submenu == null) {
                    Log.w("SettingsActivity", "PreferenceScreen is null, cannot add sensor config reset button");
                    return;
                }

                Preference resetButton = new Preference(mContext);
                resetButton.setKey("pref_reset_sensor_config_settings");
                resetButton.setTitle("Reset All to Defaults");
                resetButton.setSummary("Reset all sensor configuration parameters to their default values");
                resetButton.setIcon(android.R.drawable.ic_menu_revert);
                resetButton.setOrder(9999); // Force to the end

                resetButton.setOnPreferenceClickListener(preference -> {
                    SharedPreferences prefs = mSettingsManager.getDefaultPreferences();
                    SharedPreferences.Editor editor = prefs.edit();
                    int resetCount = 0;
                    String videoKey = "pref_sensorconfig_"
                            + com.particlesdevs.photoncamera.settings.TunableKeyManager.VIDEO_TUNABLE_ID
                            + "_tunablekeys";
                    String videoHdrKey = "pref_sensorconfig_"
                            + com.particlesdevs.photoncamera.settings.TunableKeyManager.VIDEO_HDR_TUNABLE_ID
                            + "_tunablekeys";
                    for (String key : prefs.getAll().keySet()) {
                        if (key != null && key.startsWith("pref_sensorconfig_")
                                && !key.equals(videoKey) && !key.equals(videoHdrKey)) {
                            editor.remove(key);
                            resetCount++;
                        }
                    }
                    editor.apply();
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                    PhotonCamera.showToast("Sensor config settings reset to defaults (" + resetCount + ")");
                    return true;
                });

                submenu.addPreference(resetButton);
                Log.d("SettingsActivity", "Added sensor config reset button (preferenceCount after: " + submenu.getPreferenceCount() + ")");
            } catch (Exception e) {
                Log.e("SettingsActivity", "Error adding sensor config reset button", e);
            }
        }

        private void addTunableResetButton() {
            try {
                // When we're inside the tunable submenu fragment, getPreferenceScreen() IS the tunable submenu
                androidx.preference.PreferenceScreen tunableSubmenu = getPreferenceScreen();
                
                if (tunableSubmenu != null) {
                    Log.d("SettingsActivity", "Adding reset button to tunable submenu (preferenceCount before: " + tunableSubmenu.getPreferenceCount() + ")");
                    
                    // Create reset button preference
                    androidx.preference.Preference resetButton = new androidx.preference.Preference(mContext);
                    resetButton.setKey("pref_reset_tunable_settings");
                    resetButton.setTitle("Reset All to Defaults");
                    resetButton.setSummary("Reset all tunable parameters to their default values");
                    resetButton.setIcon(android.R.drawable.ic_menu_revert);
                    resetButton.setOrder(9999); // Force to the end
                    
                    resetButton.setOnPreferenceClickListener(preference -> {
                        // Reset all tunable settings
                        com.particlesdevs.photoncamera.settings.TunableSettingsManager.resetAllToDefaults(mContext);
                        
                        // Restart the settings activity to refresh UI
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                        
                        com.particlesdevs.photoncamera.app.PhotonCamera.showToast("Tunable settings reset to defaults");
                        return true;
                    });
                    
                    tunableSubmenu.addPreference(resetButton);
                    Log.d("SettingsActivity", "Added reset button (preferenceCount after: " + tunableSubmenu.getPreferenceCount() + ")");
                } else {
                    Log.w("SettingsActivity", "PreferenceScreen is null, cannot add reset button");
                }
            } catch (Exception e) {
                Log.e("SettingsActivity", "Error adding reset button", e);
            }
        }

        /**
         * No-op retained for history: settings groups used to be hidden per
         * camera mode. All groups are always shown now; the Video settings
         * live in their own shortcut screen.
         */
        private void filterPreferencesByMode() {
        }

        private void showHideHdrxSettings() {
            if (PreferenceKeys.isHdrXOn())
                removePreferenceFromScreen(mContext.getString(R.string.pref_category_jpg_key));
            else
                removePreferenceFromScreen(mContext.getString(R.string.pref_category_hdrx_key));
        }

        /**
         * Applies the Save HEIC toggle to the settings UI: the "Save" list
         * shows its JPEG or HEIC entries depending on the toggle. On devices
         * without HEIC encode (API &lt;28 or no HEVC encoder) the toggle is
         * hidden entirely and forced off.
         */
        private void applySaveHeicUi() {
            try {
                boolean supported = com.particlesdevs.photoncamera.processing.encoder.HeicSupport.isHeicEncodeSupported();
                Preference heicPref = findPreference(
                        mContext.getString(R.string.pref_save_heic_key));
                if (heicPref != null) {
                    heicPref.setVisible(supported);
                    if (!supported) {
                        PreferenceKeys.setHeicSave(false);
                    }
                }
                androidx.preference.ListPreference savePref = findPreference(
                        mContext.getString(R.string.pref_save_raw_key));
                if (savePref != null) {
                    boolean heic = supported && PreferenceKeys.isHeicSave();
                    savePref.setEntries(heic
                            ? R.array.raw_mode_entries_heic
                            : R.array.raw_mode_entries);
                }
            } catch (Exception e) {
                Log.e("SettingsFragment", "applySaveHeicUi failed", e);
            }
        }

        /**
         * Applies capability gating and dependency visibility for the Video
         * Settings category:
         * <ul>
         *   <li>Save storage (HEVC) disabled with a reason when no HEVC
         *       video encoder exists; forced off.</li>
         *   <li>HDR video shown only when Save storage is on; disabled with
         *       a reason when no 10-bit Main10 encoder exists; forced off.</li>
         *   <li>HDR Transfer shown only when Save storage and HDR are on.</li>
         *   <li>HDR tunable keys entry shown only when HDR is on.</li>
         *   <li>HDR session type entry shown only when HDR is on.</li>
         *   <li>Logical id entry shown only when the logical-id switch is on;
         *       the switch is forced off when the id is not logical.</li>
         * </ul>
         */
        private void applyVideoUi() {
            try {
                boolean hasHevc = com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.hasHevcEncoder();
                boolean hasHdr = com.particlesdevs.photoncamera.processing.encoder.VideoCodecSupport.isHdrVideoSupported();
                boolean hevcOn = hasHevc && PreferenceKeys.isVideoHevc();
                boolean hdrOn = hevcOn && hasHdr && PreferenceKeys.isVideoHdr();

                Preference hevcPref = findPreference(mContext.getString(R.string.pref_video_hevc_key));
                if (hevcPref != null) {
                    if (!hasHevc) {
                        hevcPref.setEnabled(false);
                        hevcPref.setSummary(mContext.getString(R.string.video_hevc_unsupported));
                        if (PreferenceKeys.isVideoHevc()) PreferenceKeys.setVideoHevc(false);
                        hevcOn = false;
                    } else {
                        hevcPref.setEnabled(true);
                        hevcPref.setSummary(mContext.getString(R.string.video_save_storage_summary));
                    }
                }
                Preference hdrPref = findPreference(mContext.getString(R.string.pref_video_hdr_key));
                if (hdrPref != null) {
                    hdrPref.setVisible(hevcOn);
                    if (!hevcOn) {
                        if (PreferenceKeys.isVideoHdr()) PreferenceKeys.setVideoHdr(false);
                        hdrOn = false;
                    } else if (!hasHdr) {
                        hdrPref.setEnabled(false);
                        hdrPref.setSummary(mContext.getString(R.string.video_hdr_unsupported));
                        if (PreferenceKeys.isVideoHdr()) PreferenceKeys.setVideoHdr(false);
                        hdrOn = false;
                    } else {
                        hdrPref.setEnabled(true);
                        hdrPref.setSummary(mContext.getString(R.string.video_hdr_summary));
                    }
                }
                Preference transferPref = findPreference(mContext.getString(R.string.pref_video_hdr_transfer_key));
                if (transferPref != null) {
                    transferPref.setVisible(hdrOn);
                }
                Preference hdrTunablePref = findPreference("pref_video_hdr_tunable_submenu");
                if (hdrTunablePref != null) {
                    hdrTunablePref.setVisible(hdrOn);
                }
                Preference hdrSessionPref = findPreference(mContext.getString(R.string.pref_video_hdr_session_type_key));
                if (hdrSessionPref != null) {
                    hdrSessionPref.setVisible(hdrOn);
                }
                boolean useLogicalOn = PreferenceKeys.isVideoUseLogicalId();
                if (useLogicalOn && !isLogicalCameraId(mContext, PreferenceKeys.getVideoLogicalId())) {
                    PreferenceKeys.setVideoUseLogicalId(false);
                    useLogicalOn = false;
                    PhotonCamera.showToast(mContext.getString(R.string.video_logical_id_unsupported));
                }
                Preference logicalIdPref = findPreference(mContext.getString(R.string.pref_video_logical_id_key));
                if (logicalIdPref != null) {
                    logicalIdPref.setVisible(useLogicalOn);
                }
                Preference logicalLensesPref = findPreference(mContext.getString(R.string.pref_video_logical_lenses_key));
                if (logicalLensesPref != null) {
                    logicalLensesPref.setVisible(useLogicalOn);
                }
            } catch (Exception e) {
                Log.e("SettingsFragment", "applyVideoUi failed", e);
            }
        }

        /**
         * True when {@code id} names a logical camera (one with physical
         * members) on this device. Guards the video logical-id toggle.
         */
        private boolean isLogicalCameraId(Context context, String id) {
            if (context == null || id == null || id.isEmpty()) return false;
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return false;
            try {
                android.hardware.camera2.CameraManager manager =
                        (android.hardware.camera2.CameraManager)
                                context.getSystemService(Context.CAMERA_SERVICE);
                if (manager == null) return false;
                android.hardware.camera2.CameraCharacteristics chars =
                        manager.getCameraCharacteristics(id.trim());
                return chars != null && !chars.getPhysicalCameraIds().isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            if (container != null) container.removeAllViews();
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mRootView = view;
            setupToolbar();
        }

        private void setupToolbar() {
            if (activity != null) {
                Toolbar toolbar = activity.findViewById(R.id.settings_toolbar);
                if (toolbar != null) {
                    CharSequence title = getPreferenceScreen().getTitle();
                    // Default to "Settings" if title is null
                    if (title == null || title.toString().isEmpty()) {
                        title = "Settings";
                    }
                    toolbar.setTitle(title);
                }
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            // Update toolbar title when fragment resumes (e.g., after navigating back)
            setupToolbar();
        }


        @Override
        public void onDestroy() {
            super.onDestroy();
            getParentFragmentManager().beginTransaction().remove(SettingsFragment.this).commitAllowingStateLoss();
        }

        private void setTelegramPref() {
            activity.runOnUiThread(()-> {
                Preference myPref = findPreference(PreferenceKeys.Key.KEY_TELEGRAM.mValue);
                if (myPref != null)
                    myPref.setOnPreferenceClickListener(preference -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/photon_camera_channel"));
                        startActivity(browserIntent);
                        return true;
                    });
            });
        }

        private void setGithubPref() {
            activity.runOnUiThread(()-> {
            Preference github = findPreference(PreferenceKeys.Key.KEY_CONTRIBUTORS.mValue);
            if (github != null)
                github.setOnPreferenceClickListener(preference -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eszdman/PhotonCamera"));
                    startActivity(browserIntent);
                    return true;
                });
            });
        }

        private void setRestorePref() {
                activity.runOnUiThread(()-> {
            Preference restorePref = findPreference(mContext.getString(R.string.pref_restore_preferences_key));
            if (restorePref != null) {
                restorePref.setSummary(mContext.getString(R.string.restore_summary_json));
                restorePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String restoreResult = BackupRestoreUtil.restorePreferences(mContext, newValue.toString());
                    Snackbar.make(mRootView, restoreResult, Snackbar.LENGTH_LONG).show();
                    return true;
                });
            }
          });
        }

        private void setBackupPref() {
            activity.runOnUiThread(()-> {
                Preference backupPref = findPreference(mContext.getString(R.string.pref_backup_preferences_key));
                if (backupPref != null) {
                    backupPref.setSummary(mContext.getString(R.string.backup_summary_json));
                    backupPref.setOnPreferenceChangeListener((preference, newValue) -> {
                        String backupResult = BackupRestoreUtil.backupSettings(mContext, newValue.toString());
                        Snackbar.make(mRootView, backupResult, Snackbar.LENGTH_LONG).show();
                        return true;
                    });
                }
           });
        }
        private void setSupportedDevices() {
            activity.runOnUiThread(()-> {
                Preference preference = findPreference(PreferenceKeys.Key.ALL_DEVICES_NAMES_KEY.mValue);
                if (preference != null) {
                    preference.setSummary((mSettingsManager.getStringSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue,
                            ALL_DEVICES_NAMES_KEY, Collections.singleton(mContext.getString(R.string.list_not_loaded)))
                            .stream().sorted().map(s -> s + "\n").reduce("\n", String::concat)));
                }
           });
        }

        private void setProTitle() {
            activity.runOnUiThread(()-> {
                    Preference preference = findPreference(mContext.getString(R.string.pref_about_key));
                    if (preference != null && supportedDevice.isSupportedDevice()) {
                        preference.setTitle(R.string.device_support);
                    }
            });
        }

        private void setThisDevice() {
            Preference preference = findPreference(mContext.getString(R.string.pref_this_device_key));
            if (preference != null) {
                preference.setSummary(mContext.getString(R.string.this_device, SupportedDevice.THIS_DEVICE));
            }
        }

        private void setFetchConfigurationsPref() {
            Preference fetchPref = findPreference(mContext.getString(R.string.pref_fetch_configurations_key));
            if (fetchPref != null) {
                fetchPref.setOnPreferenceClickListener(preference -> {
                    preference.setSummary(mContext.getString(R.string.fetch_configurations_summary) + " (fetching…)");
                    new Thread(() -> {
                        supportedDevice.fetchFromNetwork();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            preference.setSummary(mContext.getString(R.string.fetch_configurations_summary));
                            com.google.android.material.snackbar.Snackbar.make(
                                    activity.findViewById(android.R.id.content),
                                    "Device configurations updated. Restart to apply camera changes.",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show();
                        });
                    }
                    }).start();
                    return true;
                });
            }
        }

        private void removePreferenceFromScreen(String preferenceKey) {
            PreferenceScreen parentScreen = findPreference(SettingsFragment.KEY_MAIN_PARENT_SCREEN);
            if (parentScreen != null)
                if (parentScreen.findPreference(preferenceKey) != null) {
                    parentScreen.removePreference(Objects.requireNonNull(parentScreen.findPreference(preferenceKey)));
                }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Guard against null key (can happen during preference restore)
            if (key == null) {
                return;
            }
            
            Log.d("SettingsFragment", "onSharedPreferenceChanged: key=" + key);
            
            if (key.equals(PreferenceKeys.Key.KEY_SAVE_PER_LENS_SETTINGS.mValue)) {
                setHdrxTitle();
                if (PreferenceKeys.isPerLensSettingsOn()) {
                    PreferenceKeys.loadSettingsForCamera(PreferenceKeys.getCameraID());
                    restartActivity();
                }
            }
            if (key.equalsIgnoreCase(PreferenceKeys.Key.KEY_THEME.mValue)) {
                restartActivity();
            }
            if (key.equalsIgnoreCase(PreferenceKeys.Key.KEY_THEME_ACCENT.mValue)) {
                restartActivity();
                toRestartApp = true;
            }
            if (key.equalsIgnoreCase(PreferenceKeys.Key.KEY_VIEWFINDER_BACKGROUND.mValue)) {
                // Only the gradient lives in the activity theme and needs an app
                // restart; the blurred edges apply when the camera resumes.
                String mode = PreferenceKeys.getViewfinderBackground();
                if (PreferenceKeys.VIEWFINDER_BACKGROUND_GRADIENT.equals(mode)
                        || PreferenceKeys.VIEWFINDER_BACKGROUND_GRADIENT.equals(viewfinderBackgroundBefore)) {
                    toRestartApp = true;
                }
                viewfinderBackgroundBefore = mode;
            }
            if (key.equalsIgnoreCase(PreferenceKeys.Key.KEY_FRAME_COUNT.mValue)) {
                setFramesSummary();
            }
            if (key.equals(PreferenceKeys.Key.KEY_SAVE_HEIC.mValue)) {
                applySaveHeicUi();
            }
            if (key.equals(PreferenceKeys.Key.KEY_VIDEO_HEVC.mValue)
                    || key.equals(PreferenceKeys.Key.KEY_VIDEO_HDR.mValue)
                    || key.equals(PreferenceKeys.Key.KEY_VIDEO_USE_LOGICAL_ID.mValue)
                    || key.equals(PreferenceKeys.Key.KEY_VIDEO_LOGICAL_ID.mValue)
                    || key.equals(PreferenceKeys.Key.KEY_VIDEO_LOGICAL_LENSES.mValue)) {
                applyVideoUi();
            }
            if (key.equalsIgnoreCase(PreferenceKeys.Key.KEY_HIDE_GALLERY_ICON.mValue)) {
                Log.d("SettingsFragment", "Hide gallery icon changed, expected key: " + PreferenceKeys.Key.KEY_HIDE_GALLERY_ICON.mValue);
                try {
                    boolean hideIcon = mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL, PreferenceKeys.Key.KEY_HIDE_GALLERY_ICON);
                    Log.d("SettingsFragment", "Hide gallery icon value: " + hideIcon);
                    toggleGalleryIconVisibility(hideIcon);
                } catch (Exception e) {
                    Log.e("SettingsFragment", "Error toggling gallery icon: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void setHdrxTitle() {
            Preference p = findPreference(mContext.getString(R.string.pref_category_hdrx_key));
            if (p != null) {
                if (PreferenceKeys.isPerLensSettingsOn()) {
                    p.setTitle(mContext.getString(R.string.hdrx) + "\t(Lens: " + PreferenceKeys.getCameraID() + ')');
                } else {
                    p.setTitle(mContext.getString(R.string.hdrx));
                }
            }
        }

        private void setFramesSummary() {
            Preference frameCountPreference = findPreference(PreferenceKeys.Key.KEY_FRAME_COUNT.mValue);
            if (frameCountPreference != null) {
                if (mSettingsManager.getInteger(PreferenceKeys.SCOPE_GLOBAL, PreferenceKeys.Key.KEY_FRAME_COUNT) == 1) {
                    frameCountPreference.setSummary(mContext.getString(R.string.unprocessed_raw));
                } else {
                    frameCountPreference.setSummary(mContext.getString(R.string.frame_count_summary));
                }
            }
        }

        private void toggleGalleryIconVisibility(boolean hideIcon) {
            try {
                // Get the ComponentName for the activity-alias using explicit package name
                String packageName = mContext.getPackageName();
                ComponentName galleryLauncher = new ComponentName(
                        packageName,
                        packageName + ".gallery.ui.GalleryActivityLauncher"
                );
                
                // Get the package manager
                PackageManager pm = mContext.getPackageManager();
                
                // Set the component enabled state based on hideIcon preference
                // If hideIcon is true, disable the launcher icon; otherwise enable it
                int newState = hideIcon ? 
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED : 
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                
                Log.d("SettingsFragment", "Toggling gallery icon visibility:");
                Log.d("SettingsFragment", "  hideIcon=" + hideIcon);
                Log.d("SettingsFragment", "  newState=" + newState);
                Log.d("SettingsFragment", "  component=" + galleryLauncher);
                
                pm.setComponentEnabledSetting(
                        galleryLauncher,
                        newState,
                        PackageManager.DONT_KILL_APP
                );
                
                Log.d("SettingsFragment", "Component state changed successfully");
                
                // Show a message to user
                if (activity != null) {
                    String message = hideIcon ? 
                            "Gallery icon will be hidden from launcher" : 
                            "Gallery icon will be visible in launcher";
                    activity.runOnUiThread(() -> 
                            com.google.android.material.snackbar.Snackbar.make(
                                    activity.findViewById(android.R.id.content),
                                    message,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                    );
                }
            } catch (Exception e) {
                Log.e("SettingsFragment", "Error in toggleGalleryIconVisibility: " + e.getMessage());
                e.printStackTrace();
                // Show error message to user
                if (activity != null) {
                    activity.runOnUiThread(() -> 
                            com.google.android.material.snackbar.Snackbar.make(
                                    activity.findViewById(android.R.id.content),
                                    "Error toggling gallery icon: " + e.getMessage(),
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                    );
                }
            }
        }

        private void restartActivity() {
            if (getActivity() != null) {
                Intent intent = new Intent(mContext, getActivity().getClass());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent,
                        ActivityOptions.makeCustomAnimation(mContext, R.anim.fade_in, R.anim.fade_out).toBundle());
            }
        }

        private void setVersionDetails() {
            activity.runOnUiThread(() -> {
                Preference about = findPreference(mContext.getString(R.string.pref_version_key));
                if (about != null) {
                    try {
                        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                        String versionName = packageInfo.versionName;
                        long versionCode = packageInfo.versionCode;

                        Date date = new Date(packageInfo.lastUpdateTime);
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                        about.setSummary(mContext.getString(R.string.version_summary, versionName + "." + versionCode, sdf.format(date)));

                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                }
            });

        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {
            // Log which preference was clicked
            Log.d("SettingsFragment", "onPreferenceTreeClick: " + preference.getKey());

            // Navigate manually only for dynamically-generated (empty) sub-screens:
            // non-empty screens are already opened once by default handling
            // (PreferenceScreen.onClick -> onNavigateToScreen), so forwarding
            // them here would stack the same screen twice (two backs to exit).
            if (preference instanceof PreferenceScreen) {
                PreferenceScreen screen = (PreferenceScreen) preference;
                if (screen.getPreferenceCount() != 0) {
                    return super.onPreferenceTreeClick(preference);
                }
                Log.d("SettingsFragment", "Submenu clicked, navigating: " + preference.getKey());

                // Navigate to the submenu (preferences will be generated in the new fragment's onCreate)
                if (activity instanceof SettingsActivity) {
                    ((SettingsActivity) activity).onPreferenceStartScreen(this, screen);
                    return true;
                }
            }

            // Return false to allow default handling
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof ResetPreferences) {
                DialogFragment dialogFragment = ResetPreferences.Dialog.newInstance(preference);
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getParentFragmentManager(), null);
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

    }
}
