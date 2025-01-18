package com.openautodash.ui.menu;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import com.openautodash.utilities.GridSpacingItemDecoration;

import java.util.Collections;
import java.util.List;

public class MenuAutopilot extends Fragment implements com.openautodash.ui.menu.AppGridAdapter.OnAppClickListener {
    private RecyclerView appsGrid;
    private List<ResolveInfo> apps;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_autopilot, container, false);
        appsGrid = view.findViewById(R.id.apps_grid);
        setupAppsGrid();
        return view;
    }

    private void setupAppsGrid() {
        // Get all launchable apps
        PackageManager packageManager = requireContext().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        apps = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL);

        // Sort apps alphabetically
        Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.loadLabel(packageManager).toString(),
                b.loadLabel(packageManager).toString()
        ));

        // Set fixed number of columns (you can adjust this number)
        int numberOfColumns = 4;

        // Setup the RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), numberOfColumns);
        appsGrid.setLayoutManager(layoutManager);
        com.openautodash.ui.menu.AppGridAdapter adapter = new com.openautodash.ui.menu.AppGridAdapter(apps, this);
        appsGrid.setAdapter(adapter);

        // Add spacing between items
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        appsGrid.addItemDecoration(new GridSpacingItemDecoration(numberOfColumns, spacingInPixels, true));
    }

    @Override
    public void onAppClick(ResolveInfo app) {
        PackageManager packageManager = requireContext().getPackageManager();
        String packageName = app.activityInfo.packageName;
        String className = app.activityInfo.name;

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setClassName(packageName, className);

        startActivity(intent);
    }
}