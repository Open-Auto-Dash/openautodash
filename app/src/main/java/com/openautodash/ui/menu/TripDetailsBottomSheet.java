package com.openautodash.ui.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.openautodash.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TripDetailsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DATE = "date";
    private static final String ARG_DIST = "dist";
    private static final String ARG_DURATION = "duration";
    private static final String ARG_TYPE = "type";

    public static TripDetailsBottomSheet newInstance(long startTime, float distanceMeters, long durationMs, boolean isBusiness) {
        TripDetailsBottomSheet fragment = new TripDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DATE, startTime);
        args.putFloat(ARG_DIST, distanceMeters);
        args.putLong(ARG_DURATION, durationMs);
        args.putBoolean(ARG_TYPE, isBusiness);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_trip_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        TextView tvDate = view.findViewById(R.id.sheet_tv_date);
        TextView tvDist = view.findViewById(R.id.sheet_tv_dist);
        TextView tvDuration = view.findViewById(R.id.sheet_tv_duration);
        TextView tvType = view.findViewById(R.id.sheet_tv_type);

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy - h:mm a", Locale.getDefault());
        tvDate.setText(sdf.format(new Date(args.getLong(ARG_DATE))));

        float distKm = args.getFloat(ARG_DIST) / 1000f;
        tvDist.setText(String.format(Locale.getDefault(), "%.2f km", distKm));

        long durationMs = args.getLong(ARG_DURATION);
        long minutes = (durationMs / 1000) / 60;
        tvDuration.setText(minutes + " min");

        tvType.setText(args.getBoolean(ARG_TYPE) ? "Business" : "Personal");
    }
}