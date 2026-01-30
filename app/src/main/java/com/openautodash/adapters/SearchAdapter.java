package com.openautodash.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.openautodash.R;
import com.openautodash.object.PlaceSearchResult;
import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private List<PlaceSearchResult> results = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PlaceSearchResult item);
    }

    public SearchAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<PlaceSearchResult> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaceSearchResult item = results.get(position);
        holder.primary.setText(item.primaryText());
        holder.secondary.setText(item.secondaryText());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView primary, secondary;
        ViewHolder(View itemView) {
            super(itemView);
            primary = itemView.findViewById(R.id.tv_search_primary);
            secondary = itemView.findViewById(R.id.tv_search_secondary);
        }
    }
}