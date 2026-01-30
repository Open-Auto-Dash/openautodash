package com.openautodash.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.openautodash.R;
import com.openautodash.database.Trip;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {
    private List<Trip> trips = new ArrayList<>();
    private final OnTripActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy - h:mm a", Locale.getDefault());

    public interface OnTripActionListener {
        void onTripClick(Trip trip);
        void onDeleteClick(Trip trip);
    }

    public TripAdapter(OnTripActionListener listener) {
        this.listener = listener;
    }

    public void setTrips(List<Trip> trips) {
        this.trips = trips;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);

        holder.tvDate.setText(dateFormat.format(new Date(trip.getStartTime())));
        float distKm = trip.getDistanceMeters() / 1000f;
        holder.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", distKm));

        holder.tvBizBadge.setVisibility(trip.isBusiness() ? View.VISIBLE : View.GONE);

        // Active Trip Highlight
        if (trip.isOpen()) {
            holder.itemView.setAlpha(1.0f);
            holder.tvDate.setTextColor(0xFF2196F3); // Blue for active
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.tvDate.setTextColor(0xFF000000); // Black for history
        }

        // Click Listeners
        holder.itemView.setOnClickListener(v -> listener.onTripClick(trip));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(trip));
    }

    @Override
    public int getItemCount() { return trips.size(); }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDistance, tvBizBadge;
        ImageView btnDelete;

        TripViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_item_date);
            tvDistance = itemView.findViewById(R.id.tv_item_distance);
            tvBizBadge = itemView.findViewById(R.id.tv_item_biz_badge);
            btnDelete = itemView.findViewById(R.id.btn_delete_trip);
        }
    }
}