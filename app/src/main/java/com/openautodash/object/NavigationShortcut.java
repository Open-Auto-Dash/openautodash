package com.openautodash.object;

import com.openautodash.R;

public class NavigationShortcut {
    public String name;
    public String iconKey;
    public String placeId;
    public String primaryText;
    public String secondaryText;

    public NavigationShortcut() {}

    public NavigationShortcut(String name, String iconKey, String placeId, String primaryText, String secondaryText) {
        this.name = name;
        this.iconKey = iconKey;
        this.placeId = placeId;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
    }

    public static int iconForKey(String key) {
        if ("work".equals(key)) return R.drawable.ic_shortcut_work;
        if ("food".equals(key)) return R.drawable.ic_shortcut_food;
        if ("gas".equals(key)) return R.drawable.ic_shortcut_gas;
        if ("store".equals(key)) return R.drawable.ic_shortcut_store;
        if ("favorite".equals(key)) return R.drawable.ic_shortcut_favorite;
        return R.drawable.ic_shortcut_home;
    }
}
