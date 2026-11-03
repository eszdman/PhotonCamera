package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.BlurSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Preference opening a dialog that shows every camera mode as a row with a
 * visibility checkbox and a drag handle. Checked means the mode is shown,
 * unchecked means it is hidden from the selector. Rows can be dragged to
 * change the mode selector order; dragging works for hidden rows too so they
 * keep their slot when re-shown. At least one mode must stay visible.
 */
public class HideReorderModesPreference extends Preference {

    public HideReorderModesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        showDialog();
    }

    /** Refreshes the summary: "All modes shown" or "N hidden". */
    public void updateSummary() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int hiddenCount = PreferenceKeys.getHiddenModes().size();
        setSummary(hiddenCount == 0
                ? context.getString(R.string.hide_modes_summary_none)
                : context.getString(R.string.hide_modes_summary, hiddenCount));
    }

    private void showDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        View content = LayoutInflater.from(context).inflate(R.layout.dialog_hide_reorder_modes, null);
        RecyclerView list = content.findViewById(R.id.hide_modes_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        ModeAdapter adapter = new ModeAdapter(context);
        list.setAdapter(adapter);

        ItemTouchHelper dragHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                adapter.move(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        dragHelper.attachToRecyclerView(list);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        // Validate before dismissing so hiding every mode cannot slip through.
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    Set<String> hidden = adapter.getHiddenOrdinals();
                    if (hidden.size() >= CameraMode.values().length) {
                        Toast.makeText(context, R.string.hide_modes_cannot_hide_all,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PreferenceKeys.setHiddenModes(hidden);
                    PreferenceKeys.setModeOrder(adapter.getOrder());
                    updateSummary();
                    dialog.dismiss();
                }));
        BlurSupport.show(dialog);
    }

    private static final class ModeAdapter extends RecyclerView.Adapter<ModeAdapter.ModeViewHolder> {
        private final Context context;
        private final List<CameraMode> order = new ArrayList<>();
        private final Set<String> hidden = new HashSet<>();

        ModeAdapter(Context context) {
            this.context = context;
            order.addAll(PreferenceKeys.getModeOrder());
            hidden.addAll(PreferenceKeys.getHiddenModes());
        }

        List<CameraMode> getOrder() {
            return new ArrayList<>(order);
        }

        Set<String> getHiddenOrdinals() {
            return new HashSet<>(hidden);
        }

        void move(int from, int to) {
            if (from < 0 || to < 0 || from >= order.size() || to >= order.size() || from == to) {
                return;
            }
            order.add(to, order.remove(from));
            notifyItemMoved(from, to);
        }

        @NonNull
        @Override
        public ModeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.item_hide_reorder_mode, parent, false);
            return new ModeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ModeViewHolder holder, int position) {
            CameraMode mode = order.get(position);
            holder.label.setText(context.getString(CameraMode.nameIds()[mode.ordinal()]));
            // Detach before setChecked so a recycled row doesn't record the
            // programmatic state change as a user toggle. Checked = shown,
            // unchecked = hidden.
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(!hidden.contains(String.valueOf(mode.ordinal())));
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String ordinal = String.valueOf(mode.ordinal());
                if (isChecked) {
                    hidden.remove(ordinal);
                } else {
                    hidden.add(ordinal);
                }
            });
        }

        @Override
        public int getItemCount() {
            return order.size();
        }

        static final class ModeViewHolder extends RecyclerView.ViewHolder {
            final MaterialCheckBox checkbox;
            final TextView label;

            ModeViewHolder(@NonNull View itemView) {
                super(itemView);
                checkbox = itemView.findViewById(R.id.mode_hide_checkbox);
                label = itemView.findViewById(R.id.mode_hide_label);
            }
        }
    }
}
