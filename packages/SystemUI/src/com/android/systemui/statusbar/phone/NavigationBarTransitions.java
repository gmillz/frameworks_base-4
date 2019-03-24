/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Display;
import android.view.IWallpaperVisibilityListener;
import android.view.IWindowManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.Dependency;
import com.android.systemui.R;

public final class NavigationBarTransitions extends BarTransitions {

    private final NavigationBarView mView;
    private final IStatusBarService mBarService;
    private final LightBarTransitionsController mLightTransitionsController;
    private final boolean mAllowAutoDimWallpaperNotVisible;
    private boolean mWallpaperVisible;

    private final Context mContext;

    private final Handler mHandler = new Handler();

    private boolean mLightsOut;
    private boolean mAutoDim;
    private View mNavButtons;

    private boolean mNavButtonsAlwaysDim;

    private float mNavButtonsAlphaLightsIn;
    private float mNavButtonsAlphaLightsOut;

    private int mDurationLightsIn;
    private int mDurationLightsOut;

    public NavigationBarTransitions(NavigationBarView view) {
        super(view, R.drawable.nav_background);
        mView = view;
        mContext = view.getContext();
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mLightTransitionsController = new LightBarTransitionsController(mContext,
                this::applyDarkIntensity);
        mAllowAutoDimWallpaperNotVisible = view.getContext().getResources()
                .getBoolean(R.bool.config_navigation_bar_enable_auto_dim_no_visible_wallpaper);

        IWindowManager windowManagerService = Dependency.get(IWindowManager.class);
        Handler handler = Handler.getMain();
        try {
            mWallpaperVisible = windowManagerService.registerWallpaperVisibilityListener(
                new IWallpaperVisibilityListener.Stub() {
                    @Override
                    public void onWallpaperVisibilityChanged(boolean newVisibility,
                            int displayId) throws RemoteException {
                        mWallpaperVisible = newVisibility;
                        handler.post(() -> applyLightsOut(true, false));
                    }
                }, Display.DEFAULT_DISPLAY);
        } catch (RemoteException e) {
        }
        mView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    View currentView = mView.getCurrentView();
                    if (currentView != null) {
                        mNavButtons = currentView.findViewById(R.id.nav_buttons);
                        applyLightsOut(false, true);
                    }
                });
        View currentView = mView.getCurrentView();
        if (currentView != null) {
            mNavButtons = currentView.findViewById(R.id.nav_buttons);
        }

        // Settings observer
        SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mSettingsObserver.updateSettings();
    }

    public void init() {
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyLightsOut(false /*animate*/, true /*force*/);
    }

    @Override
    public void setAutoDim(boolean autoDim) {
        if (mAutoDim == autoDim) return;
        mAutoDim = autoDim;
        applyLightsOut(true, false);
    }

    @Override
    protected boolean isLightsOut(int mode) {
        return super.isLightsOut(mode) || (mAllowAutoDimWallpaperNotVisible && mAutoDim
                && (!mWallpaperVisible || mNavButtonsAlwaysDim) && mode != MODE_WARNING);
    }

    public LightBarTransitionsController getLightTransitionsController() {
        return mLightTransitionsController;
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyLightsOut(animate, false /*force*/);
    }

    private void applyLightsOut(boolean animate, boolean force) {
        // apply to lights out
        applyLightsOut(isLightsOut(getMode()), animate, force);
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;
        if (mNavButtons == null) return;

        // ok, everyone, stop it right there
        mNavButtons.animate().cancel();

        // Bump percentage by 10% if dark.
        float darkBump = mLightTransitionsController.getCurrentDarkIntensity() / 10;
        final float navButtonsAlpha = lightsOut
                    ? mNavButtonsAlphaLightsOut + darkBump 
                    : mNavButtonsAlphaLightsIn;

        if (!animate) {
            mNavButtons.setAlpha(navButtonsAlpha);
        } else {
            final int duration = lightsOut ? mDurationLightsOut : mDurationLightsIn;
            mNavButtons.animate()
                .alpha(navButtonsAlpha)
                .setDuration(duration)
                .start();
        }
    }

    public void reapplyDarkIntensity() {
        applyDarkIntensity(mLightTransitionsController.getCurrentDarkIntensity());
    }

    public void applyDarkIntensity(float darkIntensity) {
        SparseArray<ButtonDispatcher> buttonDispatchers = mView.getButtonDispatchers();
        for (int i = buttonDispatchers.size() - 1; i >= 0; i--) {
            buttonDispatchers.valueAt(i).setDarkIntensity(darkIntensity);
        }
        if (mAutoDim) {
            applyLightsOut(false, true);
        }
        mView.onDarkIntensityChange(darkIntensity);
    }

    private final View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                applyLightsOut(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE,
                            "LightsOutListener");
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };

    /**
     * Settingsobserver to take care of the user settings.
     */
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_KEYS_ALWAYS_DIM),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_ALPHA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_ALPHA),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_DURATION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DURATION),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateSettings();
        }

        public void updateSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            // Always dim navigation bar (also when wallpaper is visible)
            mNavButtonsAlwaysDim = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_KEYS_ALWAYS_DIM,
                    0,
                    UserHandle.USER_CURRENT) == 1;
            // Navigation bar lights in transparency (aka. non-dimmed)
            mNavButtonsAlphaLightsIn = (float) Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_ALPHA,
                    100,
                    UserHandle.USER_CURRENT) / 100;
            // Navigation bar lights out transparency (aka. dimmed)
            mNavButtonsAlphaLightsOut = (float) Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_ALPHA,
                    60,
                    UserHandle.USER_CURRENT) / 100;
            // Navigation bar lights in animation duration (aka. dimmed -> non-dimmed)
            mDurationLightsIn = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_DURATION,
                    LIGHTS_IN_DURATION,
                    UserHandle.USER_CURRENT);
            // Navigation bar lights out animation duration (aka. non-dimmed -> dimmed)
            mDurationLightsOut = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DURATION,
                    LIGHTS_OUT_DURATION,
                    UserHandle.USER_CURRENT);
            reapplyDarkIntensity();
        }
    }
}
