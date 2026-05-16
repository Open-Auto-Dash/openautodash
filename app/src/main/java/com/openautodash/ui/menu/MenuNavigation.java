package com.openautodash.ui.menu;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import com.openautodash.adapters.SearchAdapter;
import com.openautodash.object.NavigationShortcut;
import com.openautodash.object.PlaceSearchResult;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.LocationSearchManager;

import java.util.List;

public class MenuNavigation extends Fragment {
    private static final String[] ICON_KEYS = {"home", "work", "food", "gas", "store", "favorite"};

    private LocalSettings localSettings;
    private LocationSearchManager searchManager;
    private LinearLayout shortcutList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localSettings = new LocalSettings(requireContext());
        searchManager = new LocationSearchManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        shortcutList = view.findViewById(R.id.container_nav_shortcut_list);
        view.findViewById(R.id.b_add_nav_shortcut).setOnClickListener(v -> showShortcutDialog(-1, null));
        renderShortcuts();
    }

    private void renderShortcuts() {
        shortcutList.removeAllViews();
        List<NavigationShortcut> shortcuts = localSettings.getNavigationShortcuts();
        if (shortcuts.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No shortcuts yet");
            empty.setTextColor(ResourcesCompat.getColor(getResources(), R.color.colorTextBody, null));
            empty.setTextSize(18);
            shortcutList.addView(empty);
            return;
        }

        for (int i = 0; i < shortcuts.size(); i++) {
            NavigationShortcut shortcut = shortcuts.get(i);
            int index = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));

            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(NavigationShortcut.iconForKey(shortcut.iconKey));
            icon.setPadding(dp(10), dp(10), dp(10), dp(10));
            icon.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_search_bar, null));
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView label = new TextView(requireContext());
            label.setText(shortcut.name);
            label.setTextColor(ResourcesCompat.getColor(getResources(), R.color.colorTextTitle, null));
            label.setTextSize(18);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            labelParams.setMarginStart(dp(14));
            row.addView(label, labelParams);

            Button edit = new Button(requireContext());
            edit.setText("Edit");
            edit.setOnClickListener(v -> showShortcutDialog(index, shortcut));
            row.addView(edit, new LinearLayout.LayoutParams(dp(100), dp(46)));

            Button delete = new Button(requireContext());
            delete.setText("Delete");
            delete.setOnClickListener(v -> {
                List<NavigationShortcut> current = localSettings.getNavigationShortcuts();
                if (index >= 0 && index < current.size()) {
                    current.remove(index);
                    localSettings.setNavigationShortcuts(current);
                    renderShortcuts();
                }
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(110), dp(46));
            deleteParams.setMarginStart(dp(8));
            row.addView(delete, deleteParams);

            shortcutList.addView(row);
        }
    }

    private void showShortcutDialog(int editIndex, @Nullable NavigationShortcut existing) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Shortcut name");
        nameInput.setSingleLine(true);
        if (existing != null) nameInput.setText(existing.name);
        root.addView(nameInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        TextView iconTitle = new TextView(requireContext());
        iconTitle.setText("Icon");
        iconTitle.setTextColor(ResourcesCompat.getColor(getResources(), R.color.colorTextTitle, null));
        iconTitle.setPadding(0, dp(8), 0, dp(6));
        root.addView(iconTitle);

        LinearLayout iconRow = new LinearLayout(requireContext());
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(iconRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        String[] selectedIcon = {existing != null && existing.iconKey != null ? existing.iconKey : "home"};
        for (String iconKey : ICON_KEYS) {
            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(NavigationShortcut.iconForKey(iconKey));
            icon.setPadding(dp(11), dp(11), dp(11), dp(11));
            icon.setBackground(ResourcesCompat.getDrawable(getResources(),
                    iconKey.equals(selectedIcon[0]) ? R.drawable.background_image_view_sellected : R.drawable.bg_search_bar, null));
            icon.setOnClickListener(v -> {
                selectedIcon[0] = iconKey;
                updateIconSelection(iconRow, selectedIcon[0]);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            params.setMarginEnd(dp(8));
            iconRow.addView(icon, params);
        }

        TextView destination = new TextView(requireContext());
        destination.setText(existing != null && existing.primaryText != null ? existing.primaryText : "Select a destination below");
        destination.setTextColor(ResourcesCompat.getColor(getResources(), R.color.colorTextBody, null));
        destination.setPadding(0, dp(12), 0, dp(4));
        root.addView(destination);

        EditText searchInput = new EditText(requireContext());
        searchInput.setHint("Search destination");
        searchInput.setSingleLine(true);
        root.addView(searchInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        RecyclerView results = new RecyclerView(requireContext());
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        root.addView(results, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));

        PlaceSearchResult[] selectedPlace = {null};
        SearchAdapter adapter = new SearchAdapter(item -> {
            selectedPlace[0] = item;
            destination.setText(item.primaryText());
            searchInput.setText(item.primaryText());
        });
        results.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() <= 2) return;
                searchManager.searchPlaces(s.toString(), new LocationSearchManager.LocationSearchCallback() {
                    @Override public void onSearchResults(List<PlaceSearchResult> places) { adapter.updateData(places); }
                    @Override public void onPlaceSelected(com.google.android.libraries.places.api.model.Place place) {}
                    @Override public void onError(String message) {}
                });
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editIndex >= 0 ? "Edit Shortcut" : "Add Shortcut")
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("Required");
                return;
            }
            PlaceSearchResult place = selectedPlace[0];
            if (place == null && existing == null) {
                destination.setText("Choose a destination first");
                return;
            }

            NavigationShortcut shortcut = new NavigationShortcut(
                    name,
                    selectedIcon[0],
                    place != null ? place.placeId() : existing.placeId,
                    place != null ? place.primaryText() : existing.primaryText,
                    place != null ? place.secondaryText() : existing.secondaryText
            );

            List<NavigationShortcut> shortcuts = localSettings.getNavigationShortcuts();
            if (editIndex >= 0 && editIndex < shortcuts.size()) {
                shortcuts.set(editIndex, shortcut);
            } else {
                shortcuts.add(shortcut);
            }
            localSettings.setNavigationShortcuts(shortcuts);
            renderShortcuts();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void updateIconSelection(LinearLayout iconRow, String selectedIcon) {
        for (int i = 0; i < iconRow.getChildCount() && i < ICON_KEYS.length; i++) {
            iconRow.getChildAt(i).setBackground(ResourcesCompat.getDrawable(getResources(),
                    ICON_KEYS[i].equals(selectedIcon) ? R.drawable.background_image_view_sellected : R.drawable.bg_search_bar, null));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
