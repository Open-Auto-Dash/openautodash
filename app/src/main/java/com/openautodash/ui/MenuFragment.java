package com.openautodash.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openautodash.R;
import com.openautodash.adapters.MenuAdapter;
import com.openautodash.object.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MenuFragment extends Fragment implements MenuAdapter.OnMenuItemClickListener {
    private static final String TAG = "MenuFragment";

    //Menu
    private RecyclerView menuRecyclerView;
    private FrameLayout menuContainer;
    private MenuAdapter menuAdapter;

    private List<MenuItem> menuItemList;

    public MenuFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_menu, container, false);

        menuItemList = new ArrayList<>();

        //Menu
        menuRecyclerView = view.findViewById(R.id.rv_menu_items);
        menuContainer =view.findViewById(R.id.layout_menu_content);

        MenuItem menuItem = new MenuItem();
        menuItemList = menuItem.initMenu(getContext());

        menuAdapter = new MenuAdapter(getContext(), menuItemList);
        menuAdapter.setOnMenuItemClickListener(this); // Set the listener
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        menuRecyclerView.setLayoutManager(layoutManager);
        menuRecyclerView.setItemAnimator(new DefaultItemAnimator());
        menuRecyclerView.setAdapter(menuAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MenuItem item = menuItemList.get(0);
        item.setSelected(true);
        loadChildFragment(item.getFragment());
        menuAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMenuItemClick(int position) {
        loadChildFragment(menuItemList.get(position).getFragment());
    }

    private void loadChildFragment(Fragment fragment) {

        // Perform the fragment transaction to add/replace the child fragment in the container
        FragmentManager fragmentManager = getChildFragmentManager(); // Use getChildFragmentManager() to manage fragments within a fragment
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        fragmentTransaction.replace(R.id.layout_menu_content, fragment);
        fragmentTransaction.commit();
    }

}
